from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest

import numpy as np

import inference_contract as contract


LABELS = contract.load_labels()


def model_stub(batch=None, dtype="float32"):
    return SimpleNamespace(
        inputs=[SimpleNamespace(shape=(batch, 224, 224, 3), dtype=dtype)],
        outputs=[SimpleNamespace(shape=(batch, 38), dtype=dtype)],
    )


class InferenceContractTest(unittest.TestCase):
    def test_label_files(self):
        cases = {
            "valid": "  # heading\n\n" + "\n".join(f"  {label}  \n# comment" for label in LABELS),
            "empty": "", "too_few": "\n".join(LABELS[:-1]),
            "too_many": "\n".join(LABELS + ["extra"]),
            "duplicate": "\n".join(LABELS[:-1] + [LABELS[0]]),
        }
        with TemporaryDirectory() as directory:
            path = Path(directory) / "labels.txt"
            for name, text in cases.items():
                with self.subTest(labels=name):
                    path.write_text(text, encoding="utf-8")
                    if name == "valid":
                        self.assertEqual(LABELS, contract.load_labels(path))
                    else:
                        with self.assertRaises(ValueError):
                            contract.load_labels(path)

    def test_canonical_label_order(self):
        self.assertEqual(38, len(LABELS))
        contract.validate_labels(iter(LABELS))
        reordered = LABELS.copy()
        reordered[0], reordered[1] = reordered[1], reordered[0]
        with self.assertRaises(ValueError):
            contract.validate_labels(reordered)
        with self.assertRaises(ValueError):
            contract.validate_keras_model(model_stub(), reordered)

    def test_valid_tensor_metadata(self):
        for batch in (None, 1):
            for dtype in ("float32", np.dtype("float32"), SimpleNamespace(name="float32")):
                with self.subTest(batch=batch, dtype=dtype):
                    self.assertEqual(
                        ((batch, 224, 224, 3), (batch, 38)),
                        contract.validate_keras_model(model_stub(batch, dtype), LABELS),
                    )

    def test_invalid_tensor_metadata(self):
        shapes = {
            "inputs": [(), (2, 224, 224, 3), (None, 128, 128, 3),
                       (None, 224, 224, 1), (None, 3, 224, 224), (224, 224, 3)],
            "outputs": [(), (2, 38), (None, 37), (None, 39), (38,), (None, 1, 38)],
        }
        for side, values in shapes.items():
            mutations = [("shape", shape) for shape in values] + [
                ("dtype", dtype) for dtype in ("float16", np.dtype("float64"), SimpleNamespace(name="uint8"))
            ]
            for attribute, value in mutations:
                with self.subTest(side=side, attribute=attribute, value=value):
                    model = model_stub()
                    setattr(getattr(model, side)[0], attribute, value)
                    with self.assertRaises(ValueError):
                        contract.validate_keras_model(model, LABELS)

    def test_single_input_and_output_required(self):
        for side in ("inputs", "outputs"):
            for count in (0, 2):
                with self.subTest(side=side, count=count):
                    model = model_stub()
                    setattr(model, side, getattr(model, side) * count)
                    with self.assertRaises(ValueError):
                        contract.validate_keras_model(model, LABELS)
                    with self.assertRaises(ValueError):
                        contract.find_embedded_rescaling(model)

    def test_valid_probability_rows(self):
        one_hot = np.zeros((1, 38), dtype=np.float32)
        one_hot[0, 37] = 1.0
        for scores in (one_hot, np.full((1, 38), 1.0 / 38, dtype=np.float32)):
            with self.subTest(confidence=float(scores.max())):
                result = contract.validate_prediction(scores, LABELS)
                self.assertEqual(np.dtype("float32"), result.dtype)
                np.testing.assert_array_equal(scores[0], result)

    def test_invalid_probabilities(self):
        valid = np.zeros((1, 38), dtype=np.float32)
        valid[0, 37] = 1.0
        negative = np.zeros_like(valid)
        negative[0, :3] = (0.75, 0.50, -0.25)
        above_one = np.zeros_like(valid)
        above_one[0, :2] = (1.25, -0.25)
        cases = {
            "unbatched": valid[0], "multiple_batches": np.repeat(valid, 2, axis=0),
            "empty_batch": valid[:0], "too_few_classes": valid[:, :37],
            "too_many_classes": np.pad(valid, ((0, 0), (0, 1))), "extra_dimension": valid[np.newaxis],
            "float64": valid.astype(np.float64), "integer": valid.astype(np.int32),
            "list": [valid], "tuple": (valid,), "dict": {"scores": valid},
            "negative": negative, "above_one": above_one, "zero_sum": np.zeros_like(valid),
            "sum_below_one": valid * 0.5, "sum_above_one": np.full_like(valid, 0.5),
        }
        for value in (np.nan, np.inf, -np.inf):
            scores = valid.copy()
            scores[0, 0] = value
            cases[f"nonfinite_{value}"] = scores
        for name, scores in cases.items():
            with self.subTest(output=name), self.assertRaises(ValueError):
                contract.validate_prediction(scores, LABELS)
        for count in (37, 38):
            with self.subTest(label_count=37, output_count=count), self.assertRaises(ValueError):
                contract.validate_prediction(np.full((1, count), 1.0 / count, dtype=np.float32), LABELS[:-1])


class EmbeddedRescalingGraphTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        try:
            import tensorflow as tf
        except (ImportError, OSError) as exc:
            raise unittest.SkipTest(f"TensorFlow unavailable: {exc}") from exc
        cls.keras = tf.keras

    def setUp(self):
        self.addCleanup(self.keras.backend.clear_session)

    def image_input(self):
        return self.keras.Input(shape=(224, 224, 3), dtype="float32")

    def head(self, tensor):
        if len(tensor.shape) == 4:
            tensor = self.keras.layers.GlobalAveragePooling2D()(tensor)
        return self.keras.layers.Dense(38, activation="softmax")(tensor)

    def rescale(self, pixels):
        return self.keras.layers.Rescaling(1.0 / 127.5, offset=-1.0)(pixels)

    def nested(self, pixels, transform):
        inner = self.image_input()
        return self.keras.Model(inner, transform(inner))(pixels)

    def test_preprocessing_graphs(self):
        layers, ops = self.keras.layers, self.keras.ops
        valid = {
            "rescaling": self.rescale,
            "approved_augmentation": lambda raw: self.rescale(
                layers.RandomZoom(0.1)(layers.RandomRotation(0.2)(layers.RandomFlip()(raw)))
            ),
            "operators": lambda raw: raw / 127.5 - 1.0,
            "divide": lambda raw: ops.subtract(ops.divide(raw, 127.5), 1.0),
            "true_divide": lambda raw: ops.subtract(ops.true_divide(raw, 127.5), 1.0),
            "subtract_then_divide": lambda raw: (raw - 127.5) / 127.5,
            "multiply_then_add": lambda raw: raw * (1.0 / 127.5) + (-1.0),
            "commuted_multiply_then_add": lambda raw: -1.0 + (1.0 / 127.5) * raw,
            "nested_preprocessing": lambda raw: self.nested(raw, lambda inner: inner / 127.5 - 1.0),
            "nested_seed_and_features": lambda raw: self.nested(raw / 127.5, lambda inner: self.head(inner - 1.0)),
            "sequential": lambda raw: self.keras.Sequential([
                self.image_input(), layers.Rescaling(1.0 / 127.5, offset=-1.0),
            ])(raw),
        }
        invalid = {
            "missing": lambda raw: raw,
            "wrong_scale": lambda raw: layers.Rescaling(1.0 / 255.0, offset=-1.0)(raw),
            "wrong_offset": lambda raw: layers.Rescaling(1.0 / 127.5)(raw),
            "double": lambda raw: self.rescale(self.rescale(raw)),
            "extra_offset": lambda raw: self.rescale(raw) + 1.0,
            "extra_factor": lambda raw: self.rescale(raw) * 0.5,
            "merged_pixels": lambda raw: layers.Add()([self.rescale(raw), self.rescale(raw)]),
            "nested_double": lambda raw: self.nested(self.rescale(raw), self.rescale),
            "reversed_division": lambda raw: 127.5 / raw - 1.0,
            "reversed_subtraction": lambda raw: 1.0 - raw / 127.5,
            "raw_bypass_first": lambda raw: layers.Add()([raw, self.rescale(raw)]),
            "raw_bypass_second": lambda raw: layers.Add()([self.rescale(raw), raw]),
            "features_only": lambda raw: self.rescale(layers.GlobalAveragePooling2D()(raw)),
            "scaling_after_pooling": lambda raw: self.rescale(layers.GlobalAveragePooling2D()(self.rescale(raw))),
            "unknown_transform": lambda raw: layers.ReLU()(self.rescale(raw)),
            "double_across_identity": lambda raw: self.rescale(layers.Identity()(self.rescale(raw))),
            "double_across_augmentation": lambda raw: self.rescale(layers.RandomFlip()(self.rescale(raw))),
        }
        for accepted, transforms in ((True, valid), (False, invalid)):
            for name, transform in transforms.items():
                with self.subTest(graph=name, accepted=accepted):
                    raw = self.image_input()
                    model = self.keras.Model(raw, self.head(transform(raw)))
                    contract.validate_keras_model(model, LABELS)
                    if accepted:
                        self.assertIsNotNone(contract.find_embedded_rescaling(model))
                    else:
                        with self.assertRaises(ValueError):
                            contract.find_embedded_rescaling(model)


if __name__ == "__main__":
    unittest.main()
