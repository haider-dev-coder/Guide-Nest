# GuideNest Documentation

## 1. Project Overview

**GuideNest** is a hybrid Android application with a Python backend designed to assist users by detecting objects in real time using a mobile camera feed. The Android app captures camera frames, sends them to the backend for object detection, and provides spoken feedback in English or Roman Urdu.

**Target audience**
- Visually impaired users who need audio descriptions of nearby objects.
- Developers and maintainers working on object detection, accessibility, and mobile-to-backend computer vision systems.

**Core functionality**
- Capture live camera frames on Android via CameraX.
- Convert camera images to JPEG and send them to a backend service.
- Detect objects using a YOLO model in Python.
- Estimate object distance and calculate step-based guidance.
- Render bounding boxes and display object names and distances.
- Speak detected object details using Text-to-Speech.
- Allow runtime backend URL configuration and speech rate adjustment.

---

## 2. Tech Stack

### Android client
- **Android SDK** (AndroidX)
- **Java 17 compatibility**
- **AndroidX CameraX**
  - `camera-core:1.3.4`
  - `camera-camera2:1.3.4`
  - `camera-lifecycle:1.3.4`
  - `camera-view:1.3.4`
- **OkHttp** `4.12.0` for HTTP communication
- **Gson** `2.10.1` for JSON parsing
- **Android Material** `1.12.0`
- **AndroidX AppCompat** `1.7.0`
- **AndroidX Core KTX** `1.13.1`
- **Kotlin Standard Library** `1.9.24`

### Python backend
- **Python 3.11+** (recommended)
- **FastAPI** `0.115.0` for HTTP API
- **Uvicorn[standard]** `0.30.6` as ASGI server
- **ultralytics** `8.3.14` for YOLO model inference
- **OpenCV-Python-Headless** `4.10.0.84` for image handling support
- **NumPy** `1.26.4`
- **Pillow** `10.4.0` for image decoding and rotation
- **pytest** `8.3.3` for backend unit tests
- **httpx** `0.27.2` for testing and HTTP tool examples

---

## 3. Folder Structure Walkthrough

```
guide-nest/
├── README.md
├── APP_DOCUMENTATION.md
├── QUICK_REFERENCE.md
├── TECHNICAL_IMPROVEMENTS.md
├── app/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/guidenest/
│   │   │   ├── activities/MainActivity.java
│   │   │   ├── model/DetectedItem.java
│   │   │   ├── model/DetectionResponse.java
│   │   │   ├── network/DetectionBackendClient.java
│   │   │   └── ObjectDetectionOverlay.java
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       ├── values/strings.xml
│   │       ├── values/plurals.xml
│   │       └── values/dimens.xml
├── backend/
│   ├── app.py
│   ├── README.md
│   ├── requirements.txt
│   ├── test_app.py
│   └── .env.example
├── build.gradle
└── settings.gradle
```

### Root files
- `README.md`: High-level project summary and quick run instructions.
- `APP_DOCUMENTATION.md`: Existing documentation draft covering app behavior.
- `QUICK_REFERENCE.md`: Short notes on feature changes and improvements.
- `TECHNICAL_IMPROVEMENTS.md`: Technical observations and suggested improvements.
- `build.gradle`: Top-level Gradle configuration for the project.
- `settings.gradle`: Includes modules for Gradle builds.

### `app/`
- Android client module.
- `app/build.gradle`: Android module build config, dependencies, build types, and backend default URL settings.
- `app/src/main/AndroidManifest.xml`: Declares camera permissions and app startup activity.
- `MainActivity.java`: Main Android activity and core business logic.
- `ObjectDetectionOverlay.java`: Custom view used to draw bounding boxes around detected objects.
- `model/DetectionResponse.java`: Data model for backend detection API responses.
- `model/DetectedItem.java`: Data model for an individual detection result.
- `network/DetectionBackendClient.java`: HTTP client for submitting camera frames to the backend.
- `res/layout/activity_main.xml`: UI layout with preview, buttons, and status text.
- `res/values/strings.xml`: Translatable text and app labels.
- `res/values/plurals.xml`: Plural strings used for distance and speech output.
- `res/values/dimens.xml`: Layout padding and UI spacing values.

### `backend/`
- `app.py`: FastAPI backend implementation that loads a YOLO model and serves object detection.
- `requirements.txt`: Python dependencies for backend runtime and testing.
- `README.md`: Backend-specific runtime and Docker instructions.
- `test_app.py`: Backend unit tests.
- `.env.example`: Environment variables for backend configuration.

---

## 4. Architecture

### Overview
The current architecture is a **mobile frontend + cloud/backend service** pattern.

1. **Android app** captures camera frames and sends them to the backend.
2. **Backend API** performs object detection using a YOLO model.
3. The backend returns detection results.
4. The Android app selects the primary detection, renders overlay graphics, and speaks the result.

### Data flow

1. User opens the app and grants camera permission.
2. CameraX preview begins in `MainActivity`.
3. The user taps **Start Detection**.
4. The app starts `ImageAnalysis` and processes frames at a controlled rate.
5. Each selected frame is converted to JPEG in `imageProxyToJpegBytes()`.
6. `DetectionBackendClient.detectFrame()` sends a JSON payload containing:
   - `imageBase64`
   - `rotationDegrees`
7. The backend decodes the image and rotates it using Pillow.
8. YOLO inference runs on the image.
9. The backend computes distance from bounding box area.
10. Results are returned to the app.
11. The app chooses the closest detection and updates the UI.
12. Text-to-Speech announces the object name and estimated distance.

### Design patterns
- **Separation of concerns**: UI and camera management live in Android, while detection logic is isolated in Python.
- **Data transfer object (DTO)**: `DetectionResponse` and `DetectedItem` represent backend payloads.
- **Rate limiting**: App enforces a minimum submission interval (`FRAME_SUBMISSION_INTERVAL_MS`) to avoid backend overload.
- **Single responsibility**: `DetectionBackendClient` handles HTTP communication only.

### Component responsibilities
- `MainActivity`: orchestrates camera, backend requests, UI updates, and TTS.
- `DetectionBackendClient`: sends JPEG frames and receives JSON responses.
- `ObjectDetectionOverlay`: renders bounding boxes.
- `backend/app.py`: provides `/health` and `/detect` endpoints.

---

## 5. Setup and Installation

### Prerequisites
- **Android Studio** with Android SDK installed
- **JDK 17** or newer
- **Python 3.11+** for the backend
- **Git** for repository cloning
- **Docker** (optional) for backend container deployment

### Clone the repository
```bash
cd "c:/Users/Administrator/Desktop/object detection test"
git clone <repository-url> guide-nest
cd guide-nest
```

### Backend setup
```bash
cd backend
python -m pip install -r requirements.txt
```

**Run locally**
```bash
uvicorn app:app --host 0.0.0.0 --port 8000
```

**Run with Docker**
```bash
docker build -t guidenest-backend .
docker run --rm -p 8000:8000 --env-file .env.example guidenest-backend
```

### Android app setup
1. Open `guide-nest/app` in Android Studio.
2. Allow Android Studio to sync Gradle.
3. Make sure the emulator or physical device is connected.
4. If using the emulator, default backend URL is `http://10.0.2.2:8000`.
5. If using a real device, set the backend URL in the app settings to your machine IP.

### Running the app
1. Start the backend service.
2. Install and launch the app from Android Studio.
3. Grant camera permission when prompted.
4. Tap **Start Detection**.
5. Point the camera at objects.
6. Watch the overlay and listen to spoken feedback.

---

## 6. Environment Variables

### Backend environment variables
Defined in `backend/.env.example`:

- `GUIDENEST_MODEL`
  - Purpose: Path to the YOLO model file.
  - Default: `yolov8n.pt`

- `GUIDENEST_CONFIDENCE`
  - Purpose: Minimum confidence threshold for detection.
  - Default: `0.35`

### Android environment configuration
The Android client uses a build-time default backend URL defined in `app/build.gradle`:

- `DEFAULT_BACKEND_URL`
  - Debug default: `http://10.0.2.2:8000`
  - Release default: `https://your-production-backend.example`

The active backend URL is stored in shared preferences and can be changed in the app settings during runtime.

---

## 7. API Specifications

### GET /health
**Description**: Check backend readiness.

**Response**:
```json
{
  "status": "ok",
  "modelLoaded": true,
  "modelPath": "yolov8n.pt",
  "confidenceThreshold": 0.35
}
```

### POST /detect
**Description**: Send a camera frame for object detection.

**Request body**:
```json
{
  "imageBase64": "<base64-encoded JPEG bytes>",
  "rotationDegrees": 90
}
```

**Response body**:
```json
{
  "modelVersion": "yolov8n.pt",
  "detections": [
    {
      "label": "person",
      "confidence": 0.89,
      "distanceMeters": 1.2,
      "boundingBox": [10.0, 20.0, 150.0, 220.0]
    }
  ]
}
```

**Detection item fields**:
- `label`: Detected object class label.
- `confidence`: Model confidence score.
- `distanceMeters`: Estimated distance based on bounding box area.
- `boundingBox`: `[x1, y1, x2, y2]` coordinates.

### Error handling
- `400 Bad Request`: Invalid payload or image decoding failure.
- `500 Internal Server Error`: Model load failure or internal backend error.

---

## 8. Authentication

There is currently no authentication or user session management in GuideNest.
The app and backend communicate over unsecured HTTP in debug mode and expect the backend URL to be configured manually.

If authentication is added in the future, the best pattern is:
- Backend issues JWT tokens after login
- Client stores tokens securely in Android SharedPreferences or EncryptedSharedPreferences
- Client adds `Authorization: Bearer <token>` header to backend requests

---

## 9. Database Schema

GuideNest currently has no database.

Potential future storage options:
- User preferences and backend URL persisted locally in Android shared preferences
- User profiles and usage logs in a backend database
- Object detection history in a lightweight SQLite or server-side database

---

## 10. Testing

### Backend tests
Run backend tests from the `backend` folder:
```bash
cd backend
pytest
```

The backend includes tests for:
- `estimate_distance_meters()` behavior
- image decode and rotation handling
- model availability error handling

### Android tests
The current project does not include Android unit or instrumented tests.

### Manual testing checklist
- Verify camera permission flow
- Confirm backend URL is configurable and stored persistently
- Start detection and ensure `POST /detect` receives requests
- Confirm object names, distances, and bounding boxes render correctly
- Verify TTS announcements and language switching

---

## 11. Build and Production

### Android production build
From the project root:
```bash
cd app
./gradlew assembleRelease
```

**Release preparation**:
- Update `app/build.gradle` release backend URL from `https://your-production-backend.example` to your real HTTPS endpoint.
- Set `manifestPlaceholders` for `usesCleartextTraffic` to `false` in release build.
- Test on a physical device.
- Use Android Studio to generate a signed APK or AAB if required.

### Backend production build
Deploy the backend using Docker or a Python host.

**Docker**:
```bash
docker build -t guidenest-backend .
docker run --rm -p 8000:8000 --env-file .env.example guidenest-backend
```

**Production recommendations**:
- Host behind HTTPS
- Restrict CORS to trusted origins
- Use a GPU instance for YOLO inference when performance matters
- Configure process supervision with `systemd`, Docker Compose, or Kubernetes

---

## 12. APK Generation

### Debug APK
From the Android module:
```bash
cd app
./gradlew assembleDebug
```
The debug APK will be located in `app/build/outputs/apk/debug/`.

### Release APK
```bash
cd app
./gradlew assembleRelease
```
The release APK will be located in `app/build/outputs/apk/release/`.

### Android Studio method
1. Open `app` in Android Studio.
2. Build > Generate Signed Bundle / APK.
3. Choose release key and build type.
4. Use the generated signed APK for distribution.

---

## 13. Deployment

### Backend deployment
- Use Docker for consistency.
- Deploy on AWS EC2, DigitalOcean, GCP, Azure, or another hosting provider.
- Ensure the backend is accessible via a stable HTTPS URL.
- Configure CORS to allow only the Android app or admin domains.

### Android deployment
- Publish the final APK or AAB to Google Play Store.
- For testing, use internal app sharing or local installation via `adb install`.
- Verify runtime permissions and backend connectivity on devices.

---

## 14. Performance and Scalability

### Android app optimizations
- The app throttles frame submissions every `700ms`.
- Only one request is allowed in flight at a time.
- This protects the backend and reduces local CPU usage.
- The app uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to avoid stale frame queues.

### Backend scalability recommendations
- Deploy multiple backend instances behind a load balancer.
- Use GPU-enabled instances for faster YOLO inference.
- Cache repeated detection results if the camera scene is stable.
- Use asynchronous request handling and HTTP keep-alive.

---

## 15. Common Errors and Troubleshooting

### 1. Camera permission denied
**Symptom**: App cannot start detection.
**Fix**: Grant camera permission and restart the app.

### 2. Backend unreachable
**Symptom**: The app shows a backend error or no detections.
**Fix**:
- Ensure the backend server is running.
- Verify the backend URL in app settings.
- For emulator, use `http://10.0.2.2:8000`.
- For a physical device, use the host machine IP on the same network.

### 3. Backend model load failure
**Symptom**: `/health` returns `modelLoaded: false` or `500`.
**Fix**:
- Confirm `ultralytics` and `Pillow` are installed.
- Check model file path via `GUIDENEST_MODEL`.
- Use a valid YOLO `.pt` model.

### 4. TTS not speaking
**Symptom**: UI updates but no audio.
**Fix**:
- Confirm device TTS engine availability.
- Ensure the volume is not muted.
- Restart the app if TTS initialization fails.

### 5. Build fails due to missing Gradle/SDK
**Fix**:
- Install Android Studio and the Android SDK.
- Ensure `JAVA_HOME` points to JDK 17.
- Sync Gradle dependencies from Android Studio.

---

## 16. Future Improvements

### Short-term enhancements
- Add a local on-device fallback when the backend is unavailable.
- Add UI buttons for pausing and resetting detection.
- Improve object naming and Roman Urdu translation coverage.
- Add richer accessibility labels and vibration cues.

### Medium-term enhancements
- Add user profiles and history tracking.
- Add lightweight offline model support using ML Kit or TensorFlow Lite.
- Add more languages and voice options.
- Add support for object categories and grouping.

### Long-term roadmap
- Add authentication and secure user sessions.
- Add a database for usage analytics.
- Add remote configuration and model version management.
- Build a web dashboard for backend monitoring.

---

## 17. Contribution Guide

### How to contribute
1. Fork the repository.
2. Create a feature branch: `feature/<name>`.
3. Make your changes and test locally.
4. Open a pull request with a clear description.
5. Link any related issue or bug report.

### Contribution standards
- Keep code changes focused and small.
- Document new features in this documentation.
- Prefer clear variable names and comments.
- Validate backend changes with `pytest`.
- Validate Android changes with `./gradlew assembleDebug`.

### Development workflow
- Use `git pull --rebase` to stay current.
- Test backend and mobile components together when modifying detection flows.
- Update documentation for any new environment variables or API changes.

---

## 18. Notes

- The current implementation has no authentication or database schema.
- The backend is the primary detection engine and is swappable through the HTTP API.
- The Android app is designed to remain lightweight and focus on camera capture, UI, and speech.
