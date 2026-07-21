## Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        CROPORA ANDROID APP                       │
│                             (KOTLIN)                             │
└──────────────────────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
            ┌───────▼────────┐         ┌────────▼────────┐
            │   CLOUD MODE   │         │  OFFLINE MODE   │
            │                │         │                 │
            │  Retrofit HTTP │         │  TensorFlow     │
            │  POST Image    │         │  Lite Inference │
            └───────┬────────┘         └────────┬────────┘
                    │                           │
         ┌──────────▼──────────┐                │
         │  FASTAPI BACKEND    │                │
         │  (Python, Uvicorn)  │                │
         │                     │                │
         │  /predict endpoint  │                │
         └──────────┬──────────┘                │
                    │                           │
         ┌──────────▼──────────┐      ┌─────────▼────────┐
         │  ML MODEL           │      │  .tflite MODEL   │
         │  (TensorFlow/       │      │  (in assets/)    │
         │   PyTorch)          │      │                  │
         │                     │      │  + labels.txt    │
         └──────────┬──────────┘      └─────────┬────────┘
                    │                           │
         ┌──────────▼──────────┐      ┌─────────▼────────┐
         │  PREDICTION         │      │  PREDICTION      │
         │  {disease, conf, …} │      │  {disease, conf} │
         └──────────┬──────────┘      └─────────┬────────┘
                    │                           │
                    └─────────────┬─────────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │   RESULT PROCESSING        │
                    │   - Display disease info   │
                    │   - Save to Room DB        │
                    │   - Lookup XML library     │
                    └────────────────────────────┘
```


### 1. Cropora Android App

```
┌──────────────────────────────────────────────────────────────────┐
│                        CROPORA ANDROID APP                       │
│                             (KOTLIN)                             │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  - Captures or selects a plant-leaf image                        │
│  - Prepares the image for analysis                               │
│  - Lets the user use Cloud Mode or Offline Mode                  │
│  - Receives and displays the final prediction                    │
│  - Stores previous scan records                                  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │     IMAGE INPUT / CAPTURE      │
                │ - Camera                       │
                │ - Gallery image selection      │
                │ - Image preview                │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │       MODE SELECTION           │
                │ - Cloud Mode                   │
                │ - Offline Mode                 │
                └────────────────────────────────┘


```
### 2. Cloud Mode

```

┌──────────────────────────────────────────────────────────────────┐
│                           CLOUD MODE                             │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Purpose: Send a leaf image to the remote server for prediction. │
│                                                                  │
│  Components:                                                     │
│  - Internet connection check                                     │
│  - Retrofit HTTP client                                          │
│  - Multipart image request                                       │
│  - FastAPI prediction response parser                            │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                 ┌────────────────────────────────┐
                 │       RETROFIT HTTP CLIENT     │
                 │                                │
                 │  POST /predict                 │
                 │  Upload image as multipart     │
                 └────────────────────────────────┘
                                  │
                                  ▼
                 ┌────────────────────────────────┐
                 │        FASTAPI BACKEND         │
                 │                                │
                 │  Receives image and predicts   │
                 └────────────────────────────────┘
                                  │
                                  ▼
                 ┌────────────────────────────────┐
                 │       CLOUD PREDICTION         │
                 │                                │
                 │  - Disease name                │
                 │  - Confidence score            │
                 │  - Optional recommendations    │
                 └────────────────────────────────┘


```

### 3. Retrofit HTTP Image Upload

```
┌──────────────────────────────────────────────────────────────────┐
│                      RETROFIT HTTP UPLOAD                        │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Input: Plant leaf image                                         │
│                                                                  │
│  Process:                                                        │
│  1. Convert selected image into File or RequestBody              │
│  2. Create MultipartBody.Part                                    │
│  3. Call the FastAPI /predict endpoint                           │
│  4. Wait for JSON response                                       │
│  5. Convert JSON into a prediction object                        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │     IMAGE FILE / BITMAP        │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │     MULTIPART HTTP REQUEST     │
                │     POST /predict              │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │     JSON RESPONSE PARSER       │
                │ { disease, confidence, ... }   │
                └────────────────────────────────┘

```

### 4. FastAPI Backend

```
┌──────────────────────────────────────────────────────────────────┐
│                         FASTAPI BACKEND                          │
│                       (PYTHON + UVICORN)                         │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Main endpoint:                                                  │
│  - POST /predict                                                 │
│                                                                  │
│  Responsibilities:                                               │
│  - Receive the uploaded plant image                              │
│  - Validate the image file                                       │
│  - Decode and preprocess the image                               │
│  - Send the prepared image to the machine-learning model         │
│  - Return the disease prediction as JSON                         │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │      IMAGE VALIDATION          │
                │ - File exists                  │
                │ - Valid image type             │
                │ - Correct image format         │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │      IMAGE PREPROCESSING       │
                │ - Resize                       │
                │ - Normalize pixels             │
                │ - Convert to tensor            │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │         ML INFERENCE           │
                └────────────────────────────────┘

```

### 5. Server-Side Machine Learning Model


```
┌──────────────────────────────────────────────────────────────────┐
│                       SERVER-SIDE ML MODEL                       │
│                    (TENSORFLOW OR PYTORCH)                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Input: Preprocessed leaf-image tensor                           │
│                                                                  │
│  Processing:                                                     │
│  - Extract visual features from the leaf                         │
│  - Detect spots, color changes, texture, and damage patterns     │
│  - Compare features against trained disease classes              │
│                                                                  │
│  Output:                                                         │
│  - Most likely disease                                           │
│  - Confidence probability                                        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │     CLASS PROBABILITY OUTPUT   │
                │                                │
                │  Healthy          : 0.03       │
                │  Early Blight     : 0.92       │
                │  Late Blight      : 0.04       │
                │  Leaf Mold        : 0.01       │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │       SELECT TOP CLASS         │
                │  Tomato Early Blight — 92%     │
                └────────────────────────────────┘

```

#### 6. API Prediction Response

```
┌──────────────────────────────────────────────────────────────────┐
│                    FASTAPI PREDICTION RESPONSE                   │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  The server returns a structured prediction result to Android.   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │          JSON RESPONSE         │
                │                                │
                │  {                             │
                │    "disease": "Early Blight",  │
                │    "confidence": 0.92,         │
                │    "plant": "Tomato"           │
                │  }                             │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │    ANDROID PREDICTION OBJECT   │
                │ - Disease name                 │
                │ - Confidence                   │
                │ - Plant type                   │
                │ - Detection source: Cloud      │
                └────────────────────────────────┘

```

#### 7. Offline Mode


```
┌──────────────────────────────────────────────────────────────────┐
│                          OFFLINE MODE                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Purpose: Detect plant disease without internet access.          │
│                                                                  │
│  Components:                                                     │
│  - TensorFlow Lite Interpreter                                   │
│  - .tflite model stored in Android assets                        │
│  - labels.txt class-name file                                    │
│  - Local image preprocessing                                     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │      SELECTED LEAF IMAGE       │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │      LOCAL PREPROCESSING       │
                │ - Resize image                 │
                │ - Normalize pixel values       │
                │ - Create input tensor          │
                └────────────────────────────────┘
                                  │
                                  ▼
                ┌────────────────────────────────┐
                │  TENSORFLOW LITE INFERENCE     │
                │  No network connection needed  │
                └────────────────────────────────┘

```

#### 8. TensorFlow Lite Model and Labels



```
┌──────────────────────────────────────────────────────────────────┐
│                    TENSORFLOW LITE MODEL ASSETS                  │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Android application assets:                                     │
│                                                                  │
│  - plant_disease_model.tflite                                    │
│  - labels.txt                                                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                     │                           │
                     ▼                           ▼
     ┌────────────────────────┐    ┌────────────────────────┐
     │ .tflite MODEL          │    │ labels.txt             │
     │                        │    │                        │
     │ Performs local ML      │    │ Maps output index to   │
     │ inference on the image │    │ a disease name         │
     └────────────────────────┘    └────────────────────────┘
                     │                           │
                     └─────────────┬─────────────┘
                                   ▼
                  ┌────────────────────────────────┐
                  │   OFFLINE PREDICTION RESULT    │
                  │                                │
                  │  Index: 4                      │
                  │  Label: Tomato Early Blight    │
                  │  Confidence: 92%               │
                  └────────────────────────────────┘

```


#### 9. Offline Prediction Processing



```


```

#### 10. Unified Prediction Result




```


```
#### 11. Result Processing

```


```

#### 12. XML Disease Information Library

```


```


```


```