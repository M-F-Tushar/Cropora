import logging
from pathlib import Path
from typing import List, Sequence, Tuple

import numpy as np

from config import IMAGE_SIZE, MODEL_PATH, USE_MOCK

logger = logging.getLogger(__name__)

try:
    import tensorflow as tf
except Exception as exc:  # pragma: no cover - depends on environment
    tf = None
    logger.warning("TensorFlow import failed, mock predictor will be used: %s", exc)


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
        predictions = np.asarray(predictions, dtype=np.float32)
        if predictions.ndim == 1:
            predictions = np.expand_dims(predictions, axis=0)

        scores = predictions[0]
        best_index = int(np.argmax(scores))
        confidence = max(0.0, min(1.0, float(scores[best_index])))
        disease_name = self.class_names[best_index] if best_index < len(self.class_names) else f"Class {best_index}"
        return disease_name, confidence

    def _mock_predict(self, image_batch: np.ndarray) -> Tuple[str, float]:
        if not self.class_names:
            return "Unknown disease", 0.50

        mean_intensity = float(np.mean(image_batch))
        scaled_index = int(round(mean_intensity * (len(self.class_names) - 1)))
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

    if not model_path.exists():
        logger.error("Model file not found at %s. Real inference is disabled.", model_path)
        return ModelPredictor(class_names=class_names)

    try:
        model = tf.keras.models.load_model(model_path)
        input_shape = tuple(model.input_shape)
        output_shape = tuple(model.output_shape)
        if len(input_shape) != 4 or input_shape[1:] != (IMAGE_SIZE, IMAGE_SIZE, 3):
            raise ValueError(
                f"Expected model input shape (None, {IMAGE_SIZE}, {IMAGE_SIZE}, 3), "
                f"got {input_shape}"
            )
        if len(output_shape) != 2 or output_shape[-1] != len(class_names):
            raise ValueError(
                f"Model output count {output_shape[-1]} does not match label count {len(class_names)}"
            )
        logger.info("Loaded Keras model from %s", model_path)
        return ModelPredictor(class_names=class_names, model=model)
    except Exception:
        logger.exception("Failed to load or validate model from %s. Real inference is disabled.", model_path)
        return ModelPredictor(class_names=class_names)