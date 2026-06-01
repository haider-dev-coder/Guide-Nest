# Guide Nest

Guide Nest is an Android accessibility app that captures camera frames, sends them to a Python object-detection backend, and speaks the closest detected object's name and distance.

## What changed

- The Android app now focuses on camera capture, UI, and speech.
- Object detection moved out of the app and into `backend/app.py`.
- The client talks to the backend over HTTP, which makes the detection model swappable and easier to improve.

## Project layout

- `app/` Android client
- `backend/` Python detection API
- `error.pdf` audit of issues found and the fixes applied

## Run the backend

```bash
cd backend
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

## Production checklist

1. Install backend dependencies from `backend/requirements.txt`.
2. Replace the release backend URL in `app/build.gradle`.
3. Serve the backend behind HTTPS and restrict CORS to trusted origins.
4. Run `./gradlew assembleRelease` in a network-enabled environment.
5. Run backend tests and a real device camera test before release.

## Run the app

1. Open the Android project in Android Studio.
2. Start the Python backend.
3. Launch the app.
4. If you are using a physical device, update the backend URL in Settings.
