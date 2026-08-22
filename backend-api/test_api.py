import io
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from PIL import Image

import main


class CroporaApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    @staticmethod
    def make_png() -> bytes:
        output = io.BytesIO()
        Image.new("RGB", (32, 32), color=(30, 180, 60)).save(output, format="PNG")
        return output.getvalue()

    def test_health_aliases_report_runtime_mode(self):
        for path in ("/", "/health"):
            response = self.client.get(path)
            self.assertEqual(200, response.status_code)
            self.assertEqual("ok", response.json()["status"])
            self.assertEqual(38, response.json()["class_count"])

    def test_disease_library_keeps_ten_reviewed_entries(self):
        response = self.client.get("/diseases")
        self.assertEqual(200, response.status_code)
        self.assertEqual(10, response.json()["count"])
        self.assertEqual(10, len(response.json()["diseases"]))

    def test_predict_accepts_valid_image(self):
        with patch.object(main.predictor, "use_mock", True):
            response = self.client.post(
                "/predict",
                files={"image": ("leaf.png", self.make_png(), "image/png")},
            )
        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn(payload["model_label"], main.CLASS_NAMES)
        self.assertGreaterEqual(payload["confidence"], 0.0)
        self.assertLessEqual(payload["confidence"], 1.0)

    def test_predict_returns_503_without_real_model(self):
        if main.predictor.model_loaded:
            self.skipTest("Real model is available in this environment")
        with patch.object(main.predictor, "use_mock", False):
            response = self.client.post(
                "/predict",
                files={"image": ("leaf.png", self.make_png(), "image/png")},
            )
        self.assertEqual(503, response.status_code)

    def test_preprocessing_keeps_raw_rgb_values(self):
        tensor = main.preprocess_image(self.make_png())
        self.assertEqual((1, 224, 224, 3), tensor.shape)
        self.assertEqual(180.0, float(tensor[0, 0, 0, 1]))

    def test_predict_rejects_non_image(self):
        response = self.client.post(
            "/predict",
            files={"image": ("notes.txt", b"not an image", "text/plain")},
        )
        self.assertEqual(400, response.status_code)

    def test_predict_rejects_spoofed_image(self):
        response = self.client.post(
            "/predict",
            files={"image": ("fake.png", b"not an image", "image/png")},
        )
        self.assertEqual(400, response.status_code)

    def test_predict_rejects_oversized_upload(self):
        with patch.object(main, "MAX_IMAGE_SIZE_BYTES", 16):
            response = self.client.post(
                "/predict",
                files={"image": ("large.png", b"x" * 17, "image/png")},
            )
        self.assertEqual(413, response.status_code)


if __name__ == "__main__":
    unittest.main()