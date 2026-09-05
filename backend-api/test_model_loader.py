from pathlib import Path
import unittest
from unittest.mock import Mock, call, patch

import numpy as np

import model_loader as loader
from inference_contract import load_labels


LABELS = load_labels()


class ModelPredictorTest(unittest.TestCase):
    def setUp(self):
        self.image = np.full((1, 224, 224, 3), 127.5, dtype=np.float32)
        self.model = Mock(spec=["predict"])
        self.predictor = loader.ModelPredictor(LABELS, model=self.model)

    def test_real_prediction_uses_contract_and_returns_argmax(self):
        for index in (0, 18, 37):
            with self.subTest(index=index):
                scores = np.full((1, 38), 0.25 / 37, dtype=np.float32)
                scores[0, index] = 0.75
                self.model.predict.return_value = scores
                self.model.predict.reset_mock()
                with patch.object(loader, "validate_prediction", wraps=loader.validate_prediction) as validate:
                    self.assertEqual((LABELS[index], 0.75), self.predictor.predict(self.image))
                validate.assert_called_once_with(scores, LABELS)
                self.model.predict.assert_called_once_with(self.image, verbose=0)

    def test_contract_failure_propagates_without_fallback(self):
        output = self.model.predict.return_value
        with patch.object(loader, "validate_prediction", side_effect=ValueError("invalid scores")) as validate:
            with self.assertRaisesRegex(ValueError, "invalid scores"):
                self.predictor.predict(self.image)
        validate.assert_called_once_with(output, LABELS)

    def test_mock_maps_and_clamps_raw_intensities(self):
        predictor = loader.ModelPredictor(LABELS, use_mock=True)
        for intensity, index in ((-255.0, 0), (0.0, 0), (127.5, 18), (255.0, 37), (510.0, 37)):
            with self.subTest(intensity=intensity):
                image = np.full_like(self.image, intensity)
                label, confidence = predictor.predict(image)
                self.assertEqual(LABELS[index], label)
                self.assertAlmostEqual(0.70 + (index % 3) * 0.08, confidence)

    def test_unavailable_predictor_does_not_fall_back_to_mock(self):
        with self.assertRaises(RuntimeError):
            loader.ModelPredictor(LABELS).predict(self.image)


class LoadPredictorTest(unittest.TestCase):
    def test_loading_modes_and_validation_failures(self):
        # The step count checks both ordering and that failed loading stops immediately.
        cases = {
            "real": 3, "mock": 0, "no_tensorflow": 0, "missing_file": 0,
            "wrong_size": 0, "load_error": 1, "invalid_model": 2, "invalid_rescaling": 3,
        }
        for mode, steps in cases.items():
            with self.subTest(mode=mode):
                model = Mock(spec=["predict"])
                pipeline = Mock()
                pipeline.load.return_value = model
                tf = Mock()
                tf.keras.models.load_model = pipeline.load
                path = Mock(spec=Path)
                path.is_file.return_value = mode != "missing_file"
                failure = {
                    "load_error": pipeline.load,
                    "invalid_model": pipeline.validate,
                    "invalid_rescaling": pipeline.rescaling,
                }.get(mode)
                if failure is not None:
                    failure.side_effect = ValueError(mode)
                with patch.multiple(
                    loader, USE_MOCK=mode == "mock", IMAGE_SIZE=128 if mode == "wrong_size" else 224,
                    tf=None if mode == "no_tensorflow" else tf, Path=Mock(return_value=path),
                    validate_keras_model=pipeline.validate, find_embedded_rescaling=pipeline.rescaling,
                ):
                    predictor = loader.load_predictor(LABELS)

                self.assertEqual(mode == "real", predictor.model_loaded)
                self.assertEqual(mode == "mock", predictor.use_mock)
                self.assertIs(model if mode == "real" else None, predictor.model)
                self.assertEqual(LABELS, predictor.class_names)
                expected_calls = [
                    call.load(path, compile=False), call.validate(model, LABELS), call.rescaling(model),
                ]
                self.assertEqual(expected_calls[:steps], pipeline.mock_calls)
                model.predict.assert_not_called()


if __name__ == "__main__":
    unittest.main()
