import logging
from pathlib import Path
from typing import List, Sequence, Tuple

import numpy as np

from config import IMAGE_SIZE, MODEL_PATH, USE_MOCK
from inference_contract import (
    EXPECTED_INPUT_SHAPE,
    find_embedded_rescaling,
    validate_keras_model,
    validate_prediction,
)

logger = logging.getLogger(__name__)

try:
    import tensorflow as tf
except Exception as exc:  # pragma: no cover - depends on environment
    tf = None
    logger.warning("TensorFlow import failed; real inference is unavailable: %s", exc)


class ModelPredictor:
    """Wrapper that exposes a single predict method for real or mock inference."""

    def __init__(self, class_names: Sequence[str], model=None, use_mock: bool = False):
        self.class_names: List[str] = list(class_names)
        self.model = model
        self.use_mock = use_mock
        self.model_loaded = model is not None

    def predict(self, image_batch: np.ndarray) -> Tuple[str, float]:
        if self.use_mock:
            return self._mock_predict(image_batch)
        if self.model is None:
            raise RuntimeError("Real model is unavailable. Check /health and server logs.")

        predictions = self.model.predict(image_batch, verbose=0)
        scores = validate_prediction(predictions, self.class_names)
        best_index = int(np.argmax(scores))
        return self.class_names[best_index], float(scores[best_index])

    def _mock_predict(self, image_batch: np.ndarray) -> Tuple[str, float]:
        if not self.class_names:
            return "Unknown disease", 0.50

        mean_intensity = float(np.mean(image_batch))
        scaled_index = int(round((mean_intensity / 255.0) * (len(self.class_names) - 1)))
        best_index = max(0, min(len(self.class_names) - 1, scaled_index))
        confidence = round(0.70 + ((best_index % 3) * 0.08), 2)
        return self.class_names[best_index], min(confidence, 0.99)


def load_predictor(class_names: Sequence[str]) -> ModelPredictor:
    model_path = Path(MODEL_PATH)

    if USE_MOCK:
        logger.info("USE_MOCK enabled. Skipping model load and using mock predictor.")
        return ModelPredictor(class_names=class_names, use_mock=True)

    if tf is None:
        logger.error("TensorFlow is unavailable. Real inference is disabled.")
        return ModelPredictor(class_names=class_names)

    if not model_path.is_file():
        logger.error("Model file not found at %s. Real inference is disabled.", model_path)
        return ModelPredictor(class_names=class_names)

    try:
        if IMAGE_SIZE != EXPECTED_INPUT_SHAPE[1]:
            raise ValueError(f"The approved model requires IMAGE_SIZE={EXPECTED_INPUT_SHAPE[1]}")
        model = tf.keras.models.load_model(model_path, compile=False)
        validate_keras_model(model, class_names)
        find_embedded_rescaling(model)
        logger.info("Loaded Keras model from %s", model_path)
        return ModelPredictor(class_names=class_names, model=model)
    except Exception:
        logger.exception("Failed to load or validate model from %s. Real inference is disabled.", model_path)
        return ModelPredictor(class_names=class_names)