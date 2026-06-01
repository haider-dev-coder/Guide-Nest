#!/usr/bin/env python
"""Validation tests for backend frame processing fixes."""

import base64
import io
from PIL import Image
import app


def test_valid_image_decoding_with_rotation():
    """Test 1: Valid image decoding and rotation."""
    print("Test 1: Valid image decoding with rotation...")
    try:
        test_image = Image.new("RGB", (640, 480), color=(100, 100, 100))
        buffer = io.BytesIO()
        test_image.save(buffer, format="JPEG")
        encoded = base64.b64encode(buffer.getvalue()).decode("utf-8")
        
        decoded = app.decode_image(encoded, 90)
        print(f"✓ Image decoded successfully: {decoded.size}")
        assert decoded.size[0] > 0 and decoded.size[1] > 0, "Invalid image size after rotation"
        print(f"✓ Rotated image dimensions valid: {decoded.size}")
    except Exception as e:
        print(f"✗ Error: {e}")
        raise


def test_invalid_base64_handling():
    """Test 2: Invalid base64 error handling."""
    print("\nTest 2: Invalid base64 error handling...")
    try:
        app.decode_image("invalid===", 0)
        print("✗ Should have raised error for invalid base64")
        raise AssertionError("Should have raised HTTPException")
    except Exception as e:
        if "HTTPException" in str(type(e)) or "Invalid" in str(e):
            print(f"✓ Correctly caught error: {str(e)[:50]}...")
        else:
            raise


def test_empty_base64_handling():
    """Test 3: Empty base64 error handling."""
    print("\nTest 3: Empty base64 error handling...")
    try:
        app.decode_image("", 0)
        print("✗ Should have raised error for empty base64")
        raise AssertionError("Should have raised HTTPException")
    except Exception as e:
        if "HTTPException" in str(type(e)) or "empty" in str(e).lower():
            print(f"✓ Correctly caught error: {str(e)[:50]}...")
        else:
            raise


def test_distance_estimation_valid():
    """Test 4: Distance estimation with valid coordinates."""
    print("\nTest 4: Distance estimation with valid coordinates...")
    distance = app.estimate_distance_meters([100, 100, 200, 200], 640, 480)
    print(f"✓ Distance calculated: {distance} meters")
    assert 0.4 <= distance <= 5.0, f"Distance out of range: {distance}"


def test_distance_estimation_invalid_box():
    """Test 5: Distance estimation with invalid box."""
    print("\nTest 5: Distance estimation with invalid box...")
    distance = app.estimate_distance_meters(None, 640, 480)
    print(f"✓ Default distance returned for invalid box: {distance} meters")
    assert distance == 5.0


def test_distance_estimation_invalid_dimensions():
    """Test 6: Distance estimation with invalid dimensions."""
    print("\nTest 6: Distance estimation with invalid dimensions...")
    distance = app.estimate_distance_meters([0, 0, 100, 100], 0, 0)
    print(f"✓ Default distance returned for invalid dimensions: {distance} meters")
    assert distance == 5.0


if __name__ == "__main__":
    test_valid_image_decoding_with_rotation()
    test_invalid_base64_handling()
    test_empty_base64_handling()
    test_distance_estimation_valid()
    test_distance_estimation_invalid_box()
    test_distance_estimation_invalid_dimensions()
    print("\n✓ All validation tests passed!")
