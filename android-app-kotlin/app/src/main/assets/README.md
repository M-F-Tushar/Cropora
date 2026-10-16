# Android prediction assets

The Android scanner supports both the existing Cloud API path and local TensorFlow
Lite inference. The offline classifier expects these files in this directory:

- `model.tflite` — a float32 model with input `[1, 224, 224, 3]` and output
  `[1, 38]` probabilities.
- `labels.txt` — the 38 output labels in the exact canonical order from
  `model/labels-38.txt` and `backend-api/labels-38.txt`.
- `diseases.xml` — the reviewed local disease guidance used by both the disease
  library and offline prediction results.

Generate `model.tflite` from the approved Keras artifact at the project root:

```bash
python model/convert_model.py
```

The converter verifies the pinned Keras artifact, checks the converted tensor
contract, runs a probability-output smoke test, and synchronizes `labels.txt`.
The Android build keeps `.tflite` assets uncompressed so the interpreter can map
the model directly from the APK. The expected extension is `.tflite` (not
`.tf-lite`); it must remain `model.tflite` to match `TFLiteClassifier`.

Offline mode shares the cloud result data contract. It resizes RGB images to
224x224 with bilinear filtering, passes raw float32 pixel values in `[0, 255]`
(the model contains its own normalization), selects the highest class
probability, and uses the same 0.50 uncertainty threshold as the backend default.
The local XML catalog has reviewed details for only 10 classes; other classes
receive generic guidance, as they do when the backend has no reviewed entry.

The converted binary is generated locally and is not present in this checkout.
See [`../../../../../model/model_notes.md`](../../../../../model/model_notes.md) and
[`../../../../../release-records/tflite-provenance.txt`](../../../../../release-records/tflite-provenance.txt)
for conversion, parity, validation, and artifact-record instructions.
