# GuideNest End-to-End Project Documentation

## 1. Project Overview

### Project Name
GuideNest

### Problem Statement
Visually impaired and accessibility-focused users often need fast, hands-free awareness of nearby objects. Traditional mobile camera apps provide visual output only, and many object-detection solutions are either too generic, too slow, or difficult to adapt for mobile accessibility workflows.

### Solution Explanation
GuideNest is an Android accessibility application that captures camera frames, sends them to a Python backend running a YOLO object-detection model, and announces the nearest detected object with an estimated distance using text-to-speech. The Android app focuses on camera interaction, UI, and speech, while the backend handles inference and detection ranking.

### Target Users
- Visually impaired users
- Accessibility researchers and student developers
- Mobile AI prototyping teams
- Assistive technology demonstrators

### Key Features
- Real-time camera preview with backend-assisted object detection
- Spoken feedback for the closest detected object
- Distance estimation in meters and steps
- Roman Urdu and English output modes
- Adjustable backend URL and speech rate
- Health-check workflow before starting detection
- Overlay bounding boxes on the live preview

## 2. System Architecture

### High-Level Architecture Explanation
GuideNest uses a split architecture:

- The Android client captures frames with CameraX, manages the user experience, and speaks results.
- The FastAPI backend receives JPEG frames over HTTP, runs YOLO inference, estimates distance, and returns normalized detection results.
- The model file is loaded on backend startup and warmed up for faster first inference.
- There is currently no persistent database in the live system. Settings are stored locally on-device using `SharedPreferences`.

### Tech Stack

#### Frontend
- Android Native
- Java 17
- AndroidX
- CameraX
- Material Components
- Android TextToSpeech
- OkHttp
- Gson

#### Backend
- Python 3.11
- FastAPI
- Uvicorn
- Ultralytics YOLOv8
- NumPy
- Pillow
- OpenCV Headless

#### Database
- Current state: no server-side database
- Current local persistence: Android `SharedPreferences`
- Recommended future production database: PostgreSQL

#### Tools and DevOps
- Gradle
- Android Studio
- Pytest
- Docker
- Git

### Architecture Diagram
```text
+----------------------+
|      End User        |
|  Camera + Audio UX   |
+----------+-----------+
           |
           v
+----------------------+
|   Android App        |
|  - CameraX           |
|  - UI                |
|  - TTS               |
|  - OkHttp Client     |
|  - SharedPreferences |
+----------+-----------+
           |
           | HTTP JSON
           v
+----------------------+
|   FastAPI Backend    |
|  - /health           |
|  - /detect           |
|  - Validation        |
|  - Distance Logic    |
+----------+-----------+
           |
           v
+----------------------+
|   YOLOv8 Model       |
|  yolov8n.pt          |
+----------------------+
```

## 3. Complete Workflow

### End-to-End Delivery Workflow

#### Phase 1: Idea and Discovery
- Identify the accessibility problem: recognizing nearby objects through speech.
- Define the primary output: object name plus distance guidance.
- Decide to separate mobile UX from model inference for flexibility and maintainability.

#### Phase 2: Planning
- Define MVP scope:
  - live camera preview
  - backend health check
  - object detection
  - spoken feedback
  - language toggle
- Choose architecture:
  - Android app for interaction
  - Python backend for model serving
- Define non-functional goals:
  - responsive inference
  - graceful error handling
  - easy model replacement

#### Phase 3: Development
- Build Android UI and camera preview flow.
- Add CameraX frame analysis.
- Convert frames to JPEG and send to backend.
- Build FastAPI endpoints.
- Integrate YOLO model loading and warmup.
- Add distance estimation logic.
- Return sorted detections to the app.
- Announce the nearest object through TTS.

#### Phase 4: Testing
- Run backend unit tests with `pytest`.
- Validate health-check responses.
- Test invalid payloads and malformed detection data.
- Perform manual camera tests on emulator and physical device.
- Validate speech, language toggle, and settings persistence.

#### Phase 5: Deployment
- Package Android app with Gradle.
- Host backend using Docker, VM, or cloud container service.
- Replace debug backend URL with production HTTPS URL.
- Verify device-to-backend connectivity.

### Runtime User Workflow
1. User launches GuideNest.
2. App requests camera permission if needed.
3. App checks backend health.
4. User taps `Start Detection`.
5. CameraX captures frames.
6. App converts frames to JPEG and sends them to `/detect`.
7. Backend decodes image, rotates if needed, and runs YOLO inference.
8. Backend estimates distance and sorts detections by nearest object.
9. App selects the primary detection and updates the overlay.
10. App speaks the object name and estimated distance.
11. User can stop detection, switch language, or change backend URL and speech rate.

### Developer Workflow
1. Pull latest source.
2. Start backend locally.
3. Verify `/health`.
4. Launch Android app in emulator or device.
5. Point app to local or LAN backend.
6. Test detection flow.
7. Run backend tests before release.
8. Build release APK only after production backend URL and network settings are ready.

## 4. Folder Structure

### Project Directory Tree
```text
guide-nest/
|-- README.md
|-- APP_DOCUMENTATION.md
|-- QUICK_REFERENCE.md
|-- TECHNICAL_IMPROVEMENTS.md
|-- GuideNest-Full-Documentation.md
|-- PROJECT_PDF_DOCUMENTATION.md
|-- build.gradle
|-- settings.gradle
|-- gradle.properties
|-- gradlew.bat
|-- app/
|   |-- build.gradle
|   `-- src/
|       `-- main/
|           |-- AndroidManifest.xml
|           |-- java/com/example/guidenest/
|           |   |-- ObjectDetectionOverlay.java
|           |   |-- activities/
|           |   |   `-- MainActivity.java
|           |   |-- model/
|           |   |   |-- BackendHealth.java
|           |   |   |-- DetectedItem.java
|           |   |   `-- DetectionResponse.java
|           |   `-- network/
|           |       `-- DetectionBackendClient.java
|           `-- res/
|               |-- layout/activity_main.xml
|               |-- values/colors.xml
|               |-- values/dimens.xml
|               |-- values/strings.xml
|               |-- values/styles.xml
|               `-- xml/network_security_config.xml
`-- backend/
    |-- app.py
    |-- requirements.txt
    |-- Dockerfile
    |-- test_app.py
    |-- validate_fixes.py
    |-- README.md
    |-- .env.example
    `-- yolov8n.pt
```

### Folder and File Explanation

#### Root Files
- `README.md`: quick setup and project summary
- `build.gradle`, `settings.gradle`, `gradle.properties`: Android project configuration
- `GuideNest-Full-Documentation.md`: earlier project notes
- `PROJECT_PDF_DOCUMENTATION.md`: clean export-ready documentation

#### Android App
- `MainActivity.java`: camera lifecycle, detection flow, TTS, settings, UI updates
- `DetectionBackendClient.java`: HTTP communication with backend
- `ObjectDetectionOverlay.java`: renders bounding boxes
- `model/`: response DTOs for health and detection payloads
- `activity_main.xml`: main screen layout
- `strings.xml`: user-facing copy and speech templates
- `AndroidManifest.xml`: app permissions and activity registration

#### Backend
- `app.py`: FastAPI server, validation, inference, distance estimation
- `requirements.txt`: Python dependencies
- `Dockerfile`: backend container packaging
- `test_app.py`: backend unit tests
- `validate_fixes.py`: validation helper script
- `yolov8n.pt`: YOLO model weights

## 5. Database Design

### Current State
The current implementation has no server-side database. This is accurate and intentional for the current MVP. The only persistent storage in active use is Android `SharedPreferences` for:

- backend base URL
- speech rate

### Recommended Production Database Design
If GuideNest evolves into a production platform with user accounts, analytics, device management, and model version tracking, a relational database should be introduced.

### Recommended Tables

#### `users`
- `id` UUID PK
- `email` VARCHAR UNIQUE
- `password_hash` VARCHAR
- `full_name` VARCHAR
- `preferred_language` VARCHAR
- `created_at` TIMESTAMP
- `updated_at` TIMESTAMP

#### `devices`
- `id` UUID PK
- `user_id` UUID FK -> users.id
- `device_name` VARCHAR
- `platform` VARCHAR
- `app_version` VARCHAR
- `last_seen_at` TIMESTAMP

#### `detection_sessions`
- `id` UUID PK
- `user_id` UUID FK -> users.id
- `device_id` UUID FK -> devices.id
- `started_at` TIMESTAMP
- `ended_at` TIMESTAMP
- `backend_version` VARCHAR
- `model_version` VARCHAR

#### `detections`
- `id` UUID PK
- `session_id` UUID FK -> detection_sessions.id
- `label` VARCHAR
- `confidence` DECIMAL
- `distance_meters` DECIMAL
- `bounding_box_json` JSONB
- `captured_at` TIMESTAMP

#### `model_registry`
- `id` UUID PK
- `model_name` VARCHAR
- `model_version` VARCHAR
- `storage_path` VARCHAR
- `is_active` BOOLEAN
- `created_at` TIMESTAMP

#### `audit_logs`
- `id` UUID PK
- `user_id` UUID NULL FK -> users.id
- `event_type` VARCHAR
- `event_payload` JSONB
- `created_at` TIMESTAMP

### Relationship Summary
- One user can have many devices.
- One user can have many detection sessions.
- One device can produce many sessions.
- One session can contain many detections.
- Model registry tracks deployed model versions independently.

### ER Diagram
```text
users 1 ------ * devices
users 1 ------ * detection_sessions
devices 1 ---- * detection_sessions
detection_sessions 1 ---- * detections
users 1 ------ * audit_logs

model_registry
  referenced by detection_sessions.model_version
```

## 6. API Design

### API Overview
Base URL examples:

- Local emulator: `http://10.0.2.2:8000`
- Local network phone test: `http://<LAN-IP>:8000`
- Production: `https://your-production-backend.example`

### Endpoints

#### `GET /health`
Checks backend availability and model readiness.

Response example:
```json
{
  "status": "ok",
  "modelLoaded": true,
  "modelPath": "yolov8n.pt",
  "confidenceThreshold": 0.2,
  "imageSize": 960
}
```

#### `POST /detect`
Accepts a base64 JPEG frame and optional rotation.

Request example:
```json
{
  "imageBase64": "/9j/4AAQSkZJRgABAQAAAQABAAD...",
  "rotationDegrees": 90
}
```

Response example:
```json
{
  "modelVersion": "yolov8n.pt",
  "detections": [
    {
      "label": "chair",
      "confidence": 0.93,
      "distanceMeters": 1.4,
      "boundingBox": [112.4, 84.2, 508.7, 699.1]
    },
    {
      "label": "table",
      "confidence": 0.81,
      "distanceMeters": 2.6,
      "boundingBox": [530.1, 170.0, 1101.3, 690.5]
    }
  ]
}
```

### Error Response Examples

#### Invalid image payload
```json
{
  "detail": "Invalid image payload: Image base64 string is empty"
}
```

#### Backend model not available
```json
{
  "detail": "ultralytics is not installed or model failed to load. Install backend requirements first."
}
```

## 7. Frontend Structure

### Pages and Screens
The current app is a single-screen Android experience:

- Main camera screen
- About interaction
- Settings dialog

### Core UI Components
- `PreviewView`: camera feed
- `ObjectDetectionOverlay`: bounding boxes
- `TextView`: backend status
- `TextView`: detected object label
- `TextView`: distance text
- `Button`: start detection
- `Button`: stop detection
- `Button`: about
- `Button`: settings
- `Button`: language toggle

### State Management Approach
State is managed directly inside `MainActivity` using activity-level variables:

- detection status flags
- speech rate
- selected language mode
- backend URL
- last spoken message
- request throttling and timing controls

Persistent settings are stored in `SharedPreferences`.

### UI Flow
```text
App Launch
  -> Permission Check
  -> Backend Health Check
  -> Start Detection
  -> Frame Capture
  -> Backend Response
  -> Update Overlay + Text
  -> Speak Result
  -> Stop or Continue
```

## 8. Backend Logic

### Core Modules

#### API Layer
- FastAPI app initialization
- CORS middleware
- request and response models

#### Inference Layer
- YOLO model loading on startup
- model warmup using a dummy image
- inference with configurable confidence and image size

#### Validation Layer
- base64 validation
- image decode validation
- rotation normalization
- box coordinate sanitation
- result structure checks

#### Business Logic
- estimate distance from object dimensions, FOV, and box size
- normalize detections
- sort detections by nearest object and highest confidence

### Authentication Flow
There is no authentication in the current MVP.

Recommended future flow:
1. User signs in with email/password or OAuth.
2. Backend issues JWT access token and refresh token.
3. Android client stores token securely using encrypted local storage.
4. Protected endpoints require `Authorization: Bearer <token>`.
5. Role-based access controls are applied for admin and analytics endpoints.

### Business Logic Summary
- `decode_image()`: base64 decode and image rotation
- `estimate_distance_meters()`: blend known object dimensions with ratio-based fallback
- `startup_event()`: load and warm model
- `health()`: expose backend readiness
- `detect()`: run end-to-end detection and return sorted results

## 9. Complete Development Steps

### Environment Setup

#### Prerequisites
- Android Studio
- JDK 17
- Python 3.11
- Git
- Android device or emulator

### Backend Setup
```bash
cd guide-nest/backend
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

### Backend Test Command
```bash
cd guide-nest/backend
pytest
```

### Android Setup
```bash
cd guide-nest
gradlew.bat assembleDebug
```

### Full Local Development Flow
1. Start the backend server.
2. Open the Android project in Android Studio.
3. Run the app on emulator or phone.
4. If using emulator, keep backend URL as `http://10.0.2.2:8000`.
5. If using a phone, update backend URL in Settings to your machine's LAN IP.
6. Grant camera permission.
7. Tap `Start Detection`.
8. Validate object announcement, distance estimation, and overlay drawing.

### Suggested Implementation Plan for a Fresh Build
1. Create Android shell app and camera screen.
2. Add CameraX preview and frame analysis.
3. Build FastAPI backend with `/health` and `/detect`.
4. Integrate YOLO model inference.
5. Add JSON DTOs and HTTP client.
6. Add distance estimation logic.
7. Add TTS and language switching.
8. Add settings persistence and backend URL override.
9. Add automated tests and release pipeline.

## 10. Testing Strategy

### Unit Testing

#### Backend
- validate distance estimation ordering
- validate image rotation handling
- validate model-missing behavior
- validate malformed payload handling

Current file:
- `backend/test_app.py`

#### Android
Recommended unit tests:
- detection response parsing
- backend URL sanitization
- primary detection selection logic
- speech message formatting

### Integration Testing
- Android app to local FastAPI server
- Android app to Dockerized backend
- backend with real YOLO weights
- health check before detection start

### Manual Testing Steps
1. Launch backend and confirm `/health` returns `modelLoaded: true`.
2. Launch Android app.
3. Grant permissions.
4. Start detection.
5. Test with single close object.
6. Test with multiple objects and verify nearest one is announced.
7. Test English and Roman Urdu speech.
8. Test network failure by stopping backend.
9. Test invalid backend URL in Settings.
10. Test stop/start detection repeatedly.

## 11. Deployment Guide

### Hosting Options

#### Android App
- Internal testing through Android Studio
- APK distribution
- Google Play internal testing

#### Backend
- AWS EC2
- AWS ECS or EKS
- Azure VM or App Service
- Google Cloud Run
- DigitalOcean Droplet
- Railway or Render for smaller prototypes

### Docker Deployment
```bash
cd guide-nest/backend
docker build -t guidenest-backend .
docker run -p 8000:8000 guidenest-backend
```

### Production Deployment Steps
1. Containerize backend.
2. Store model file on server or mounted volume.
3. Set environment variables for model path and thresholds.
4. Expose backend behind HTTPS reverse proxy.
5. Restrict CORS to trusted clients.
6. Update Android release backend URL in `app/build.gradle`.
7. Build signed Android release.
8. Run smoke tests on production infrastructure.

### Platform Notes
- `Vercel` and `Netlify` are not ideal for YOLO inference workloads because of serverless execution limits and model size constraints.
- `AWS`, `GCP`, `Azure`, or a dedicated container host are better suited for the backend.

## 12. Security Best Practices

### Authentication
- Current MVP has no authentication.
- Add JWT-based auth before introducing user accounts or saved history.

### Data Protection
- Use HTTPS in production.
- Avoid logging raw image payloads.
- Encrypt sensitive settings if user accounts are added.
- Store API secrets and credentials in environment variables.

### Common Vulnerabilities to Address
- Overly permissive CORS
- Unrestricted public inference endpoint abuse
- Denial of service from large payloads
- Insecure backend URL configuration
- Missing rate limiting
- Missing audit logging

### Security Recommendations
- Restrict `allow_origins`
- add request size limits
- add auth and rate limiting
- validate MIME assumptions and image dimensions
- use Android encrypted storage for secrets in future versions
- run backend behind a reverse proxy such as Nginx

## 13. Scalability Plan

### How to Scale This Project

#### Backend Scaling
- Move from single-instance FastAPI to container orchestration
- Use a queue for heavy inference if concurrency grows
- deploy GPU-backed inference where needed
- separate health, inference, and analytics services

#### Mobile Scaling
- Introduce repository and view-model layers as the app grows
- move from activity-managed state to a more structured architecture
- add offline and cached guidance modes

#### Data and Observability Scaling
- Introduce PostgreSQL for metadata
- use object storage for optional image snapshots
- add metrics, tracing, and centralized logs

### Future Improvements
- on-device fallback model for offline mode
- user accounts and personalized accessibility settings
- obstacle prioritization rules
- navigation guidance instead of object-only guidance
- multilingual speech packs
- model registry and A/B testing
- analytics dashboard for research environments

## 14. Final Summary
GuideNest is a practical assistive-technology system built with a clean separation between Android user experience and Python-based AI inference. Its current architecture is strong for an MVP because it keeps the mobile app lightweight while allowing model improvements on the backend. The project is already positioned for production hardening through better security, HTTPS deployment, structured persistence, and scalable inference hosting.

---

# PDF EXPORT VERSION

## GuideNest End-to-End Project Documentation

### 1. Project Overview
GuideNest is an Android accessibility application that helps users identify nearby objects using their device camera. The mobile app captures frames, sends them to a FastAPI backend running YOLOv8, and announces the nearest detected object with estimated distance in meters and steps. It is designed for visually impaired users, accessibility research, and assistive AI prototyping.

Key features:
- real-time camera preview
- backend health check before detection
- object detection with bounding box overlay
- spoken guidance in English and Roman Urdu
- adjustable backend URL and speech rate

### 2. System Architecture
GuideNest uses a client-server architecture. The Android app manages camera capture, UI, local settings, and text-to-speech. The Python backend handles image decoding, validation, YOLO inference, distance estimation, and detection ranking.

Architecture diagram:
```text
User
 -> Android App
 -> HTTP API
 -> FastAPI Backend
 -> YOLOv8 Model
 -> Detection Response
 -> Android Speech Output
```

Tech stack:
- Frontend: Android Native, Java 17, CameraX, Material Components, OkHttp, Gson
- Backend: Python 3.11, FastAPI, Uvicorn, Ultralytics YOLOv8, NumPy, Pillow
- Storage: SharedPreferences on device, no server-side database in current MVP
- DevOps: Gradle, Docker, Pytest, Git

### 3. Complete Workflow
Project workflow:
1. Define the accessibility problem and MVP scope.
2. Plan a split architecture between Android client and inference backend.
3. Implement camera capture, backend API, model inference, and text-to-speech.
4. Test backend validation, app-backend communication, and real device behavior.
5. Deploy backend to a container-friendly host and publish the Android app.

Runtime workflow:
1. User opens the app.
2. App checks camera permission and backend health.
3. User starts detection.
4. Camera frames are captured and converted to JPEG.
5. Frames are sent to `/detect`.
6. Backend runs YOLO inference and estimates distance.
7. App displays and speaks the nearest object.

### 4. Folder Structure
```text
guide-nest/
|-- app/
|   `-- src/main/
|       |-- AndroidManifest.xml
|       |-- java/com/example/guidenest/
|       |   |-- activities/MainActivity.java
|       |   |-- network/DetectionBackendClient.java
|       |   |-- model/
|       |   `-- ObjectDetectionOverlay.java
|       `-- res/
|           |-- layout/activity_main.xml
|           |-- values/strings.xml
|           `-- xml/network_security_config.xml
`-- backend/
    |-- app.py
    |-- requirements.txt
    |-- Dockerfile
    |-- test_app.py
    `-- yolov8n.pt
```

### 5. Database Design
Current system status:
- no server-side database
- local persistence uses SharedPreferences for backend URL and speech rate

Recommended future database:
- `users`
- `devices`
- `detection_sessions`
- `detections`
- `model_registry`
- `audit_logs`

ER summary:
```text
users 1..* devices
users 1..* detection_sessions
devices 1..* detection_sessions
detection_sessions 1..* detections
users 1..* audit_logs
```

### 6. API Design
Endpoints:
- `GET /health`
- `POST /detect`

`GET /health` response:
```json
{
  "status": "ok",
  "modelLoaded": true,
  "modelPath": "yolov8n.pt",
  "confidenceThreshold": 0.2,
  "imageSize": 960
}
```

`POST /detect` request:
```json
{
  "imageBase64": "<base64-jpeg>",
  "rotationDegrees": 90
}
```

`POST /detect` response:
```json
{
  "modelVersion": "yolov8n.pt",
  "detections": [
    {
      "label": "chair",
      "confidence": 0.93,
      "distanceMeters": 1.4,
      "boundingBox": [112.4, 84.2, 508.7, 699.1]
    }
  ]
}
```

### 7. Frontend Structure
Main frontend components:
- camera preview
- object overlay
- backend status text
- detected object text
- distance text
- start and stop controls
- about, settings, and language controls

State management:
- activity-level state in `MainActivity`
- persistent settings via SharedPreferences

### 8. Backend Logic
Core backend responsibilities:
- load and warm YOLO model at startup
- decode base64 images
- normalize rotation
- validate image and detection output
- estimate distance
- sort detections by nearest object
- return structured JSON responses

Current authentication:
- none

Recommended future authentication:
- JWT access and refresh tokens for protected endpoints

### 9. Complete Development Steps
Backend setup:
```bash
cd guide-nest/backend
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

Backend tests:
```bash
cd guide-nest/backend
pytest
```

Android build:
```bash
cd guide-nest
gradlew.bat assembleDebug
```

Implementation order:
1. Build Android shell
2. Add CameraX
3. Build FastAPI API
4. Integrate YOLO
5. Connect Android app to backend
6. Add distance estimation and speech
7. Add settings and tests

### 10. Testing Strategy
- Unit testing for backend validation and distance logic
- Integration testing between Android app and FastAPI backend
- Manual device testing for camera, speech, network failure, and multi-object scenarios

### 11. Deployment Guide
Recommended hosting:
- Android app: APK distribution or Play Store testing tracks
- Backend: AWS, Azure, GCP, DigitalOcean, Railway, or Render

Docker deployment:
```bash
cd guide-nest/backend
docker build -t guidenest-backend .
docker run -p 8000:8000 guidenest-backend
```

Important note:
- Vercel and Netlify are not recommended for YOLO inference hosting

### 12. Security Best Practices
- use HTTPS
- restrict CORS
- avoid logging raw images
- add rate limiting
- validate payload size
- introduce JWT authentication before multi-user rollout

### 13. Scalability Plan
- move to container orchestration for backend scaling
- add GPU-backed inference if throughput grows
- introduce PostgreSQL for sessions and analytics
- add observability and centralized logs
- support offline or hybrid on-device inference in future versions

### 14. Final Summary
GuideNest is a well-structured accessibility AI project that combines an Android client with a Python-based detection backend. Its current MVP is lightweight, practical, and extensible. The next production steps are security hardening, scalable deployment, structured persistence, and improved model operations.
