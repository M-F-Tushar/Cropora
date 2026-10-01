"""Inspection helpers for the shared cloud and offline model contract."""

import hashlib
from pathlib import Path
import sys

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = ROOT / "backend-api"
# Tooling can depend on the backend; the standalone backend must not depend on tooling.
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from inference_contract import (
    EXPECTED_CLASS_COUNT,
    EXPECTED_INPUT_SHAPE,
    find_embedded_rescaling,
    load_labels as _load_labels,
    validate_keras_model,
    validate_labels,
    validate_shape,
)

DEFAULT_KERAS_MODEL = BACKEND_DIR / "models" / "cropora_model.keras"
DEFAULT_LABELS = ROOT / "model" / "labels-38.txt"
BACKEND_LABELS = BACKEND_DIR / "labels-38.txt"
ANDROID_ASSETS_DIR = ROOT / "android-app-kotlin" / "app" / "src" / "main" / "assets"
# Pinned identity recorded in release-records/model-provenance.txt.
APPROVED_MODEL_SIZE = 25_143_175
APPROVED_MODEL_SHA256 = "08f285aff6d9e1ab88d4d5b2269f1cc977714003755f8553887edbf8691b325f"


def load_labels(path: Path = DEFAULT_LABELS):
    return _load_labels(path)


def preprocess_image(path: Path) -> np.ndarray:
    with Image.open(path) as image:
        rgb_image = image.convert("RGB").resize((224, 224), Image.Resampling.BILINEAR)
        return np.expand_dims(np.asarray(rgb_image, dtype=np.float32), axis=0)


def tensor_details(interpreter):
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    if len(input_details) != 1 or len(output_details) != 1:
        raise ValueError("Cropora requires exactly one input tensor and one output tensor")
    input_detail = input_details[0]
    output_detail = output_details[0]
    validate_shape(input_detail["shape"], EXPECTED_INPUT_SHAPE, "TFLite input")
    if input_detail["dtype"] != np.float32:
        raise ValueError(f"Expected TFLite float32 input, got {input_detail['dtype']}")
    if tuple(int(value) for value in output_detail["shape"]) != (1, EXPECTED_CLASS_COUNT):
        raise ValueError(
            f"Expected TFLite output shape (1, {EXPECTED_CLASS_COUNT}), "
            f"got {tuple(output_detail['shape'])}"
        )
    if output_detail["dtype"] != np.float32:
        raise ValueError(f"Expected TFLite float32 output, got {output_detail['dtype']}")
    return input_detail, output_detail


def validate_tflite_prediction(predictions: np.ndarray) -> np.ndarray:
    predictions = np.asarray(predictions)
    if predictions.shape != (1, EXPECTED_CLASS_COUNT):
        raise ValueError(
            f"Expected TFLite prediction shape (1, {EXPECTED_CLASS_COUNT}), "
            f"got {predictions.shape}"
        )
    if predictions.dtype != np.float32:
        raise ValueError(f"Expected float32 TFLite predictions, got {predictions.dtype}")
    if not np.all(np.isfinite(predictions)) or np.any(predictions < 0.0) or np.any(predictions > 1.0):
        raise ValueError("Expected finite TFLite probabilities in [0, 1]")
    if not np.isclose(np.sum(predictions[0], dtype=np.float64), 1.0, rtol=1e-5, atol=1e-6):
        raise ValueError("Expected TFLite class probabilities summing to 1; logits are not supported")
    return predictions[0]


def validate_artifact(path: Path = DEFAULT_KERAS_MODEL) -> str:
    path = Path(path)
    if not path.is_file():
        raise FileNotFoundError(f"Keras model not found: {path}")
    if path.stat().st_size != APPROVED_MODEL_SIZE:
        raise ValueError(f"Model size does not match the approved {APPROVED_MODEL_SIZE}-byte artifact: {path}")
    with path.open("rb") as artifact:
        checksum = hashlib.file_digest(artifact, "sha256").hexdigest()
    if checksum != APPROVED_MODEL_SHA256:
        raise ValueError(f"Model SHA-256 does not match the approved artifact: {path}")
    return checksum
