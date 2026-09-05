"""Shared serving contract, kept inside the backend's Docker build context."""

from numbers import Real
from pathlib import Path

import numpy as np

EXPECTED_INPUT_SHAPE = (1, 224, 224, 3)
EXPECTED_CLASS_COUNT = 38
CANONICAL_LABELS = Path(__file__).with_name("labels-38.txt")


def load_labels(path=CANONICAL_LABELS):
    labels = [
        line.strip() for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if len(labels) != EXPECTED_CLASS_COUNT or len(set(labels)) != len(labels):
        raise ValueError(f"Expected {EXPECTED_CLASS_COUNT} unique labels in {path}")
    return labels


def validate_labels(labels):
    if list(labels) != load_labels():
        raise ValueError("Labels must match the canonical 38-class serving order exactly")


def validate_shape(actual, expected, name):
    actual = tuple(actual)
    if not actual or actual[0] not in (None, 1) or actual[1:] != tuple(expected)[1:]:
        raise ValueError(f"Expected {name} shape {tuple(expected)} (batch None or 1), got {actual}")


def _dtype_name(tensor):
    return getattr(tensor.dtype, "name", str(tensor.dtype))


def validate_keras_model(model, labels):
    validate_labels(labels)
    if len(model.inputs) != 1 or len(model.outputs) != 1:
        raise ValueError("Expected exactly one Keras input and one Keras output")
    for name, tensor, shape in (
        ("input", model.inputs[0], EXPECTED_INPUT_SHAPE),
        ("output", model.outputs[0], (1, EXPECTED_CLASS_COUNT)),
    ):
        validate_shape(tensor.shape, shape, f"Keras {name}")
        if _dtype_name(tensor) != "float32":
            raise ValueError(f"Expected Keras float32 {name}, got {tensor.dtype}")
    return tuple(model.inputs[0].shape), tuple(model.outputs[0].shape)


def find_embedded_rescaling(model):
    """Check every raw-input path to the first learned layer, without inference."""
    message = "Expected embedded preprocessing mapping raw RGB [0, 255] to [-1, 1]."
    if len(model.inputs) != 1 or len(model.outputs) != 1:
        raise ValueError(message + " Expected one input and one output.")
    verified = []

    def scalar(value):
        if not isinstance(value, Real) or not np.isfinite(value):
            raise ValueError(message + " Expected finite scalar constants.")
        return float(value)

    def normalized(state):
        if state is not None:
            if not np.allclose(state[:2], (1.0 / 127.5, -1.0), rtol=1e-6, atol=1e-8):
                raise ValueError(message + " Missing, bypassed, or incorrect normalization.")
            verified.append(state[2])

    def visit(tensor, cache):
        if id(tensor) in cache:
            return cache[id(tensor)]
        history = getattr(tensor, "_keras_history", None)
        operation = getattr(history, "operation", getattr(history, "layer", None))
        if operation is None:
            raise ValueError(message + " Cannot inspect the Keras graph.")
        node = operation._inbound_nodes[history.node_index]
        inputs = list(node.input_tensors)
        if not inputs:
            raise ValueError(message + " Unconnected input path.")
        states = [visit(item, cache) for item in inputs]
        # None marks learned features; otherwise track (scale, offset, operation).
        state = None
        if hasattr(operation, "layers") and hasattr(operation, "inputs"):
            if len(operation.inputs) != len(states):
                raise ValueError(message + " Cannot resolve nested model inputs.")
            seeds = {id(item): value for item, value in zip(operation.inputs, states)}
            state = visit(operation.outputs[history.tensor_index], seeds)
        elif any(value is not None for value in states):
            kind = operation.__class__.__name__
            builtin = operation.__class__.__module__.startswith(("keras.", "tensorflow.python.keras."))
            if not builtin or len(states) != 1:
                raise ValueError(message + f" Unsupported preprocessing: {kind}.")
            state = states[0]
            if kind in {"Conv2D", "DepthwiseConv2D", "SeparableConv2D", "Dense"}:
                normalized(state)
                state = None
            elif kind in {
                "GlobalAveragePooling2D", "GlobalMaxPooling2D", "AveragePooling2D",
                "MaxPooling2D", "Flatten", "Reshape", "ZeroPadding2D",
            }:
                normalized(state)
            elif kind in {
                "Identity", "Dropout", "SpatialDropout2D", "RandomFlip", "RandomRotation", "RandomZoom",
            } or (
                kind == "Activation" and operation.get_config().get("activation") == "linear"
            ):
                # Functional.predict propagates training=False, including to augmentation layers.
                pass  # Keep affine state so these no-ops cannot hide double normalization.
            elif kind in {"Rescaling", "Divide", "TrueDivide", "Subtract", "Add", "Multiply"}:
                scale, offset, _ = state
                if kind == "Rescaling":
                    config = operation.get_config()
                    factor = scalar(config.get("scale"))
                    scale, offset = scale * factor, offset * factor + scalar(config.get("offset", 0.0))
                else:
                    args = getattr(getattr(node, "arguments", None), "args", ())
                    if len(args) == 2 and kind in {"Add", "Multiply"} and args[1] is inputs[0]:
                        args = (args[1], args[0])
                    if len(args) != 2 or args[0] is not inputs[0]:
                        raise ValueError(message + " Expected tensor-first scalar arithmetic.")
                    constant = scalar(args[1])
                    if kind in {"Divide", "TrueDivide"}:
                        if constant == 0.0:
                            raise ValueError(message + " Division by zero.")
                        scale, offset = scale / constant, offset / constant
                    elif kind in {"Add", "Subtract"}:
                        offset += constant if kind == "Add" else -constant
                    else:
                        scale, offset = scale * constant, offset * constant
                if tuple(tensor.shape)[1:] != EXPECTED_INPUT_SHAPE[1:] or _dtype_name(tensor) != "float32":
                    raise ValueError(message + " Preprocessing must preserve RGB shape and float32 dtype.")
                state = (scale, offset, operation)
            else:
                raise ValueError(message + f" Unsupported preprocessing: {kind}.")
        cache[id(tensor)] = state
        return state

    normalized(visit(model.outputs[0], {id(model.inputs[0]): (1.0, 0.0, None)}))
    if not verified:
        raise ValueError(message)
    return verified[0]


def validate_prediction(predictions, labels):
    if isinstance(predictions, (list, tuple, dict)):
        raise ValueError("Expected a single prediction tensor")
    predictions = np.asarray(predictions)
    if len(labels) != EXPECTED_CLASS_COUNT or predictions.shape != (1, EXPECTED_CLASS_COUNT):
        raise ValueError(f"Expected prediction shape (1, 38) and 38 labels, got {predictions.shape}")
    if predictions.dtype != np.float32:
        raise ValueError(f"Expected float32 predictions, got {predictions.dtype}")
    if not np.all(np.isfinite(predictions)) or np.any(predictions < 0.0) or np.any(predictions > 1.0):
        raise ValueError("Expected finite probability scores in [0, 1]")
    if not np.isclose(np.sum(predictions[0], dtype=np.float64), 1.0, rtol=1e-5, atol=1e-6):
        raise ValueError("Expected class probabilities summing to 1; logits are not supported")
    return predictions[0]
