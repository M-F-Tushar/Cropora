#!/usr/bin/env python3
import argparse
from pathlib import Path

from model_contract import (
    DEFAULT_KERAS_MODEL,
    DEFAULT_LABELS,
    find_embedded_rescaling,
    load_labels,
    validate_artifact,
    validate_keras_model,
    validate_labels,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect the approved Cropora Keras artifact and contract.")
    parser.add_argument("--keras-model", type=Path, default=DEFAULT_KERAS_MODEL)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    args = parser.parse_args()

    labels = load_labels(args.labels)
    validate_labels(labels)
    print(f"Labels: {len(labels)} in canonical serving order ({args.labels})")
    print("Caller preprocessing: resize to 224x224 RGB and keep raw float32 [0, 255].")

    checksum = validate_artifact(args.keras_model)
    print(f"Approved artifact SHA-256: {checksum}")

    import tensorflow as tf

    print(f"TensorFlow: {tf.__version__}")
    model = tf.keras.models.load_model(args.keras_model, compile=False)
    input_shape, output_shape = validate_keras_model(model, labels)
    rescaling = find_embedded_rescaling(model)
    print(f"Keras input: shape={input_shape}, dtype={model.inputs[0].dtype}")
    print(f"Keras output: shape={output_shape}, dtype={model.outputs[0].dtype}")
    print(f"Embedded preprocessing: {rescaling.name} maps [0, 255] to [-1, 1]")
    print("Keras structural contract: valid; prediction execution and accuracy are not tested.")


if __name__ == "__main__":
    main()