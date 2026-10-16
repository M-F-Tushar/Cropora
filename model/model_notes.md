# Cropora 38-Class Model Contract

## Recorded Approved Artifact

- Source repository: `Muhammad-Hassan12/Plant-Disease-Detector`
- Pinned source commit: `f6165bd93524dfb77a9629aae70db845832d1b01`
- Source artifact: `Models/model_4_mobilenet_finetuned.keras`
- Local path: `backend-api/models/cropora_model.keras`
- Pinned size: 25,143,175 bytes
- SHA-256: `08f285aff6d9e1ab88d4d5b2269f1cc977714003755f8553887edbf8691b325f`
- Source repository license claim: MIT

The artifact is supplied locally at the path above. Local presence is separate
from version-control or release inclusion; Docker deployments must supply it
externally. The source, approval assertions, and prior validation claims are in
[`release-records/model-provenance.txt`](../release-records/model-provenance.txt).
The pre-push static check on 2026-09-05 confirmed the recorded size/SHA-256,
matching label files, Python syntax, and archive metadata. Model execution,
API requests, and accuracy were not tested; prior runtime claims remain unverified.

## Tensor Contract

- Framework dependency: TensorFlow 2.19.1. The archive records Keras 3.10.0;
  the installed Keras version is resolved separately by dependency installation.
- Architecture: fine-tuned MobileNetV2 classifier
- Input: exactly one `float32` tensor with signature `(None, 224, 224, 3)` or
  `(1, 224, 224, 3)`; the API sends a batch of one
- Color: RGB
- Caller preprocessing: decode, convert to RGB, resize to 224x224, cast to `float32`
- Caller pixel range: raw `[0, 255]`; do not normalize again in the caller
- The archived `RandomFlip`, `RandomRotation`, and `RandomZoom` layers are
  inference-time no-ops under Functional model prediction (`training=False`).
- Embedded preprocessing: maps raw RGB `[0, 255]` to `[-1, 1]`, using Keras
  `Rescaling(scale=1/127.5, offset=-1)` or supported `Divide`/`Subtract` operations
  equivalent to `input / 127.5 - 1`
- Output: exactly one `float32` tensor with signature `(None, 38)` or `(1, 38)`
- Runtime output: shape `(1, 38)`, finite probabilities in `[0, 1]`, summing to one
  within numerical tolerance; invalid outputs are rejected rather than clipped
- Label mapping: output indices use `backend-api/labels-38.txt` exactly; never sort
  it. Configured serving labels and the tooling copy `model/labels-38.txt` must
  match this canonical order
- Selection: `argmax(output[0])`; confidence is the validated probability at that index
- Configuration: `IMAGE_SIZE` must remain `224` for this approved model

`backend-api/inference_contract.py` is the shared contract implementation used by
`backend-api/model_loader.py` and the `model/model_contract.py` tooling facade.
The backend validates shape, dtype, label order, and embedded preprocessing at
load time, then checks probabilities on each prediction. Preprocessing inspection
traces supported built-in affine operations through nested models without running
inference; unsupported transforms before the first convolution/dense layer are
rejected rather than assumed safe. It does not need the sibling `model/` directory in its Docker deployment. Model loading uses
`compile=False`; inference validation does not require training configuration.


## API Compatibility

- Request: `POST /predict`
- Encoding: `multipart/form-data`
- Field: `image`
- Response: `model_label`, `disease`, `confidence`, `uncertain`, `guidance_available`, `symptoms`, `treatment`, `prevention`

Real-mode readiness requires `/health` to report `use_mock=false`,
`model_loaded=true`, and `class_count=38`. These flags are not proof of successful
inference, pinned artifact identity, or accuracy. A real-mode `/predict` request
is a separate smoke test, and accuracy requires a representative evaluation.

## Validation

These are instructions for a future validation run, not commands executed during
this documentation review. From the project root, use an activated backend
virtual environment so `python` resolves consistently on Windows, macOS, or Linux.

### Unit tests and optional artifact check

```bash
python -m unittest discover -s backend-api -p "test_*.py" -v
python -m unittest discover -s model -p "test_*.py" -v
```

The backend tests use `backend-api/requirements-dev.txt`. Model tests separate
pure contract checks from TensorFlow-dependent checks. Supplied-artifact identity
is checked without TensorFlow and skips only when the artifact is missing; the
structural artifact test also skips when TensorFlow is unavailable. Review the skip
summary: a passing suite with skips does not establish full model validation.

### Required artifact inspection gate

With TensorFlow from `backend-api/requirements.txt` installed and the approved
artifact supplied:

```bash
python model/inspect_model.py
```

The inspector checks the pinned size and SHA-256, verifies labels against the
canonical serving order, and loads the model with `compile=False` to validate its
structural and embedded-preprocessing contract. This is the explicit full
artifact inspection gate: missing dependencies, a missing artifact, or a failed
check must fail rather than skip. It does not prove prediction accuracy or
replace runtime probability checks.

Preserve the inspector output, dependency versions, real-mode `/health` response,
and a successful `/predict` response with the unchanged eight-field JSON as
separate evidence. Historical provenance claims alone do not replace these
records.

## Limitations

Matching the two label files establishes consistency within Cropora, not independent
proof of the source model's training class indices. Confirm that mapping against
the pinned source's class-index evidence before treating predictions as validated.
The backend accepts contract-compatible models at `MODEL_PATH`; only the inspector
checks the pinned artifact identity. Structural inspection cannot establish runtime
correctness or the behavior of arbitrary custom layers.

The source author's published 98.75% score is not independently measured Cropora accuracy. Controlled PlantVillage images do not represent every phone-camera background, crop, disease, blur, or lighting condition. Confidence is not certainty. Low-confidence output must remain uncertain, and users should verify serious cases with a qualified agricultural source.

## TensorFlow Lite and Android Offline Inference

The Android scanner exposes Cloud and Offline modes. Both produce the existing
`PredictionResponse` contract and use the same result, disease-guidance, and
history screens. Cloud mode uploads to `POST /predict`; Offline mode runs the
TFLite interpreter on the device without a network request. The local XML catalog
currently provides reviewed guidance for 10 labels; other labels use the same
generic fallback guidance text as the API.

The offline model contract matches the cloud contract:

- Exactly one float32 input tensor shaped `[1, 224, 224, 3]` and one float32 output
  tensor shaped `[1, 38]`.
- The 38 outputs are finite probabilities in `[0, 1]` summing to one. Logits,
  quantized tensors, multiple inputs/outputs, and mismatched label counts are
  rejected.
- `labels.txt` preserves the order in `model/labels-38.txt` and
  `backend-api/labels-38.txt`; the converter synchronizes the Android copy.
- Input preprocessing is RGB resize to 224x224 with bilinear filtering and raw
  float32 values `[0, 255]`. Android's filtered bitmap scaling and the Python
  cloud/tooling preprocessing use bilinear resize. Do not normalize in the caller;
  the approved model embeds the `[0, 255]` to `[-1, 1]` transform.
- The predicted class is `argmax`; confidence is its probability, and the local
  uncertainty threshold is 0.50, matching the backend default.

### Convert and validate

Use the project Python environment with TensorFlow 2.19.1 and Pillow installed.
From the project root:

```bash
python model/convert_model.py
python -m unittest discover -s model -p "test_*.py" -v
```

The converter refuses an artifact with the wrong pinned size or SHA-256, verifies
the Keras input, output, labels, and embedded rescaling contract, converts without
quantization, then allocates the converted model and runs a probability-output
smoke test. On success it writes `android-app-kotlin/app/src/main/assets/model.tflite`
and synchronizes `labels.txt` in that assets directory. The Android build uses
TensorFlow Lite runtime 2.14.0 and leaves `.tflite` assets uncompressed for
memory-mapped loading. Because conversion uses TensorFlow 2.19.1, verify the
model with the Android runtime on a device or emulator before release. The required
filename is `model.tflite` (the extension is `.tflite`, not `.tf-lite`).

`test_tflite_contract.py` compares the converted model with Keras on deterministic
synthetic RGB fixtures, checks raw-value preprocessing and tensor details, and
checks the Android label ordering. TensorFlow-dependent checks skip when TensorFlow
or the generated model is absent; run the converter first for full coverage. For
an additional image-specific check, pass a representative leaf photo to:

```bash
python model/validate_tflite.py path/to/leaf.jpg \
  --model android-app-kotlin/app/src/main/assets/model.tflite
python model/parity_test.py path/to/leaf.jpg \
  --tflite-model android-app-kotlin/app/src/main/assets/model.tflite
```

After a successful conversion, record TensorFlow version, output byte size, and
SHA-256 in [`../release-records/tflite-provenance.txt`](../release-records/tflite-provenance.txt).
A successful conversion or parity check is not an accuracy evaluation; do not
interpret confidence as certainty.
