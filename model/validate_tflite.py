#!/usr/bin/env python3
import argparse
from pathlib import Path

import numpy as np
import tensorflow as tf

from model_contract import (
    DEFAULT_LABELS,
    load_labels,
    preprocess_image,
    tensor_details,
    validate_labels,
    validate_tflite_prediction,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Run one image through the Cropora TFLite model.")
    parser.add_argument("image", type=Path)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    args = parser.parse_args()

    labels = load_labels(args.labels)
    validate_labels(labels)
    interpreter = tf.lite.Interpreter(model_path=str(args.model))
    interpreter.allocate_tensors()
    input_detail, output_detail = tensor_details(interpreter)
    interpreter.set_tensor(input_detail["index"], preprocess_image(args.image))
    interpreter.invoke()
    scores = validate_tflite_prediction(interpreter.get_tensor(output_detail["index"]))
    best_index = int(np.argmax(scores))
    print(f"Prediction: {labels[best_index]}")
    print(f"Confidence: {float(scores[best_index]):.6f}")


if __name__ == "__main__":
    main()