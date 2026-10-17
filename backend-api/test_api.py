import io
import os
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
from xml.etree import ElementTree

import numpy as np
from fastapi.testclient import TestClient
from PIL import Image

with patch.dict(os.environ, {"USE_MOCK": "true"}):
    import model_loader

    # Discovery may have already cached the loader's configuration.
    with patch.object(model_loader, "USE_MOCK", True):
        import main


PREDICTION_KEYS = {
    "model_label", "disease", "confidence", "uncertain",
    "guidance_available", "symptoms", "treatment", "prevention",
}
FALLBACK_GUIDANCE = {
    "symptoms": "Detailed symptoms and treatment guidance are not available in this version.",
    "treatment": "Please verify this result with a local agricultural expert or plant-disease reference.",
    "prevention": "Capture a clear close-up and continue monitoring. This result is not a confirmed diagnosis.",
}


class CroporaApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)
        cls.addClassCleanup(cls.client.close)
        output = io.BytesIO()
        Image.new("RGB", (32, 32), color=(30, 180, 60)).save(output, format="PNG")
        cls.image = output.getvalue()

    def post_image(self, predictor=None, content=None, mime="image/png"):
        if predictor is None:
            predictor = model_loader.ModelPredictor(main.CLASS_NAMES, use_mock=True)
        with patch.object(main, "predictor", predictor):
            return self.client.post(
                "/predict",
                files={"image": ("leaf.png", self.image if content is None else content, mime)},
            )

    def test_health_and_complete_library(self):
        for path in ("/", "/health", "/diseases"):
            with self.subTest(path=path):
                response = self.client.get(path)
                self.assertEqual(200, response.status_code)
                payload = response.json()
                if path == "/diseases":
                    self.assertEqual(38, payload["count"])
                    self.assertEqual(set(main.CLASS_NAMES), {
                        entry["model_label"] for entry in payload["diseases"]
                    })
                    self.assertEqual(38, len(payload["diseases"]))
                else:
                    self.assertEqual("ok", payload["status"])
                    self.assertEqual(38, payload["class_count"])

    def test_catalog_matches_offline_guidance_and_display_names(self):
        backend_path = Path(__file__).with_name("diseases.xml")
        android_path = backend_path.parent.parent / "android-app-kotlin/app/src/main/assets/diseases.xml"
        self.assertEqual(android_path.read_bytes(), backend_path.read_bytes())
        entries = ElementTree.parse(android_path).getroot().findall("disease")
        for entry in entries:
            label = entry.findtext("model_label")
            with self.subTest(label=label):
                self.assertEqual(entry.findtext("name").casefold(), main.display_label(label).casefold())
                for field in ("symptoms", "treatment", "prevention"):
                    self.assertEqual(entry.findtext(field), main.DISEASE_INFO[label][field])

    def test_every_model_class_returns_guidance(self):
        for label in main.CLASS_NAMES:
            with self.subTest(label=label):
                predictor = Mock(model_loaded=True, use_mock=False)
                predictor.predict.return_value = (label, 0.9)
                response = self.post_image(predictor)
                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual(PREDICTION_KEYS, set(payload))
                self.assertTrue(payload["guidance_available"])
                for field in ("symptoms", "treatment", "prevention"):
                    self.assertEqual(main.DISEASE_INFO[label][field], payload[field])

    def test_mock_prediction_keeps_response_contract(self):
        response = self.post_image()
        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(PREDICTION_KEYS, set(payload))
        self.assertIn(payload["model_label"], main.CLASS_NAMES)
        self.assertGreaterEqual(payload["confidence"], 0.0)
        self.assertLessEqual(payload["confidence"], 1.0)

    def test_real_prediction_uncertainty_guidance_and_raw_input(self):
        cases = (
            ("Tomato___Early_blight", "Tomato Early Blight", True),
            ("Apple___Black_rot", "Apple Black rot", True),
            ("Unknown___Condition", "Unknown Condition", False),
        )
        for label, disease, reviewed in cases:
            guidance = main.DISEASE_INFO[label] if reviewed else FALLBACK_GUIDANCE
            for confidence, uncertain in ((0.25, True), (0.50, False), (0.90, False)):
                with self.subTest(label=label, confidence=confidence):
                    predictor = Mock(model_loaded=True, use_mock=False)
                    predictor.predict.return_value = (label, confidence)
                    with patch.object(main, "CONFIDENCE_THRESHOLD", 0.50):
                        response = self.post_image(predictor)
                    self.assertEqual(200, response.status_code)
                    self.assertEqual({
                        "model_label": label, "disease": disease, "confidence": confidence,
                        "uncertain": uncertain, "guidance_available": reviewed, **guidance,
                    }, response.json())
                    predictor.predict.assert_called_once()
                    tensor = predictor.predict.call_args.args[0]
                    self.assertEqual((1, 224, 224, 3), tensor.shape)
                    self.assertEqual(np.dtype("float32"), tensor.dtype)
                    np.testing.assert_array_equal(tensor[0, 0, 0], [30.0, 180.0, 60.0])

    def test_unavailable_model_returns_503(self):
        response = self.post_image(model_loader.ModelPredictor(main.CLASS_NAMES))
        self.assertEqual(503, response.status_code)
        self.assertEqual(
            {"detail": "Real model is not loaded. Check /health and server logs."}, response.json()
        )

    def test_malformed_model_scores_return_500(self):
        for value in (np.nan, 0.0):
            with self.subTest(score=value):
                model = Mock(spec=["predict"])
                model.predict.return_value = np.full((1, 38), value, dtype=np.float32)
                response = self.post_image(model_loader.ModelPredictor(main.CLASS_NAMES, model=model))
                self.assertEqual(500, response.status_code)
                self.assertEqual({"detail": "Model prediction failed."}, response.json())
                model.predict.assert_called_once()

    def test_invalid_uploads(self):
        cases = (
            (b"not an image", "text/plain", 400),
            (b"not an image", "image/png", 400),
            (b"", "image/png", 400),
            (b"x" * 17, "image/png", 413),
        )
        for content, mime, status in cases:
            with self.subTest(content=content, mime=mime), patch.object(main, "MAX_IMAGE_SIZE_BYTES", 16):
                self.assertEqual(status, self.post_image(content=content, mime=mime).status_code)


if __name__ == "__main__":
    unittest.main()
