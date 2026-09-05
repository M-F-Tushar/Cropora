from pathlib import Path
from typing import List

from inference_contract import load_labels as load_contract_labels, validate_labels


def load_labels(path: str) -> List[str]:
    label_path = Path(path)
    if not label_path.is_file():
        raise RuntimeError(f"Canonical labels file not found: {label_path}")

    try:
        labels = load_contract_labels(label_path)
        validate_labels(labels)
    except ValueError as exc:
        raise RuntimeError(f"Invalid canonical labels in {label_path}: {exc}") from exc
    return labels


def display_label(model_label: str) -> str:
    overrides = {
        "Apple___Apple_scab": "Apple Scab",
        "Corn___Cercospora_leaf_spot Gray_leaf_spot": "Corn Gray Leaf Spot",
        "Corn___Northern_Leaf_Blight": "Corn Northern Leaf Blight",
        "Potato___Early_blight": "Potato Early Blight",
        "Potato___Late_blight": "Potato Late Blight",
        "Tomato___Early_blight": "Tomato Early Blight",
        "Tomato___Late_blight": "Tomato Late Blight",
    }
    if model_label in overrides:
        return overrides[model_label]
    crop, separator, condition = model_label.partition("___")
    if not separator:
        return model_label.replace("_", " ")
    return f"{crop.replace('_', ' ')} {condition.replace('_', ' ')}"