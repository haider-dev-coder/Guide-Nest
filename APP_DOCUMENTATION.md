# GuideNest - Object Detection Application - Complete Documentation

## 1. APPLICATION OVERVIEW

**GuideNest** is an Android-based object detection application designed to assist users with real-time visual object detection using their device camera. The app uses Google ML Kit's Object Detection API to identify objects in the camera feed and provides audio feedback using Text-to-Speech (TTS) technology.

**Purpose**: Help users identify objects in their surroundings with distance estimation and accessibility features.

---

## 2. PROJECT STRUCTURE

```
guide-nest/
├── build.gradle                    # Root project Gradle configuration
├── gradle.properties               # Gradle properties
├── settings.gradle                 # Project settings
├── gradlew.bat                     # Gradle wrapper for Windows
├── README.md                       # Project README
├── gradle/
│   └── wrapper/                    # Gradle wrapper files
├── app/                            # Main application module
│   ├── build.gradle                # App module Gradle configuration
│   ├── build/                      # Build output directory (generated)
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # App manifest with permissions
│           ├── java/
│           │   └── com/example/guidenest/
│           │       ├── MainActivity.java           # Main activity (core detection logic)
│           │       └── ObjectDetectionOverlay.java # Custom view for drawing bounding boxes
│           └── res/
│               ├── layout/
│               ├── values/         # String resources and configurations
│               └── ... (drawable, menu, etc.)
```

---

## 3. KEY FILES AND THEIR LOCATIONS

### 3.1 **MainActivity.java** - Core Application Logic
**Location**: `app/src/main/java/com/example/guidenest/MainActivity.java`

**Responsibilities**:
- Initializes camera and object detection
- Manages UI elements (buttons, text views, overlay)
- Processes camera frames for object detection
- Calculates distance estimation
- Handles Text-to-Speech announcements
- Manages language switching (English/Roman Urdu)
- Handles permission requests

**Key Methods**:
- `onCreate()` - Initializes UI and detection on app start
- `initObjectDetector()` - Sets up ML Kit Object Detection
- `startCamera()` - Starts camera preview
- `analyzeImage()` - Processes each camera frame
- `handleDetections()` - Processes detected objects
- `estimateDistanceMeters()` - Calculates distance based on object size
- `speakText()` - Converts text to speech
- `toggleLanguage()` - Switches between English and Urdu

### 3.2 **ObjectDetectionOverlay.java** - Visual Overlay
**Location**: `app/src/main/java/com/example/guidenest/ObjectDetectionOverlay.java`

**Responsibilities**:
- Custom View to draw bounding boxes on detected objects
- Displays red rectangular outlines around objects

**Key Methods**:
- `setBoundingBoxes()` - Updates bounding boxes to display
- `clearBoundingBoxes()` - Clears all boxes
- `onDraw()` - Renders boxes on canvas

### 3.3 **AndroidManifest.xml**
**Location**: `app/src/main/AndroidManifest.xml`

**Contents**:
- Declares CAMERA permission requirement
- Registers MainActivity as launcher activity
- Configures app theme and appearance

### 3.4 **build.gradle** (App Module)
**Location**: `app/build.gradle`

**Configuration**:
- Target SDK: 33 (Android 13)
- Min SDK: 21 (Android 5.0)
- Namespace: com.example.guidenest
- Version: 1.0

---

## 4. TOOLS AND DEPENDENCIES

### 4.1 **Build System**
- **Gradle**: Build automation tool (v8.0)
- **Android Gradle Plugin**: Compiles Android apps

### 4.2 **Core Libraries**

| Library | Component | Version | Purpose |
|---------|-----------|---------|---------|
| **AndroidX Core** | android.core:core-ktx | 1.10.1 | Android framework enhancements |
| **AndroidX AppCompat** | androidx.appcompat | 1.6.1 | Backward compatibility for app features |
| **Material Design** | com.google.android.material | 1.9.0 | Material Design UI components |
| **ML Kit Object Detection** | com.google.mlkit:object-detection | 17.0.0 | **Main detection engine** |
| **CameraX** | androidx.camera | 1.2.3 | Camera access and preview |
| **Kotlin Stdlib** | kotlin-stdlib | 1.8.10 | Kotlin standard library |

### 4.3 **Key Components**

#### **Google ML Kit Object Detection** (Main Tool)
- Runs object detection model on device (no cloud calls)
- Detects 90+ object categories
- Provides confidence scores and bounding boxes
- Works in STREAM_MODE for real-time detection

**Model Capabilities**:
- People, animals, vehicles, furniture, food, etc.
- Confidence scores (0-1 scale)
- Object coordinates (bounding box)
- Multiple object detection

#### **CameraX** (Camera Framework)
- Handles camera lifecycle management
- Provides frame-by-frame access for analysis
- Manages camera permissions
- Supports preview and analysis simultaneously

#### **TextToSpeech (Android Framework)**
- Converts detected object names to audio
- Supports multiple languages
- Adjustable speech rate and volume

---

## 5. OBJECT DETECTION WORKFLOW

```
┌─────────────────────────────────────────────────┐
│ User taps "Detect" button                       │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│ ImageAnalysis starts capturing camera frames    │
│ Resolution: 640x480, Strategy: Keep only latest │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│ analyzeImage() receives each frame              │
│ Converts to InputImage for ML Kit               │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│ objectDetector.process() runs detection         │
│ Returns list of DetectedObject with labels      │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│ handleDetections() processes results            │
│ - Selects BEST object (currently by confidence) │
│ - Calculates distance from bounding box size    │
│ - Converts distance to steps (0.75m per step)   │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│ Updates UI and overlay with detection           │
│ - Shows object name on screen                   │
│ - Shows distance in meters and steps            │
│ - Draws bounding box on object                  │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│ Text-to-Speech announces: "Chair, 5 steps away" │
│ Announcement throttled to 4-second interval     │
└─────────────────────────────────────────────────┘
```

---

## 6. DISTANCE ESTIMATION LOGIC

The app estimates distance based on the object's bounding box size relative to the frame:

| Size Ratio | Estimated Distance | Steps (approx) |
|------------|-------------------|----------------|
| > 60% | 0.5 meters | 1 step |
| 40-60% | 1.0 meters | 1-2 steps |
| 25-40% | 2.0 meters | 2-3 steps |
| 15-25% | 3.5 meters | 4-5 steps |
| 10-15% | 5.0 meters | 6-7 steps |
| < 10% | 7.0 meters | 9+ steps |

**Formula**: Steps = distance (meters) / 0.75

---

## 7. CURRENT ISSUES AND FIXES

### **Issue 1: Prioritizes Far Objects Instead of Close Objects**
**Current Logic** (Lines 160-169):
```java
DetectedObject bestObject = null;
float highestConfidence = 0.0f;
for (DetectedObject object : detectedObjects) {
    if (!object.getLabels().isEmpty()) {
        float confidence = object.getLabels().get(0).getConfidence();
        if (confidence > highestConfidence) {
            highestConfidence = confidence;
            bestObject = object;
        }
    }
}
```

**Problem**: Selects object with highest confidence, not largest size (closest object)

**Fix Applied**: Select object with largest bounding box (closest to camera)

### **Issue 2: Generic Object Names Instead of Specific Names**
**Current Logic**: Uses ML Kit labels directly which may be generic

**Fix Applied**: 
- Improved object classification system
- Map generic labels to specific names
- Better filtering for confidence

### **Issue 3: Text-to-Speech Speaks Generic Categories**
**Current**: Directly speaks the label from ML Kit

**Fix Applied**: 
- Enhanced `getDisplayName()` with better object mapping
- Added alternative names recognition
- Improved category handling

---

## 8. CONFIGURATION AND SETTINGS

### **Camera Configuration**
- **Resolution**: 640x480 pixels
- **Backpressure Strategy**: KEEP_ONLY_LATEST (drops old frames)
- **Rotation**: Handled automatically

### **Object Detector Configuration**
- **Mode**: STREAM_MODE (continuous detection)
- **Multiple Objects**: Enabled
- **Classification**: Enabled (provides object labels)

### **Speech Configuration**
- **Default Language**: English (Locale.ENGLISH)
- **Alternative Language**: Roman Urdu (Locale("ur"))
- **Announcement Interval**: 4 seconds (prevents repeated announcements)

---

## 9. UI COMPONENTS

### **Buttons**
- **Detect**: Start real-time detection
- **Stop**: Stop detection
- **About**: Show app information
- **Settings**: Adjust volume/speech rate
- **Language**: Toggle English/Urdu

### **Text Views**
- **Object Display**: Shows detected object name
- **Distance Display**: Shows distance in meters and steps

### **Overlay View**
- Draws red bounding boxes around detected objects
- Stroke width: 5px

### **Preview View**
- Real-time camera feed

---

## 10. PERMISSIONS

**Required Permissions** (declared in AndroidManifest.xml):
- `android.permission.CAMERA` - Access device camera

**Request Method**: 
- Runtime permission request on app start
- User must grant camera permission for app to function

---

## 11. MULTI-LANGUAGE SUPPORT

### **English Mode**
- Object names: Lowercase English (e.g., "chair", "person")
- Speech language: English

### **Roman Urdu Mode**
- Object names: Romanized Urdu (e.g., "kursi" for chair, "insaan" for person)
- Speech language: Urdu

**Mapping Example**:
```
English → Roman Urdu
chair → kursi
door → darwaza
stairs → seedhiyaan
backpack → jhola
```

---

## 12. HOW TO USE THE APP

1. **Launch App**: Open GuideNest
2. **Grant Permission**: Allow camera access when prompted
3. **Tap Detect**: Start object detection
4. **Point Camera**: Aim camera at objects
5. **Listen**: App announces detected objects and distance
6. **Change Language**: Tap Language button to switch languages
7. **Adjust Settings**: Modify volume via Settings button
8. **Stop**: Tap Stop button to end detection

---

## 13. TECHNICAL SPECIFICATIONS

| Specification | Value |
|---------------|-------|
| **Min API Level** | 21 (Android 5.0) |
| **Target API Level** | 33 (Android 13) |
| **Build Tool Version** | 33 |
| **Kotlin Version** | 1.8.10 |
| **ML Kit Version** | 17.0.0 |
| **CameraX Version** | 1.2.3 |
| **Gradle Version** | 8.0 |

---

## 14. ANNOUNCEMENT THROTTLING

The app prevents rapid repeated announcements:
- **Default Interval**: 4 seconds
- **Change Trigger**: Different object or distance changed
- **Prevents**: Audio spam on rapid object detection

---

## 15. ERROR HANDLING

- **Permission Denied**: Shows toast message, app won't work without camera
- **Invalid Frame**: Silently skips frames with null images
- **Detection Failure**: Catches exceptions, continues operation
- **Low Confidence Objects**: Ignores unless in valid object list

---

## 16. PERFORMANCE OPTIMIZATIONS

1. **Frame Skipping**: KEEP_ONLY_LATEST strategy drops old frames
2. **640x480 Resolution**: Balanced between accuracy and speed
3. **On-Device Model**: No network calls, faster processing
4. **Confidence Threshold**: 0.3f minimum for unknown objects
5. **Throttled Announcements**: Prevents TTS overload

---

This documentation provides complete details about the GuideNest application architecture, tools used, file locations, and how components work together.
