package com.example.guidenest.model;

import java.util.ArrayList;
import java.util.List;

public class DetectionResponse {
    private String modelVersion;
    private List<DetectedItem> detections;

    public String getModelVersion() {
        return modelVersion == null ? "unknown" : modelVersion;
    }

    public List<DetectedItem> getDetections() {
        return detections == null ? new ArrayList<>() : detections;
    }
}
