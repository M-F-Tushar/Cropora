from pathlib import Path
from typing import List


def load_labels(path: str) -> List[str]:
    label_path = Path(path)
    if not label_path.is_file():
        raise RuntimeError(f"Canonical labels file not found: {label_path}")

    labels = [
        line.strip()
        for line in label_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if not labels:
        raise RuntimeError(f"Canonical labels file is empty: {label_path}")
    if len(labels) != len(set(labels)):
        raise RuntimeError(f"Canonical labels file contains duplicates: {label_path}")
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