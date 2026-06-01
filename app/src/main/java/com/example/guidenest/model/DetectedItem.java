package com.example.guidenest.model;

import java.util.List;

public class DetectedItem {
    private String label;
    private float confidence;
    private double distanceMeters;
    private List<Float> boundingBox;

    public String getLabel() {
        return label;
    }

    public float getConfidence() {
        return confidence;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public List<Float> getBoundingBox() {
        return boundingBox;
    }
}
