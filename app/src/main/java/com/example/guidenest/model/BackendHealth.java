package com.example.guidenest.model;

public class BackendHealth {
    private String status;
    private boolean modelLoaded;
    private String modelPath;
    private double confidenceThreshold;

    public String getStatus() {
        return status == null ? "unknown" : status;
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    public String getModelPath() {
        return modelPath == null ? "unknown" : modelPath;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
}
