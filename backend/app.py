import base64
import io
import os
import logging
import time
from typing import List

import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from PIL import Image

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    from ultralytics import YOLO
except ImportError:  # pragma: no cover
    YOLO = None


MODEL_PATH = os.getenv("GUIDENEST_MODEL", "yolov8n.pt")
CONFIDENCE_THRESHOLD = float(os.getenv("GUIDENEST_CONFIDENCE", "0.20"))
INFERENCE_IMAGE_SIZE = int(os.getenv("GUIDENEST_IMAGE_SIZE", "960"))
DEFAULT_CAMERA_FOV_DEGREES = float(os.getenv("GUIDENEST_CAMERA_FOV_DEGREES", "69.0"))

OBJECT_DIMENSIONS_METERS = {
    "person": {"height": 1.70, "width": 0.45},
    "chair": {"height": 0.90, "width": 0.45},
    "couch": {"height": 0.90, "width": 1.80},
    "sofa": {"height": 0.90, "width": 1.80},
    "bed": {"height": 0.60, "width": 1.90},
    "dining table": {"height": 0.75, "width": 1.20},
    "table": {"height": 0.75, "width": 1.20},
    "tv": {"height": 0.65, "width": 1.10},
    "laptop": {"height": 0.24, "width": 0.35},
    "cell phone": {"height": 0.15, "width": 0.07},
    "bottle": {"height": 0.28, "width": 0.08},
    "cup": {"height": 0.10, "width": 0.08},
    "book": {"height": 0.24, "width": 0.17},
    "backpack": {"height": 0.45, "width": 0.30},
    "handbag": {"height": 0.28, "width": 0.22},
    "suitcase": {"height": 0.65, "width": 0.42},
    "dog": {"height": 0.60, "width": 0.80},
    "cat": {"height": 0.25, "width": 0.45},
    "car": {"height": 1.50, "width": 1.80},
    "bus": {"height": 3.20, "width": 2.55},
    "truck": {"height": 3.20, "width": 2.50},
    "motorcycle": {"height": 1.10, "width": 0.80},
    "bicycle": {"height": 1.00, "width": 1.70},
    "potted plant": {"height": 0.65, "width": 0.35},
    "refrigerator": {"height": 1.75, "width": 0.75},
    "microwave": {"height": 0.35, "width": 0.50},
    "oven": {"height": 0.60, "width": 0.60},
    "sink": {"height": 0.25, "width": 0.55},
    "toilet": {"height": 0.75, "width": 0.40},
    "door": {"height": 2.00, "width": 0.90},
}

app = FastAPI(title="GuideNest Detector", version="1.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

logger.info(f"Loading model from: {MODEL_PATH}")
# Do not load model at import time. Model will be loaded during startup_event.
model = None


class DetectRequest(BaseModel):
    imageBase64: str
    rotationDegrees: int = 0


class DetectedItem(BaseModel):
    label: str
    confidence: float
    distanceMeters: float
    boundingBox: List[float]


class DetectResponse(BaseModel):
    modelVersion: str
    detections: List[DetectedItem]



def get_model():
    if model is None:
        raise HTTPException(
            status_code=500,
            detail="ultralytics is not installed or model failed to load. Install backend requirements first."
        )
    return model


def decode_image(image_base64: str, rotation_degrees: int) -> Image.Image:
    try:
        if not image_base64 or image_base64.strip() == "":
            raise ValueError("Image base64 string is empty")
        
        image_bytes = base64.b64decode(image_base64)
        
        if len(image_bytes) == 0:
            raise ValueError("Decoded image bytes are empty")
        
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        
        # Validate image dimensions after decode
        if image.size[0] <= 0 or image.size[1] <= 0:
            raise ValueError(f"Invalid image dimensions after decode: {image.size}")
        
        if rotation_degrees and rotation_degrees % 360 != 0:
            # Normalize rotation to 0-359 range
            normalized_rotation = rotation_degrees % 360
            image = image.rotate(-normalized_rotation, expand=True)
            
            # Validate dimensions after rotation
            if image.size[0] <= 0 or image.size[1] <= 0:
                raise ValueError(f"Invalid image dimensions after rotation: {image.size}")
        
        return image
    except (ValueError, TypeError) as exc:
        raise HTTPException(status_code=400, detail=f"Invalid image payload: {exc}") from exc
    except Exception as exc:  # pragma: no cover
        raise HTTPException(status_code=400, detail=f"Image decode failed: {exc}") from exc


def estimate_distance_meters(box: List[float], width: int, height: int, label: str = "") -> float:
    # Validate input parameters
    if not box or len(box) != 4:
        return 5.0  # Default distance for invalid box
    
    if width <= 0 or height <= 0:
        return 5.0  # Default distance for invalid dimensions
    
    try:
        x1, y1, x2, y2 = box
        
        # Ensure coordinates are within valid range
        x1 = max(0.0, min(float(x1), float(width)))
        y1 = max(0.0, min(float(y1), float(height)))
        x2 = max(0.0, min(float(x2), float(width)))
        y2 = max(0.0, min(float(y2), float(height)))
        
        # Ensure x1 < x2 and y1 < y2
        if x1 >= x2:
            x1, x2 = x2, x1
        if y1 >= y2:
            y1, y2 = y2, y1
        
        box_width = max(1.0, x2 - x1)
        box_height = max(1.0, y2 - y1)

        # Approximate focal length from a common mobile-camera FOV so distance
        # scales more realistically than the previous coarse area buckets.
        focal_length_px = width / (2.0 * np.tan(np.deg2rad(DEFAULT_CAMERA_FOV_DEGREES / 2.0)))

        normalized_label = (label or "").strip().lower()
        known_dimensions = OBJECT_DIMENSIONS_METERS.get(normalized_label, {})
        distance_candidates = []

        real_width = known_dimensions.get("width")
        if real_width:
            distance_candidates.append((real_width * focal_length_px) / box_width)

        real_height = known_dimensions.get("height")
        if real_height:
            distance_candidates.append((real_height * focal_length_px) / box_height)

        dominant_ratio = max(box_width / float(width), box_height / float(height))
        if dominant_ratio >= 0.72:
            ratio_distance = 0.5
        elif dominant_ratio >= 0.56:
            ratio_distance = 0.9
        elif dominant_ratio >= 0.40:
            ratio_distance = 1.5
        elif dominant_ratio >= 0.28:
            ratio_distance = 2.2
        elif dominant_ratio >= 0.18:
            ratio_distance = 3.0
        elif dominant_ratio >= 0.12:
            ratio_distance = 4.0
        elif dominant_ratio >= 0.08:
            ratio_distance = 5.0
        else:
            ratio_distance = 6.5

        if distance_candidates:
            dimension_distance = float(np.median(distance_candidates))
            blended_distance = (0.75 * dimension_distance) + (0.25 * ratio_distance)
            return float(np.clip(blended_distance, 0.3, 12.0))

        return ratio_distance
    except (ValueError, TypeError):
        return 5.0  # Default distance on error


@app.on_event("startup")
async def startup_event():
    """Warm up the model on startup to avoid slow first inference."""
    global model
    # Attempt to load the model during startup. If ultralytics is not installed
    # or model loading fails, continue but leave `model` as None so the API
    # can still start and return informative errors for inference calls.
    if model is None:
        if YOLO is None:
            logger.warning("Ultralytics YOLO not installed; model will not be loaded.")
            return
        try:
            logger.info(f"Loading model from: {MODEL_PATH}")
            model = YOLO(MODEL_PATH)
            logger.info(f"Model loaded successfully: {model is not None}")
        except Exception as e:
            logger.warning(f"Model failed to load during startup (non-critical): {e}")
            model = None
            return

    try:
        logger.info("Warming up model with a dummy inference...")
        dummy_image = np.zeros((480, 640, 3), dtype=np.uint8)
        _ = model.predict(dummy_image, conf=CONFIDENCE_THRESHOLD, verbose=False)
        logger.info("Model warmup completed successfully")
    except Exception as e:
        logger.warning(f"Model warmup failed (non-critical): {e}")


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "modelLoaded": model is not None,
        "modelPath": MODEL_PATH,
        "confidenceThreshold": CONFIDENCE_THRESHOLD,
        "imageSize": INFERENCE_IMAGE_SIZE,
    }


@app.post("/detect", response_model=DetectResponse)
def detect(request: DetectRequest) -> DetectResponse:
    try:
        logger.info("Detection request received")
        start_time = time.time()
        
        active_model = get_model()

        logger.info("Decoding image...")
        image = decode_image(request.imageBase64, request.rotationDegrees)
        image_array = np.array(image)
        
        # Validate image array shape before prediction
        if len(image_array.shape) != 3 or image_array.shape[2] != 3:
            raise HTTPException(
                status_code=400,
                detail=f"Invalid image dimensions after rotation: {image_array.shape}. Expected (height, width, 3)"
            )
        
        logger.info(f"Image decoded. Shape: {image_array.shape}. Running prediction...")
        prediction_start = time.time()
        results = active_model.predict(
            image_array,
            conf=CONFIDENCE_THRESHOLD,
            imgsz=INFERENCE_IMAGE_SIZE,
            max_det=40,
            verbose=False,
        )
        prediction_time = time.time() - prediction_start
        logger.info(f"Prediction completed in {prediction_time:.2f}s")
        
        # Validate results structure
        if not results or len(results) == 0:
            logger.info("No results from prediction")
            return DetectResponse(modelVersion=MODEL_PATH, detections=[])
        
        result = results[0]
        
        # Check if boxes exist and are not empty
        if not hasattr(result, "boxes") or result.boxes is None or len(result.boxes) == 0:
            logger.info("No boxes detected")
            return DetectResponse(modelVersion=MODEL_PATH, detections=[])
        
        # Check if names mapping exists
        if not hasattr(result, "names") or result.names is None:
            raise HTTPException(
                status_code=500,
                detail="Model output missing class names mapping"
            )

        height, width = image_array.shape[:2]
        detections: List[DetectedItem] = []

        for box in result.boxes:
            try:
                # Extract and validate box coordinates
                coords = box.xyxy[0].tolist()
                if len(coords) != 4:
                    continue
                
                confidence = float(box.conf[0].item())
                class_id = int(box.cls[0].item())
                
                # Validate class_id is within range
                if class_id not in result.names:
                    continue
                
                label = result.names[class_id]
                
                x1, y1, x2, y2 = coords
                x1 = max(0.0, min(float(x1), float(width)))
                y1 = max(0.0, min(float(y1), float(height)))
                x2 = max(0.0, min(float(x2), float(width)))
                y2 = max(0.0, min(float(y2), float(height)))
                if x1 >= x2 or y1 >= y2:
                    continue
                normalized_coords = [x1, y1, x2, y2]
                
                detections.append(
                    DetectedItem(
                        label=label,
                        confidence=confidence,
                        distanceMeters=estimate_distance_meters(normalized_coords, width, height, label),
                        boundingBox=[float(value) for value in normalized_coords],
                    )
                )
            except (IndexError, ValueError, TypeError) as e:
                # Skip malformed detections
                logger.warning(f"Skipping malformed detection: {e}")
                continue

        detections.sort(key=lambda item: (item.distanceMeters, -item.confidence))
        total_time = time.time() - start_time
        logger.info(f"Detection completed in {total_time:.2f}s. Found {len(detections)} objects")
        return DetectResponse(modelVersion=MODEL_PATH, detections=detections)
    
    except HTTPException:
        raise
    except Exception as e:  # pragma: no cover
        logger.error(f"Detection error: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Frame processing failed: {str(e)}"
        ) from e
