#!/usr/bin/env python3
import argparse
from pathlib import Path

import numpy as np
import tensorflow as tf

from model_contract import (
    DEFAULT_KERAS_MODEL,
    DEFAULT_LABELS,
    load_labels,
    preprocess_image,
    tensor_details,
    validate_artifact,
    validate_keras_model,
    validate_tflite_prediction,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare Keras and TFLite predictions for images.")
    parser.add_argument("images", type=Path, nargs="+")
    parser.add_argument("--keras-model", type=Path, default=DEFAULT_KERAS_MODEL)
    parser.add_argument("--tflite-model", type=Path, required=True)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument("--max-confidence-delta", type=float, default=0.02)
    args = parser.parse_args()

    labels = load_labels(args.labels)
    validate_artifact(args.keras_model)
    keras_model = tf.keras.models.load_model(args.keras_model, compile=False)
    validate_keras_model(keras_model, labels)
    interpreter = tf.lite.Interpreter(model_path=str(args.tflite_model))
    interpreter.allocate_tensors()
    input_detail, output_detail = tensor_details(interpreter)

    failed = False
    for image_path in args.images:
        image = preprocess_image(image_path)
        keras_scores = np.asarray(keras_model.predict(image, verbose=0))[0]
        interpreter.set_tensor(input_detail["index"], image)
        interpreter.invoke()
        tflite_scores = validate_tflite_prediction(
            interpreter.get_tensor(output_detail["index"])
        )
        keras_index = int(np.argmax(keras_scores))
        tflite_index = int(np.argmax(tflite_scores))
        delta = abs(float(keras_scores[keras_index]) - float(tflite_scores[tflite_index]))
        passed = keras_index == tflite_index and delta <= args.max_confidence_delta
        failed = failed or not passed
        print(
            f"{image_path}: {'PASS' if passed else 'FAIL'} "
            f"Keras={labels[keras_index]} ({keras_scores[keras_index]:.6f}) "
            f"TFLite={labels[tflite_index]} ({tflite_scores[tflite_index]:.6f}) "
            f"delta={delta:.6f}"
        )

    if failed:
        raise SystemExit("Keras/TFLite parity failed")


if __name__ == "__main__":
    main()