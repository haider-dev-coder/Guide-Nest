import base64
import io

from PIL import Image

import app as backend_app


def make_base64_image() -> str:
    image = Image.new("RGB", (32, 32), color=(255, 255, 255))
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def test_estimate_distance_meters_prefers_large_boxes():
    near = backend_app.estimate_distance_meters([0, 0, 90, 90], 100, 100)
    far = backend_app.estimate_distance_meters([0, 0, 20, 20], 100, 100)
    assert near < far


def test_decode_image_applies_rotation():
    encoded = make_base64_image()
    image = backend_app.decode_image(encoded, 90)
    assert image.size == (32, 32)


def test_get_model_raises_when_missing(monkeypatch):
    monkeypatch.setattr(backend_app, "model", None)
    try:
        backend_app.get_model()
    except Exception as exc:
        assert "Install backend requirements" in str(exc.detail)
    else:
        raise AssertionError("Expected HTTPException when model is missing")
