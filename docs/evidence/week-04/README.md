# Week 4 Evidence

## What was completed

Week 4 added the initial FastAPI backend for Cropora Cloud Mode. It supports health checks, disease guidance, image-upload validation, preprocessing, mock prediction, and preparation for a future Keras model.

## Evidence

- `01-backend-tests-passed.png.png` — all eight backend tests completed with `OK`.
- `02-backend-startup.png.png` — Uvicorn started the API successfully in mock mode.
- `03-health-endpoint.png.png` — `/health` reported `status: ok`, mock mode, 38 labels, and a `224` image size.
- `04-api-documentation.png.png` — Swagger UI displayed `/`, `/health`, `/diseases`, and `/predict`.

## How to test

From `backend-api/` in PowerShell:

```powershell
.\.venv\Scripts\Activate.ps1
python -m unittest test_api.py
$env:USE_MOCK = "true"
python -m uvicorn main:app --reload
```

Then open `http://127.0.0.1:8000/health` and `http://127.0.0.1:8000/docs`. The TensorFlow warning and `model_loaded: false` are expected because the real Keras model is not included. This evidence verifies the backend and mock workflow, not real prediction accuracy or Android-to-backend integration.
