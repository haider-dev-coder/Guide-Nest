# GuideNest Python Backend

This backend replaces the in-app Java classifier with a Python object detector.

## Run locally

```bash
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

## Run with Docker

```bash
docker build -t guidenest-backend .
docker run --rm -p 8000:8000 --env-file .env.example guidenest-backend
```

## Android configuration

- Emulator default URL: `http://10.0.2.2:8000`
- Physical device: change the backend URL in the app settings to your computer's LAN IP

## API

- `GET /health`
- `POST /detect`

`POST /detect` expects:

```json
{
  "imageBase64": "<jpeg bytes in base64>",
  "rotationDegrees": 90
}
```
