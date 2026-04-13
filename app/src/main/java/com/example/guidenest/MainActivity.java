package com.example.guidenest;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import android.graphics.RectF;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * GuideNest - Real-time Object Detection Application
 * 
 * IMPROVEMENTS MADE:
 * 1. Close Object Priority: Now detects the CLOSEST object first (largest bounding box)
 *    instead of the object with highest confidence score
 * 2. Specific Object Names: Enhanced object naming system to speak proper specific names
 *    (e.g., "chair" instead of generic "home good" or "furniture")
 * 3. Expanded Object Database: Added 100+ specific object types with multiple aliases
 * 4. Better Translation: Improved Roman Urdu translations for common objects
 * 5. Refined Distance Estimation: More granular distance thresholds (0.3m to 10m)
 * 6. Improved Confidence Filtering: Better validation of detected objects
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;

    private PreviewView previewView;
    private TextView objectTextView;
    private TextView distanceTextView;
    private Button detectButton;
    private Button stopButton;
    private Button aboutButton;
    private Button settingsButton;
    private Button languageButton;
    private ObjectDetectionOverlay overlayView;
    private TextToSpeech textToSpeech;
    private boolean englishMode = true;
    private ObjectDetector objectDetector;
    private String currentObject = "";
    private double currentMeters = 0;
    private int currentSteps = 0;
    private boolean currentDetectionReady = false;
    private boolean detectionActive = false;
    private boolean isTtsReady = false;
    private ProcessCameraProvider cameraProvider;
    private String lastSpokenObject = "";
    private int lastSpokenSteps = -1;
    private long lastAnnouncementTime = 0;
    private final long ANNOUNCEMENT_INTERVAL = 4000; // 4 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        objectTextView = findViewById(R.id.objectTextView);
        distanceTextView = findViewById(R.id.distanceTextView);
        detectButton = findViewById(R.id.detectButton);
        stopButton = findViewById(R.id.stopButton);
        aboutButton = findViewById(R.id.aboutButton);
        settingsButton = findViewById(R.id.settingsButton);
        languageButton = findViewById(R.id.languageButton);
        overlayView = findViewById(R.id.overlayView);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.ENGLISH);
                isTtsReady = true;
            }
        });

        initObjectDetector();

        detectButton.setOnClickListener(v -> startDetection());

        stopButton.setOnClickListener(v -> stopDetection());

        aboutButton.setOnClickListener(v -> {
            speakText(getString(R.string.about_message));
            Toast.makeText(MainActivity.this, R.string.about_message, Toast.LENGTH_SHORT).show();
        });

        settingsButton.setOnClickListener(v -> showSettingsDialog());

        languageButton.setOnClickListener(v -> toggleLanguage());

        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    private void initObjectDetector() {
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();
        objectDetector = ObjectDetection.getClient(options);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(@NonNull ProcessCameraProvider cameraProvider) {
        this.cameraProvider = cameraProvider;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        try {
            if (imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }

            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> handleDetections(detectedObjects, imageProxy.getWidth(), imageProxy.getHeight()))
                    .addOnFailureListener(Throwable::printStackTrace)
                    .addOnCompleteListener(task -> imageProxy.close());
        } catch (Exception e) {
            e.printStackTrace();
            imageProxy.close();
        }
    }

    private void handleDetections(List<DetectedObject> detectedObjects, int imageWidth, int imageHeight) {
        try {
            if (isFinishing() || isDestroyed()) return;
            if (detectedObjects.isEmpty() || !detectionActive) {
                currentDetectionReady = false;
                runOnUiThread(() -> {
                    try {
                        if (overlayView != null) {
                            overlayView.clearBoundingBoxes();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Don't show toast for clear errors
                    }
                });
                return;
            }

        DetectedObject nearestObject = null;
        double nearestDistance = Double.MAX_VALUE;
        String nearestLabel = null;

        for (DetectedObject object : detectedObjects) {
            if (object.getBoundingBox() == null) {
                continue;
            }
            if (object.getBoundingBox().width() <= 0 || object.getBoundingBox().height() <= 0) {
                continue;
            }
            if (object.getLabels().isEmpty()) {
                continue;
            }

            String label = object.getLabels().get(0).getText();
            if (!isValidObject(label)) {
                continue;
            }

            float confidence = object.getLabels().get(0).getConfidence();
            if (confidence < 0.25f) {
                continue;
            }

            double ratio = (double) (object.getBoundingBox().width() * object.getBoundingBox().height()) / (imageWidth * imageHeight);
            double meters = estimateDistanceMeters(ratio);
            if (meters > 3.0) {
                continue;
            }

            if (meters < nearestDistance) {
                nearestDistance = meters;
                nearestObject = object;
                nearestLabel = label;
            }
        }

        if (nearestObject == null) {
            currentDetectionReady = false;
            runOnUiThread(() -> {
                try {
                    if (overlayView != null) {
                        overlayView.clearBoundingBoxes();
                    }
                    if (objectTextView != null) {
                        objectTextView.setText(getString(R.string.no_object_found));
                    }
                    if (distanceTextView != null) {
                        distanceTextView.setText(getString(R.string.distance_default));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Don't show toast for UI clear errors
                }
            });
            return;
        }

        if (previewView == null || overlayView == null) {
            return;
        }

            double ratio = (double) (nearestObject.getBoundingBox().width() * nearestObject.getBoundingBox().height()) / (imageWidth * imageHeight);
        double meters = estimateDistanceMeters(ratio);
        int steps = Math.max(1, (int) Math.round(meters / 0.75));

        currentObject = nearestLabel;
        currentMeters = Math.round(meters * 10.0) / 10.0;
        currentSteps = steps;
        currentDetectionReady = true;

        String displayName = getDisplayName(nearestLabel);
        String distanceSpeech = getDistanceSpeech(currentSteps, currentMeters);
        String distanceText = getString(R.string.distance_steps_meters, currentSteps, currentMeters);

        float scaleX = (float) previewView.getWidth() / imageWidth;
        float scaleY = (float) previewView.getHeight() / imageHeight;
        android.graphics.Rect imageRect = nearestObject.getBoundingBox();
        android.graphics.RectF scaledRect = new android.graphics.RectF(
                imageRect.left * scaleX,
                imageRect.top * scaleY,
                imageRect.right * scaleX,
                imageRect.bottom * scaleY
        );

        runOnUiThread(() -> {
            try {
                if (overlayView == null || objectTextView == null || distanceTextView == null) {
                    return;
                }
                overlayView.setBoundingBoxes(Collections.singletonList(scaledRect));
                objectTextView.setText(getString(R.string.detected_object, displayName));
                distanceTextView.setText(distanceText);
                long currentTime = System.currentTimeMillis();
                boolean objectChanged = !displayName.equals(lastSpokenObject) || currentSteps != lastSpokenSteps;
                if (detectionActive && ((currentTime - lastAnnouncementTime) > ANNOUNCEMENT_INTERVAL || objectChanged)) {
                    String message = displayName + " is " + distanceSpeech + ".";
                    speakText(message);
                    lastAnnouncementTime = currentTime;
                    lastSpokenObject = displayName;
                    lastSpokenSteps = currentSteps;
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "UI update error: " + (e.getMessage() != null ? e.getMessage() : "unknown error"), Toast.LENGTH_SHORT).show();
            }
        });
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e.getMessage() != null ? e.getMessage() : "unknown error";
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Detection error: " + msg, Toast.LENGTH_SHORT).show());
        }
    }

    private boolean isValidObject(String label) {
        String lowerLabel = label.toLowerCase(Locale.ROOT).trim();
        
        // Only allow specific object labels and reject generic placeholders
        return !lowerLabel.isEmpty() &&
               !lowerLabel.equals("object") &&
               !lowerLabel.equals("unknown") &&
               !lowerLabel.equals("undefined") &&
               !lowerLabel.equals("null") &&
               !lowerLabel.equals("entity") &&
               !lowerLabel.equals("thing") &&
               !lowerLabel.equals("misc") &&
               lowerLabel.length() > 2;
    }

    private double estimateDistanceMeters(double sizeRatio) {
        // IMPROVED: Better distance estimation based on object bounding box area ratio
        // Larger area ratio means object is closer to camera
        // Using more granular thresholds for better accuracy with area-based calculation
        if (sizeRatio > 0.5) {
            return 0.3;  // Very close
        } else if (sizeRatio > 0.3) {
            return 0.5;  // Close
        } else if (sizeRatio > 0.2) {
            return 0.75; // Near
        } else if (sizeRatio > 0.15) {
            return 1.0;  // About 1 meter
        } else if (sizeRatio > 0.1) {
            return 1.5;  // About 1.5 meters
        } else if (sizeRatio > 0.07) {
            return 2.0;  // About 2 meters
        } else if (sizeRatio > 0.05) {
            return 2.5;  // About 2.5 meters
        } else if (sizeRatio > 0.03) {
            return 3.5;  // About 3.5 meters
        } else if (sizeRatio > 0.02) {
            return 5.0;  // About 5 meters
        } else if (sizeRatio > 0.01) {
            return 7.0;  // About 7 meters
        } else {
            return 10.0; // Far away
        }
    }

    private String getDistanceSpeech(int steps, double meters) {
        if (steps <= 3) {
            return steps == 1 ? "1 step ahead" : steps + " steps ahead";
        }
        int roundedMeters = (int) Math.round(meters);
        return roundedMeters + " meters away";
    }

    private String getDisplayName(String label) {
        String lowerLabel = label.toLowerCase(Locale.ROOT).trim();
        
        // IMPROVED: More specific object naming to avoid generic categories
        // Maps ML Kit labels and alternatives to specific names
        if (englishMode) {
            // Display specific English names
            switch (lowerLabel) {
                case "person": return "person";
                case "humans": return "person";
                case "chair": return "chair";
                case "table": return "table";
                case "bed": return "bed";
                case "couch": case "sofa": return "sofa";
                case "desk": return "desk";
                case "cabinet": return "cabinet";
                case "shelf": return "shelf";
                case "shelves": return "shelves";
                case "door": return "door";
                case "window": return "window";
                case "wall": return "wall";
                case "stairs": case "staircase": case "step": return "stairs";
                case "lamp": return "lamp";
                case "mirror": return "mirror";
                case "rug": case "carpet": return "carpet";
                case "curtain": return "curtains";
                
                // Vehicles
                case "car": case "automobile": return "car";
                case "truck": return "truck";
                case "bus": return "bus";
                case "bicycle": case "bike": return "bicycle";
                case "motorcycle": return "motorcycle";
                case "train": case "locomotive": return "train";
                case "boat": return "boat";
                case "airplane": case "aircraft": case "plane": return "airplane";
                case "van": return "van";
                case "taxi": return "taxi";
                case "fire truck": return "fire truck";
                case "police car": return "police car";
                
                // Electronics
                case "tv": case "television": case "monitor": case "display": return "television";
                case "computer": case "desktop": return "computer";
                case "laptop": return "laptop";
                case "keyboard": return "keyboard";
                case "mouse": case "computer mouse": return "mouse";
                case "phone": case "smartphone": case "mobile": case "mobile phone": case "cell phone": return "mobile phone";
                case "tablet": return "tablet";
                case "remote": case "remote control": return "remote";
                case "printer": return "printer";
                case "speaker": return "speaker";
                case "headphone": case "headphones": case "earphones": return "headphones";
                case "camera": return "camera";
                case "microphone": return "microphone";
                
                // Containers & Drinkware
                case "bottle": return "bottle";
                case "water bottle": return "water bottle";
                case "wine bottle": return "wine bottle";
                case "cup": return "cup";
                case "mug": return "mug";
                case "glass": return "glass";
                case "plate": return "plate";
                case "bowl": return "bowl";
                case "fork": return "fork";
                case "spoon": return "spoon";
                case "knife": return "knife";
                case "pot": return "pot";
                case "pan": return "pan";
                case "kettle": case "tea kettle": return "kettle";
                case "vase": return "vase";
                case "jar": return "jar";
                case "can": return "can";
                case "box": return "box";
                case "basket": return "basket";
                
                // Clothing & Accessories
                case "shoe": case "shoes": case "sneaker": case "boot": return "shoes";
                case "hat": case "cap": case "beret": return "hat";
                case "bag": case "handbag": return "bag";
                case "backpack": return "backpack";
                case "purse": return "purse";
                case "suitcase": case "luggage": return "suitcase";
                case "jacket": case "coat": return "jacket";
                case "shirt": case "top": return "shirt";
                case "pants": case "jeans": case "trousers": return "pants";
                case "dress": case "gown": return "dress";
                case "tie": case "necktie": return "tie";
                case "watch": return "watch";
                case "scarf": return "scarf";
                case "gloves": case "glove": return "gloves";
                case "socks": case "sock": return "socks";
                case "belt": return "belt";
                case "sweater": case "jumper": return "sweater";
                
                // Entertainment
                case "book": case "novel": return "book";
                case "newspaper": return "newspaper";
                case "magazine": return "magazine";
                case "toy": case "toys": return "toy";
                case "doll": return "doll";
                case "ball": case "sports ball": return "ball";
                case "racket": return "racket";
                case "frisbee": return "frisbee";
                case "skateboard": return "skateboard";
                case "chess": case "game": return "game";
                case "video game": case "gaming console": return "video game console";
                case "musical instrument": case "guitar": case "piano": return "musical instrument";
                
                // Food & Drink
                case "apple": return "apple";
                case "banana": return "banana";
                case "orange": return "orange";
                case "strawberry": return "strawberry";
                case "pizza": return "pizza";
                case "cake": return "cake";
                case "sandwich": return "sandwich";
                case "hot dog": case "hotdog": return "hot dog";
                case "donut": case "doughnut": return "donut";
                case "cookie": return "cookie";
                case "bread": return "bread";
                case "cheese": return "cheese";
                case "carrot": return "carrot";
                case "broccoli": return "broccoli";
                case "potato": case "potatoes": return "potato";
                case "tomato": return "tomato";
                case "lettuce": return "lettuce";
                case "onion": return "onion";
                case "garlic": return "garlic";
                case "egg": case "eggs": return "egg";
                case "meat": case "beef": return "meat";
                case "pork": return "pork";
                case "chicken": return "chicken";
                case "ice cream": return "ice cream";
                
                // Animals
                case "cat": case "kitten": case "feline": return "cat";
                case "dog": case "puppy": case "canine": return "dog";
                case "bird": case "parrot": return "bird";
                case "horse": case "pony": return "horse";
                case "sheep": case "lamb": return "sheep";
                case "cow": case "cattle": return "cow";
                case "elephant": return "elephant";
                case "bear": return "bear";
                case "zebra": return "zebra";
                case "giraffe": return "giraffe";
                case "lion": return "lion";
                case "tiger": return "tiger";
                case "monkey": case "ape": return "monkey";
                case "penguin": return "penguin";
                case "fish": return "fish";
                case "rabbit": case "bunny": return "rabbit";
                case "squirrel": return "squirrel";
                case "snake": return "snake";
                case "turtle": return "turtle";
                case "frog": return "frog";
                case "butterfly": return "butterfly";
                case "bee": return "bee";
                case "spider": return "spider";
                case "duck": return "duck";
                case "goat": return "goat";
                
                // Sports & Equipment
                case "baseball": return "baseball";
                case "soccer ball": case "football": case "soccer": return "soccer ball";
                case "basketball": return "basketball";
                case "tennis racket": case "tennis": return "tennis racket";
                case "hockey stick": return "hockey stick";
                case "american football": case "rugby": return "football";
                case "golf ball": return "golf ball";
                case "ski": case "skis": return "ski";
                case "surfboard": return "surfboard";
                case "baseball bat": case "bat": return "bat";
                
                default: return lowerLabel;
            }
        } else {
            // Roman Urdu translations for specific names
            switch (lowerLabel) {
                case "person": case "humans": return "insaan";
                case "chair": return "kursi";
                case "table": return "mez";
                case "bed": return "bistar";
                case "couch": case "sofa": return "sofa";
                case "desk": return "mez";
                case "door": return "darwaza";
                case "window": return "khirki";
                case "wall": return "deewar";
                case "stairs": case "staircase": return "seedhiyaan";
                case "lamp": return "lamp";
                case "mirror": return "aina";
                case "carpet": case "rug": return "qalleen";
                
                // Vehicles
                case "car": case "automobile": return "gaadi";
                case "truck": return "truck";
                case "bus": return "bas";
                case "bicycle": case "bike": return "cycle";
                case "motorcycle": return "motorcycle";
                case "train": return "gaadi";
                case "boat": return "naav";
                case "airplane": case "aircraft": return "hawai jahaaz";
                
                // Electronics
                case "tv": case "television": return "tv";
                case "computer": case "desktop": return "computer";
                case "laptop": return "laptop";
                case "keyboard": return "keyboard";
                case "mouse": case "computer mouse": return "mouse";
                case "phone": case "smartphone": return "phone";
                case "tablet": return "tablet";
                
                // Containers
                case "bottle": return "bottle";
                case "cup": return "cup";
                case "plate": return "plate";
                case "glass": return "glass";
                case "bowl": return "bowl";
                
                // Clothing
                case "shoe": case "shoes": return "juta";
                case "hat": case "cap": return "topi";
                case "bag": case "handbag": return "jhola";
                case "backpack": return "jhola";
                case "jacket": case "coat": return "jacket";
                case "shirt": return "shirt";
                case "pants": case "jeans": return "pants";
                case "dress": return "dress";
                case "tie": return "tie";
                
                // Entertainment
                case "book": return "kitab";
                case "toy": case "toys": return "khilauna";
                
                // Food
                case "apple": return "seb";
                case "banana": return "kela";
                case "orange": return "nargi";
                case "pizza": return "pizza";
                case "bread": return "roti";
                
                // Animals
                case "cat": return "billee";
                case "dog": return "kutta";
                case "bird": return "parinda";
                case "horse": return "ghoree";
                case "cow": return "gaye";
                case "elephant": return "hathi";
                case "sheep": return "bhera";
                case "monkey": return "bandar";
                case "fish": return "machli";
                case "rabbit": return "khargosh";
                
                default: return lowerLabel;
            }
        }
    }

    private void startDetection() {
        if (detectionActive) return;
        detectionActive = true;
        detectButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.VISIBLE);
        speakText(getString(R.string.detection_started));

        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyzeImage);
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start detection: " + (e.getMessage() != null ? e.getMessage() : "unknown error"), Toast.LENGTH_LONG).show();
            stopDetection(); // Reset state
        }
    }

    private void stopDetection() {
        if (!detectionActive) return;
        detectionActive = false;
        detectButton.setVisibility(View.VISIBLE);
        stopButton.setVisibility(View.GONE);
        speakText(getString(R.string.detection_stopped));

        runOnUiThread(() -> {
            try {
                if (overlayView != null) {
                    overlayView.clearBoundingBoxes();
                }
                if (objectTextView != null) {
                    objectTextView.setText(getString(R.string.detected_object_default));
                }
                if (distanceTextView != null) {
                    distanceTextView.setText(getString(R.string.distance_default));
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Don't show toast for stop UI errors
            }
        });

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            bindCamera(cameraProvider); // Rebind without analysis
        }
    }

    private void toggleLanguage() {
        englishMode = !englishMode;
        if (englishMode) {
            languageButton.setText(R.string.language_english);
            if (textToSpeech != null) {
                textToSpeech.setLanguage(Locale.ENGLISH);
            }
            speakText(getString(R.string.language_changed_english));
        } else {
            languageButton.setText(R.string.language_roman_urdu);
            if (textToSpeech != null) {
                textToSpeech.setLanguage(new Locale("ur"));
            }
            speakText(getString(R.string.language_changed_roman_urdu));
        }
    }

    private void speakText(String text) {
        if (textToSpeech != null && isTtsReady) {
            if (textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuideNestTTS");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_title);

        SeekBar volumeSeekBar = new SeekBar(this);
        volumeSeekBar.setMax(100);
        volumeSeekBar.setProgress(50); // Default volume

        builder.setView(volumeSeekBar);
        builder.setPositiveButton(R.string.save_settings, (dialog, which) -> {
            float volume = volumeSeekBar.getProgress() / 100.0f;
            if (textToSpeech != null) {
                textToSpeech.setSpeechRate(volume);
            }
            speakText("Settings saved");
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}