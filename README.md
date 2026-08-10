
# Cropora

Cropora is an Android plant-disease detection application. A user photographs
or selects a picture of a plant leaf and the app returns the most likely
disease, a confidence score, and care/treatment guidance. Predictions can be
made in two ways: 

- **Cloud Mode** — the photo is uploaded to the Cropora backend API (FastAPI +
  a TensorFlow/Keras model) which returns a JSON prediction.
- **Offline Mode** — a bundled TensorFlow Lite model runs directly on the
  device, so predictions work without an internet connection.

Scan results are stored locally (Room database) so users can review their
scan history, and an in-app disease library (backed by an XML dataset)
provides symptom/treatment/prevention information for each supported disease.

## Cropora Pipleline Diagram

![Cropora screenshot](docs/evidence/week-01/Cropora.jpg)

## Repository layout

```
android-app/    Kotlin Android application (Cropora mobile app)
backend-api/    FastAPI backend used by the app's Cloud Mode
docs/           Product documentation and evidence
```

## Getting started

### Android app

1. Open the `android-app/` folder in Android Studio.
2. Let Gradle sync (`android-app/gradlew` is included as the wrapper).
3. Place a converted `model.tflite` and matching `labels.txt` in
   `android-app/app/src/main/assets/` to enable Offline Mode
   (see `backend-api/README.md` for model details).
4. Run the `app` module on an emulator or device.

### Backend API

```bash
cd backend-api
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-base.txt
cp .env.example .env   # edit MODEL_PATH etc. as needed
uvicorn main:app --reload
```

The API listens on `http://127.0.0.1:8000` by default and exposes a
`/predict` endpoint that accepts a multipart image upload and returns a JSON
disease prediction.


