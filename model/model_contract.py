"""Inspection helpers backed by the same contract used for API inference."""

import hashlib
from pathlib import Path
import sys

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
# Pinned identity recorded in release-records/model-provenance.txt.
APPROVED_MODEL_SIZE = 25_143_175
APPROVED_MODEL_SHA256 = "08f285aff6d9e1ab88d4d5b2269f1cc977714003755f8553887edbf8691b325f"


def load_labels(path: Path = DEFAULT_LABELS):
    return _load_labels(path)


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
