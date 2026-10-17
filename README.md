# Cropora

Cropora is a developing Android application for plant-leaf disease detection. A
user can capture a leaf photo or select one from the device, choose Cloud or
Offline detection, and view the predicted disease, confidence score, symptoms,
treatment guidance, and prevention information. Scans can be saved to a local
history, and an optional daily reminder encourages regular scanning.

The project is being developed in stages. The Android interface, image-selection
flow, cloud API integration, offline inference integration, local history, and
disease library are implemented. The approved Keras artifact is supplied locally,
and a generated TensorFlow Lite asset (`model.tflite`) is committed under the
Android assets. Model validation, on-device verification, and several application
features remain in progress.

> Cropora is an educational project and is not a substitute for diagnosis or
> advice from a qualified agricultural professional.

## Product design

Cropora uses two prediction paths:

- **Cloud Mode** — the Android app sends a leaf image as a multipart request to
  the FastAPI backend. The backend validates and preprocesses the image, runs a
  TensorFlow/Keras model, and returns a structured prediction.
- **Offline Mode** — the Android app runs a TensorFlow Lite model stored on the
  device, allowing predictions without an internet connection.

Both paths produce a common result containing the disease name, confidence score,
uncertainty status, symptoms, treatment, and prevention guidance. The result can
be saved to the local scan history.

![Cropora logo](android-app-kotlin/app_icon_source.png.jpg)

More detail about the original architecture is available in
[`docs/evidence/week-01/system-sketch.md`](docs/evidence/week-01/system-sketch.md).

## Current development status

| Component | Status | Notes |
| --- | --- | --- |
| Android project structure and screens | Implemented | Main, scan, result, history and history detail, disease library and detail, settings, and analytics activities exist. The analytics screen is a layout-only placeholder. |
| Camera and gallery image selection | Implemented | The scan screen supports camera capture, gallery selection, image preview, permissions, and state restoration. |
| FastAPI backend structure | Implemented | Configuration, label loading, image validation, preprocessing, prediction response handling, and API tests are present. |
| Mock backend prediction | Implemented | Allows API development and testing without TensorFlow or a trained model. It is not a real diagnosis. |
| Real cloud prediction | Local artifact supplied | The model is available locally at `backend-api/models/cropora_model.keras`. Prior validation claims are recorded in the provenance file; they were not independently rerun. |
| Android-to-backend connection | Implemented | Retrofit uploads selected images to `POST /predict` and maps successful responses into the result screen. The backend address can be overridden at runtime from the Settings screen without rebuilding the app. |
| Offline TensorFlow Lite prediction | Integration added; asset present | The app runs `model.tflite` locally when Offline is selected. The asset is committed at `android-app-kotlin/app/src/main/assets/model.tflite` and its conversion is recorded in `release-records/tflite-provenance.txt`. On-device verification is still pending. |
| Local scan history | Implemented | The result screen can save a scan to a local Room database. The history screen lists saved scans, and a detail screen shows the full record with a delete action. |
| Local disease library | Implemented | The Android app loads reviewed disease guidance from `android-app-kotlin/app/src/main/assets/diseases.xml` and opens detail views for each entry. |
| Daily scan reminder | Implemented | A toggle in Settings schedules a periodic WorkManager reminder (every 24 hours) that posts a notification. |

The backend includes 38 model labels. Reviewed symptom, treatment, and prevention
guidance is currently available for 10 classes; other recognized classes return
generic guidance until their content is added.

## Architecture

```mermaid
flowchart TD
    A[Android app] --> B[Capture or select leaf image]
    B --> C{Prediction mode}
    C --> D[Cloud Mode]
    C --> E[Offline Mode]
    D --> F[FastAPI POST /predict]
    F --> G[Keras model - supplied locally]
    E --> H[TensorFlow Lite model - model.tflite in app assets]
    G --> I[Unified prediction result]
    H --> I
    I --> J[Result and disease guidance]
    J --> K[Local scan history - Room database]
```

## Repository structure

```text
Cropora/
├── android-app-kotlin/   Kotlin Android application (Gradle project, assets, tests)
├── backend-api/          FastAPI cloud-prediction service and shared inference contract
├── docs/                 Architecture notes, development evidence, and Kotlin learning examples
├── model/                Model conversion, inspection, contract and parity tests, and model notes
└── release-records/      Model and TFLite provenance and recorded approval claims
```

## Android application

### Requirements

- Android Studio with Android SDK 34
- JDK 17, as required by the configured Android Gradle plugin
- An Android device or emulator running API 24 or newer

The app depends on TensorFlow Lite runtime 2.14.0, Room 2.6.1, Retrofit 2.9.0,
and WorkManager 2.9.0.

### Offline model asset

Offline mode uses `android-app-kotlin/app/src/main/assets/model.tflite`, which is
committed to this repository, along with `labels.txt` and `diseases.xml`. Its
conversion is recorded in
[`release-records/tflite-provenance.txt`](release-records/tflite-provenance.txt).

To regenerate the asset, run this from the project root with TensorFlow and Pillow
installed:

```bash
python model/convert_model.py
```

The converter validates the pinned Keras source, tensor contract, and probability
output before writing the Android asset. See [`model/model_notes.md`](model/model_notes.md)
for contract, parity, and provenance details. Cloud mode does not require the TFLite
asset.

> **Version note:** the model contract is recorded against TensorFlow 2.19.1,
> `backend-api/requirements.txt` currently requires `tensorflow>=2.21.0`, and the
> committed TFLite record lists TensorFlow 2.21.0 as the converter. The Android
> runtime is 2.14.0. Align these versions and verify offline inference on a device
> or emulator before release.

### Run the app

1. Open `android-app-kotlin/` in Android Studio.
2. Allow Gradle to synchronize the project.
3. Select an emulator or connected device.
4. Run the `app` configuration.

You can also build from the command line:

```bash
cd android-app-kotlin
./gradlew assembleDebug
```

On Windows PowerShell, use `./gradlew.bat assembleDebug`.

Unit tests and instrumented tests are in `app/src/test` and `app/src/androidTest`:

```bash
./gradlew test
./gradlew connectedAndroidTest   # requires a connected device or emulator
```

The Android app uses `http://10.0.2.2:8000/` by default so an Android emulator
can reach a backend running on the development computer. To use a connected
physical device, bind Uvicorn to `0.0.0.0`, replace the host with the computer's
LAN address, and pass it as a Gradle property:

```bash
./gradlew assembleDebug -PCROPORA_API_BASE_URL=http://192.168.1.10:8000/
```

The device and development computer must be on the same network. The debug build
allows cleartext HTTP for local hosts; use HTTPS outside local development. Cloud
prediction uploads to the configured backend. Offline prediction runs locally and
does not require a connection.

The backend address can also be changed after install from the app's Settings
screen. The entered value is validated, persisted, and overrides the compiled
default immediately, so switching servers does not require a rebuild.

## Backend API

### Requirements

- Python 3.11
- A virtual environment is recommended
- TensorFlow is optional for mock-mode development and required for real model
  inference

### Setup

```bash
cd backend-api
python -m venv .venv
```

Activate the environment:

```bash
# Windows PowerShell
.\.venv\Scripts\Activate.ps1

# macOS or Linux
source .venv/bin/activate
```

Install the lightweight dependencies:

```bash
pip install -r requirements-base.txt
```

For mock development, set these environment variables, or put them in
`backend-api/.env` (a template is provided as `backend-api/.env.example`):

```dotenv
USE_MOCK=true
IMAGE_SIZE=224
```

Mock mode must be enabled explicitly: the backend default is `USE_MOCK=false`.
Missing TensorFlow or a failed real-model load does not silently enable mock
predictions; real prediction remains unavailable.

For real mode, install `requirements.txt` instead of only the lightweight
requirements, set `USE_MOCK=false`, and supply the approved model described below.
Keep `IMAGE_SIZE=224`.

### Configuration

Settings are read from environment variables (or `backend-api/.env`) in `config.py`:

| Variable | Default | Purpose |
| --- | --- | --- |
| `USE_MOCK` | `false` | Enables mock predictions. Accepts `1`, `true`, `yes`, or `on`. |
| `MODEL_PATH` | `backend-api/models/cropora_model.keras` | Path to the Keras model. |
| `LABELS_PATH` | `backend-api/labels-38.txt` | Serving label file; order must match the model. |
| `IMAGE_SIZE` | `224` | Input size. Must remain `224` for the approved model. |
| `CONFIDENCE_THRESHOLD` | `0.50` | Predictions below this value are flagged `uncertain`. |
| `MAX_IMAGE_SIZE_BYTES` | `10485760` (10 MB) | Upload size limit; larger files return HTTP 413. |
| `ALLOWED_ORIGINS` | `*` | Comma-separated CORS origins. |
| `PORT` | `8000` | Port used by the Docker image. |

Start the API with:

```bash
uvicorn main:app --reload
```

For a physical Android device on the same network, listen on all interfaces:

```bash
uvicorn main:app --reload --host 0.0.0.0
```

The service runs at `http://127.0.0.1:8000` by default. Interactive API
documentation is available at `http://127.0.0.1:8000/docs`.

### API endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/` | Basic service health response |
| `GET` | `/health` | Runtime configuration and model-loading status |
| `GET` | `/diseases` | Reviewed disease guidance currently available from the backend |
| `POST` | `/predict` | Accepts an image in the multipart field `image` and returns a prediction |

A successful `/predict` response contains these fields: `model_label`, `disease`,
`confidence`, `uncertain`, `guidance_available`, `symptoms`, `treatment`, and
`prevention`. Error responses are returned for a non-image content type or empty
file (400), an invalid image file (400), an oversized upload (413), a real model
that is not loaded (503), and an unexpected prediction failure (500).

For real-mode readiness, `/health` should report `use_mock=false`,
`model_loaded=true`, and `class_count=38`. These flags do not prove successful
inference, pinned artifact identity, or prediction accuracy. Preserve a separate
real-mode `/predict` response as smoke-test evidence.

### Real model requirements

By default, the backend looks for:

```text
backend-api/models/cropora_model.keras
```

An artifact has been supplied at this path in the current workspace. Local
presence does not establish version-control or release inclusion; provision the
approved artifact explicitly for other checkouts and deployments.

The expected model contract is:

- Framework dependency: TensorFlow 2.19.1 as recorded in the contract; this is not
  a Keras version number. See the version note in the Android section.
- Exactly one `float32` input shaped `(None, 224, 224, 3)` or `(1, 224, 224, 3)`.
  The API sends one RGB image with raw values in `[0, 255]`.
- Exactly one `float32` output shaped `(None, 38)` or `(1, 38)`.
- Serving labels must match `backend-api/labels-38.txt` exactly in order; never
  sort them. `model/labels-38.txt` is the matching tooling copy.
- Embedded preprocessing must map raw RGB `[0, 255]` to `[-1, 1]`, whether expressed
  as Keras `Rescaling` or supported `Divide`/`Subtract` operations. Do not normalize
  pixels again in the caller.
- `IMAGE_SIZE` must remain `224` for the approved model.
- At prediction time, output must have shape `(1, 38)` and contain finite
  probabilities in `[0, 1]` summing to one within numerical tolerance. Invalid
  outputs are rejected, not silently clipped into confidence scores.

`backend-api/inference_contract.py` supplies the shared validation used by
`backend-api/model_loader.py` and the `model/model_contract.py` tooling facade.
The backend does not require the sibling `model/` directory at deployment time.
Runtime contract checks are distinct from the inspector's pinned size/SHA-256
check.

See [`model/model_notes.md`](model/model_notes.md) for inspection and test commands,
and [`release-records/model-provenance.txt`](release-records/model-provenance.txt)
for the source, pinned artifact identity, and historical approval assertions.
Pre-push static checks confirmed artifact size/SHA-256, matching label files,
Python syntax, and archive metadata. No tests or model inference were run as part
of that documentation review; prior runtime claims and prediction accuracy remain
unverified.

### Run backend tests

From `backend-api/`, with the virtual environment active:

```bash
pip install -r requirements-dev.txt
python -m unittest discover -s . -p "test_*.py" -v
```

Discovery includes the API, shared-contract, and model-loader tests. Model tooling
tests check supplied-artifact identity without TensorFlow; structural artifact tests
skip when TensorFlow or the artifact is missing. A passing suite with
skips is not full artifact validation. Use the non-skipping inspector gate in
[`model/model_notes.md`](model/model_notes.md) for that check.

### Docker

From the project root, build and run a lightweight image for mock mode:

```bash
docker build -t cropora-api backend-api
docker run --rm -p 8000:8000 -e USE_MOCK=true cropora-api
```

`INSTALL_TENSORFLOW` defaults to `false`, but the backend's `USE_MOCK` default is
also `false`. The lightweight image therefore requires explicit `USE_MOCK=true`;
omitting TensorFlow does not select mock mode automatically.

For real inference, include TensorFlow and mount the externally supplied model
read-only. Replace `/absolute/path/to/cropora_model.keras` with its absolute host
path:

```bash
docker build --build-arg INSTALL_TENSORFLOW=true -t cropora-api backend-api
docker run --rm -p 8000:8000 -e USE_MOCK=false -e IMAGE_SIZE=224 --mount "type=bind,source=/absolute/path/to/cropora_model.keras,target=/app/models/cropora_model.keras,readonly" cropora-api
```

`models/*.keras` is excluded from the Docker build context, regardless of local
presence or version-control status. The canonical serving labels and shared
validator are inside `backend-api/`; no sibling `model/` mount is required.

## Development roadmap

The remaining integration and validation steps are:

1. Capture reproducible approved-artifact inspection and real-mode API evidence,
   including the Android scan flow and result mapping.
2. Verify offline Android inference on a device or emulator, and reconcile the
   TensorFlow versions noted above before release.
3. Expand reviewed disease guidance beyond the current 10 classes.
4. Build out the analytics screen, which is currently a placeholder.

These plans describe the intended direction of the developing product and may be
adjusted as implementation and model testing continue.
