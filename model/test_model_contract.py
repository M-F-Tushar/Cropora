import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import model_contract

try:
    import tensorflow as tf
except (ImportError, OSError):
    tf = None

from model_contract import (
    BACKEND_LABELS,
    DEFAULT_KERAS_MODEL,
    DEFAULT_LABELS,
    EXPECTED_CLASS_COUNT,

    find_embedded_rescaling,
    load_labels,
    validate_artifact,
    validate_keras_model,
)


class CloudModelContractTest(unittest.TestCase):
    def test_canonical_and_backend_labels_match(self):
        canonical = load_labels(DEFAULT_LABELS)
        backend = load_labels(BACKEND_LABELS)
        self.assertEqual(EXPECTED_CLASS_COUNT, len(canonical))
        self.assertEqual(canonical, backend)

    def test_tooling_reuses_serving_validators(self):
        import inference_contract

        self.assertIs(inference_contract.validate_keras_model, validate_keras_model)
        self.assertIs(inference_contract.find_embedded_rescaling, find_embedded_rescaling)

    @unittest.skipIf(tf is None, "TensorFlow is required for Keras contract checks")
    def test_approved_keras_model_contract(self):
        if not DEFAULT_KERAS_MODEL.is_file():
            self.skipTest("Approved artifact is not supplied; use inspect_model.py for the required gate")
        validate_artifact(DEFAULT_KERAS_MODEL)
        model = tf.keras.models.load_model(DEFAULT_KERAS_MODEL, compile=False)
        input_shape, output_shape = validate_keras_model(model, load_labels())
        self.assertIn(input_shape[0], (None, 1))
        self.assertEqual((224, 224, 3), input_shape[1:])
        self.assertIn(output_shape[0], (None, 1))
        self.assertEqual((EXPECTED_CLASS_COUNT,), output_shape[1:])
        self.assertIsNotNone(find_embedded_rescaling(model))



class ArtifactIdentityTest(unittest.TestCase):
    def test_supplied_artifact_identity_without_tensorflow(self):
        if not DEFAULT_KERAS_MODEL.is_file():
            self.skipTest("Approved artifact is not supplied")
        self.assertEqual(model_contract.APPROVED_MODEL_SHA256, validate_artifact())

    def test_missing_artifact_is_rejected(self):
        with TemporaryDirectory() as directory:
            with self.assertRaises(FileNotFoundError):
                validate_artifact(Path(directory) / "missing.keras")

    def test_size_and_hash_are_both_required(self):
        payload = b"test artifact bytes"
        expected_hash = hashlib.sha256(payload).hexdigest()
        with TemporaryDirectory() as directory, patch.object(
            model_contract, "APPROVED_MODEL_SIZE", len(payload)
        ), patch.object(model_contract, "APPROVED_MODEL_SHA256", expected_hash):
            path = Path(directory) / "candidate.keras"
            path.write_bytes(payload)
            self.assertEqual(expected_hash, validate_artifact(path))
            path.write_bytes(payload + b"x")
            with self.assertRaisesRegex(ValueError, "size"):
                validate_artifact(path)
            path.write_bytes(b"x" * len(payload))
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                validate_artifact(path)


if __name__ == "__main__":
    unittest.main()