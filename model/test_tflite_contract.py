import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import numpy as np
from PIL import Image

try:
    import tensorflow as tf
except (ImportError, OSError):
    tf = None

from model_contract import (
    DEFAULT_KERAS_MODEL,
    DEFAULT_LABELS,
    EXPECTED_CLASS_COUNT,
    EXPECTED_INPUT_SHAPE,
    load_labels,
    preprocess_image,
    tensor_details,
    validate_keras_model,
    validate_tflite_prediction,
)

ROOT = Path(__file__).resolve().parent.parent
TFLITE_MODEL = ROOT / "android-app-kotlin" / "app" / "src" / "main" / "assets" / "model.tflite"
ANDROID_LABELS = ROOT / "android-app-kotlin" / "app" / "src" / "main" / "assets" / "labels.txt"


def synthetic_rgb_images(directory: Path):
    height, width = 96, 128
    y, x = np.mgrid[:height, :width]
    fixtures = (
        np.stack((x * 2, y * 2, (x + y) % 256), axis=-1),
        np.stack(((x * 3 + 40) % 256, (y * 3 + 80) % 256, (x + y * 2) % 256), axis=-1),
        np.stack(((x + y * 2) % 256, (x * 2 + 120) % 256, (y * 3 + 30) % 256), axis=-1),
    )
    paths = []
    for index, pixels in enumerate(fixtures):
        path = directory / f"fixture_{index}.png"
        Image.fromarray(pixels.astype(np.uint8)).save(path)
        paths.append(path)
    return paths


class AndroidLabelContractTest(unittest.TestCase):
    def test_android_labels_match_canonical_order(self):
        self.assertTrue(ANDROID_LABELS.is_file(), f"Missing Android labels: {ANDROID_LABELS}")
        labels = load_labels(DEFAULT_LABELS)
        self.assertEqual(labels, load_labels(ANDROID_LABELS))
        self.assertEqual(EXPECTED_CLASS_COUNT, len(labels))

    def test_tflite_tensor_metadata_contract_without_runtime(self):
        class StubInterpreter:
            def get_input_details(self):
                return [{"shape": np.array(EXPECTED_INPUT_SHAPE), "dtype": np.float32}]

            def get_output_details(self):
                return [{"shape": np.array((1, EXPECTED_CLASS_COUNT)), "dtype": np.float32}]

        input_detail, output_detail = tensor_details(StubInterpreter())
        self.assertEqual(EXPECTED_INPUT_SHAPE, tuple(input_detail["shape"]))
        self.assertEqual((1, EXPECTED_CLASS_COUNT), tuple(output_detail["shape"]))

    def test_tflite_predictions_must_be_probabilities(self):
        with self.assertRaisesRegex(ValueError, "probabilities"):
            validate_tflite_prediction(np.zeros((1, EXPECTED_CLASS_COUNT), dtype=np.float32))


@unittest.skipIf(tf is None, "TensorFlow is required for TFLite contract checks")
class TFLiteContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not TFLITE_MODEL.is_file():
            raise unittest.SkipTest("Run model/convert_model.py to generate the Android TFLite asset")
        cls.labels = load_labels(DEFAULT_LABELS)
        cls.interpreter = tf.lite.Interpreter(model_path=str(TFLITE_MODEL))
        cls.interpreter.allocate_tensors()
        cls.input_detail, cls.output_detail = tensor_details(cls.interpreter)

    def test_tflite_tensor_contract(self):
        self.assertEqual(EXPECTED_INPUT_SHAPE, tuple(self.input_detail["shape"]))
        self.assertEqual(np.float32, self.input_detail["dtype"])
        self.assertEqual((1, EXPECTED_CLASS_COUNT), tuple(self.output_detail["shape"]))
        self.assertEqual(np.float32, self.output_detail["dtype"])

    def test_preprocessing_keeps_raw_rgb_values(self):
        with TemporaryDirectory() as directory:
            fixture = np.zeros((32, 48, 3), dtype=np.uint8)
            fixture[:, :, 0] = np.arange(48, dtype=np.uint8)
            fixture[:, :, 1] = np.arange(32, dtype=np.uint8)[:, None]
            fixture[:, :, 2] = 255
            path = Path(directory) / "preprocessing.png"
            Image.fromarray(fixture, mode="RGB").save(path)
            image = preprocess_image(path)

        self.assertEqual(EXPECTED_INPUT_SHAPE, image.shape)
        self.assertEqual(np.float32, image.dtype)
        self.assertGreaterEqual(float(image.min()), 0.0)
        self.assertLessEqual(float(image.max()), 255.0)

    def test_synthetic_keras_tflite_parity(self):
        keras_model = tf.keras.models.load_model(DEFAULT_KERAS_MODEL, compile=False)
        validate_keras_model(keras_model, self.labels)
        with TemporaryDirectory() as directory:
            for image_path in synthetic_rgb_images(Path(directory)):
                image = preprocess_image(image_path)
                keras_scores = np.asarray(keras_model.predict(image, verbose=0))
                tflite_scores = self._predict_tflite(image)
                keras_index = int(np.argmax(keras_scores[0]))
                tflite_index = int(np.argmax(tflite_scores))
                self.assertEqual(keras_index, tflite_index)
                self.assertLessEqual(
                    abs(float(keras_scores[0, keras_index]) - float(tflite_scores[tflite_index])),
                    0.02,
                )


    def _predict_tflite(self, image):
        self.interpreter.set_tensor(self.input_detail["index"], image)
        self.interpreter.invoke()
        return validate_tflite_prediction(
            self.interpreter.get_tensor(self.output_detail["index"])
        )


if __name__ == "__main__":
    unittest.main()
