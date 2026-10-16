#!/usr/bin/env python3
import argparse
import shutil
from pathlib import Path

import numpy as np
import tensorflow as tf

from model_contract import (
    ANDROID_ASSETS_DIR,
    BACKEND_LABELS,
    DEFAULT_KERAS_MODEL,
    DEFAULT_LABELS,
    find_embedded_rescaling,
    load_labels,
    tensor_details,
    validate_artifact,
    validate_keras_model,
    validate_tflite_prediction,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert the approved Cropora Keras model to TFLite.")
    parser.add_argument("--keras-model", type=Path, default=DEFAULT_KERAS_MODEL)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    args = parser.parse_args()

    if not args.keras_model.is_file():
        raise SystemExit(f"Model not found: {args.keras_model}")

    validate_artifact(args.keras_model)
    labels = load_labels(args.labels)
    model = tf.keras.models.load_model(args.keras_model, compile=False)
    validate_keras_model(model, labels)
    find_embedded_rescaling(model)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_detail, output_detail = tensor_details(interpreter)
    interpreter.set_tensor(input_detail["index"], np.zeros((1, 224, 224, 3), dtype=np.float32))
    interpreter.invoke()
    validate_tflite_prediction(interpreter.get_tensor(output_detail["index"]))

    shutil.copyfile(args.labels, BACKEND_LABELS)
    print(f"Synchronized {BACKEND_LABELS}")
    ANDROID_ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    model_path = ANDROID_ASSETS_DIR / "model.tflite"
    model_path.write_bytes(tflite_model)
    labels_path = ANDROID_ASSETS_DIR / "labels.txt"
    shutil.copyfile(args.labels, labels_path)
    print(f"Wrote {model_path} ({len(tflite_model)} bytes)")
    print(f"Synchronized {labels_path}")


if __name__ == "__main__":
    main()