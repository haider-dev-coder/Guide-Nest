# Guide Nest

Guide Nest is an Android application designed as an object detection assistant for blind users. It uses the device's camera to detect objects in real-time and provides audio feedback in English or Roman Urdu, including the object name and estimated distance.

## Features

- Real-time object detection using Google ML Kit
- Audio feedback with object names and distance estimation
- Support for English and Roman Urdu languages
- Camera preview with object highlighting (bounding boxes)
- Distance estimation in steps and meters
- User-friendly interface for visually impaired users

## Project Structure

The project consists of the following files and directories:

- **app/src/main/java/com/example/yesnoapp/MainActivity.java**: Contains the `MainActivity` class, which handles camera, object detection, and TTS.
- **app/src/main/res/layout/activity_main.xml**: Defines the user interface including camera preview and controls.
- **app/src/main/res/values/strings.xml**: Contains string resources used in the application.
- **app/src/main/AndroidManifest.xml**: Provides essential information about the app to the Android build system.
- **app/build.gradle**: Contains the Gradle build configuration for the app module.
- **build.gradle**: The top-level Gradle build configuration for the project.
- **settings.gradle**: Specifies the modules included in the project.
- **gradle.properties**: Contains configuration properties for the Gradle build system.

## Setup Instructions

1. Clone the repository.
2. Open the project in Android Studio.
3. Ensure that the Android SDK and Google Play Services are installed and configured.
4. Add your Google ML Kit API key to the project (if required).
5. Build and run the application on an emulator or physical device with camera support.

## Usage

1. Grant camera permission when prompted.
2. Tap "Start Detection" to begin object detection.
3. Point the camera at objects to detect them.
4. The app will announce detected objects and their estimated distance.
5. Use the language button to switch between English and Roman Urdu.
6. Tap "Stop Detection" to pause detection.

## Requirements

- Android API level 21 or higher
- Camera permission
- Internet connection for ML Kit (optional, depending on model)

## Contributing

Contributions are welcome. Please ensure code follows Android development best practices and includes proper accessibility features.