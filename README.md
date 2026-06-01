Guide Nest
Guide Nest is an Android accessibility app that uses the device camera to detect nearby objects, estimate distance, and speak useful guidance to the user. The Android client handles camera capture, UI, and speech, while a Python FastAPI backend performs object detection with YOLO.

Features
Real-time camera preview on Android
Object detection through a Python backend
Spoken feedback for detected objects
Estimated object distance in meters
Configurable backend URL for emulator, local network, or production
Separation between mobile app and ML backend for easier upgrades
Tech Stack
Android app in Java
CameraX for camera integration
OkHttp for backend communication
Gson for JSON parsing
Python FastAPI backend
Ultralytics YOLO for object detection
Project Structure
Guide-Nest/
|-- app/ Android application
|-- backend/ FastAPI object-detection backend
|-- gradle/ Gradle wrapper files
|-- build.gradle Root Gradle configuration
|-- settings.gradle Gradle settings
|-- README.md Project overview

Requirements
Android:

Android Studio
Android SDK 34
Min SDK 24
Java 17
Backend:

Python 3.10+
pip
How It Works
The Android app captures a camera frame.
The frame is converted to JPEG and encoded as Base64.
The app sends the frame to the backend using HTTP.
The backend runs YOLO inference and estimates object distance.
The backend returns structured detection results.
The app overlays results and speaks the nearest relevant object.
Setup
1. Clone the repository
git clone https://github.com/haider-dev-coder/Guide-Nest.git
cd Guide-Nest

2. Start the backend
cd backend
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000

The backend exposes:

GET /health
POST /detect
3. Open the Android app
Open the project in Android Studio and let Gradle sync.

4. Connect the app to the backend
Android emulator default: http://10.0.2.2:8000
Physical device: use your computer's LAN IP, for example http://192.168.1.10:8000
The debug build already points to the emulator URL by default. For a real device, update the backend URL inside the app settings.

Build Configuration
The app currently uses:

Debug backend URL: http://10.0.2.2:8000
Release backend URL placeholder: https://your-production-backend.example
Before publishing, replace the release backend URL in app/build.gradle with your production endpoint.

Backend Environment
The backend supports environment-based configuration. A safe example file is included at backend/.env.example.

Supported variables include:

GUIDENEST_MODEL
GUIDENEST_CONFIDENCE
GUIDENEST_IMAGE_SIZE
GUIDENEST_CAMERA_FOV_DEGREES
Example:
GUIDENEST_MODEL=yolov8n.pt
GUIDENEST_CONFIDENCE=0.35

API Example
POST /detect

Request body:
{
"imageBase64": "<jpeg-bytes-in-base64>",
"rotationDegrees": 90
}

Response shape:
{
"modelVersion": "yolov8n.pt",
"detections": [
{
"label": "chair",
"confidence": 0.91,
"distanceMeters": 1.8,
"boundingBox": [12.0, 48.0, 215.0, 390.0]
}
]
}

Running Tests
Backend tests:
cd backend
pytest

Android:
Run tests from Android Studio or with Gradle as needed.

Security Notes
Do not commit real .env files, keystores, signing configs, or API secrets.
Use backend/.env.example only as a template.
Keep production backend endpoints and credentials outside the repo when possible.
Serve production traffic over HTTPS.
Production Checklist
Set a real production backend URL in app/build.gradle
Restrict CORS in the backend
Use HTTPS for the backend
Test on both emulator and physical device
Validate speech guidance and camera behavior in real scenarios
Review logs and error handling before release
Documentation
Additional project documentation is included in:

APP_DOCUMENTATION.md
QUICK_REFERENCE.md
TECHNICAL_IMPROVEMENTS.md
GuideNest-Full-Documentation.md
PROJECT_PDF_DOCUMENTATION.md
License
Add your preferred license here if you plan to distribute the project publicly.
