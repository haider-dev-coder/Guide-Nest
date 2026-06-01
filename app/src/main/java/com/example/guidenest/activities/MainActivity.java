package com.example.guidenest.activities;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy.PlaneProxy;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.guidenest.BuildConfig;
import com.example.guidenest.ObjectDetectionOverlay;
import com.example.guidenest.R;
import com.example.guidenest.model.BackendHealth;
import com.example.guidenest.model.DetectionResponse;
import com.example.guidenest.model.DetectedItem;
import com.example.guidenest.network.DetectionBackendClient;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "GuideNest";

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final long ANNOUNCEMENT_INTERVAL_MS = 4000L;
    private static final long FRAME_SUBMISSION_INTERVAL_MS = 700L;

    private PreviewView previewView;
    private TextView statusTextView;
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
    private boolean isTtsReady = false;
    private boolean detectionActive = false;
    private boolean detectionStarting = false;
    private boolean isCheckingBackend = false;
    private boolean requestInFlight = false;
    private float speechRate = 1.0f;
    private long lastAnnouncementTime = 0L;
    private long lastFrameSubmissionTime = 0L;
    private String lastSpokenMessage = "";
    private String backendBaseUrl;

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private DetectionBackendClient detectionBackendClient;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences("guidenest_prefs", MODE_PRIVATE);
        backendBaseUrl = preferences.getString(
                DetectionBackendClient.PREF_BACKEND_URL,
                BuildConfig.DEFAULT_BACKEND_URL
        );
        speechRate = preferences.getFloat("speech_rate", 1.0f);

        cameraExecutor = Executors.newSingleThreadExecutor();
        detectionBackendClient = new DetectionBackendClient(backendBaseUrl);

        previewView = findViewById(R.id.previewView);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        statusTextView = findViewById(R.id.statusTextView);
        objectTextView = findViewById(R.id.objectTextView);
        distanceTextView = findViewById(R.id.distanceTextView);
        detectButton = findViewById(R.id.detectButton);
        stopButton = findViewById(R.id.stopButton);
        aboutButton = findViewById(R.id.aboutButton);
        settingsButton = findViewById(R.id.settingsButton);
        languageButton = findViewById(R.id.languageButton);
        overlayView = findViewById(R.id.overlayView);

        initTextToSpeech();
        setupListeners();
        updateDetectionButtonState();
        updateBackendStatus(getString(R.string.status_backend_template, backendBaseUrl));
        checkBackendHealth(false);

        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION
            );
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true;
                applyLanguageToTts();
                textToSpeech.setSpeechRate(speechRate);
            } else {
                updateBackendStatus(getString(R.string.status_tts_failed));
            }
        });
    }

    private void setupListeners() {
        detectButton.setOnClickListener(v -> startDetection());
        stopButton.setOnClickListener(v -> stopDetection());
        aboutButton.setOnClickListener(v -> {
            String message = getString(R.string.about_message);
            speakText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        languageButton.setOnClickListener(v -> toggleLanguage());
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindPreviewOnly();
            } catch (ExecutionException | InterruptedException e) {
                showToast(getString(R.string.camera_start_failed));
                updateBackendStatus(getString(R.string.status_camera_failed));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreviewOnly() {
        if (cameraProvider == null) {
            return;
        }
        try {
            if (imageAnalysis != null) {
                imageAnalysis.clearAnalyzer();
                imageAnalysis = null;
            }
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
        } catch (Exception exception) {
            showToast(getString(R.string.camera_start_failed));
            updateBackendStatus(getString(R.string.status_camera_failed_detail, safeMessage(exception)));
        }
    }

    private boolean bindPreviewAndAnalysis() {
        if (cameraProvider == null) {
            updateBackendStatus(getString(R.string.status_camera_failed_detail, "Camera provider is not ready yet"));
            return false;
        }
        Exception primaryFailure = null;

        try {
            bindPreviewAndAnalysisWith(
                    new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(1280, 720))
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
            );
            return true;
        } catch (Exception exception) {
            primaryFailure = exception;
        }

        try {
            bindPreviewAndAnalysisWith(
                    new ImageAnalysis.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
            );
            return true;
        } catch (Exception fallbackFailure) {
            overlayView.clearBoundingBoxes();
            objectTextView.setText(R.string.detected_object_default);
            distanceTextView.setText(R.string.distance_default);
            detectionActive = false;
            requestInFlight = false;
            detectButton.setVisibility(View.VISIBLE);
            stopButton.setVisibility(View.GONE);
            String message = primaryFailure != null ? safeMessage(primaryFailure) : safeMessage(fallbackFailure);
            showToast(getString(R.string.camera_start_failed));
            updateBackendStatus(getString(R.string.status_camera_failed_detail, message));
            return false;
        }
    }

    private void bindPreviewAndAnalysisWith(@NonNull ImageAnalysis analysis) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageAnalysis = analysis;
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
        );
    }

    private void startDetection() {
        if (detectionActive || detectionStarting || isCheckingBackend) {
            return;
        }
        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION
            );
            updateBackendStatus(getString(R.string.camera_permission_required));
            return;
        }
        if (cameraProvider == null) {
            updateBackendStatus(getString(R.string.status_camera_failed_detail, "Camera is still initializing"));
            startCamera();
            return;
        }
        detectionStarting = true;
        updateDetectionButtonState();
        updateBackendStatus(getString(R.string.status_backend_checking));
        checkBackendHealth(true);
    }

    private void stopDetection() {
        if (!detectionActive && !detectionStarting && !isCheckingBackend) {
            return;
        }
        detectionActive = false;
        detectionStarting = false;
        isCheckingBackend = false;
        requestInFlight = false;
        updateDetectionButtonState();
        overlayView.clearBoundingBoxes();
        objectTextView.setText(R.string.detected_object_default);
        distanceTextView.setText(R.string.distance_default);
        updateBackendStatus(getString(R.string.status_detection_stopped));
        bindPreviewOnly();
        speakText(getString(R.string.detection_stopped));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        try {
            if (!detectionActive || requestInFlight) {
                safeCloseImageProxy(imageProxy);
                return;
            }

            long now = System.currentTimeMillis();
            if ((now - lastFrameSubmissionTime) < FRAME_SUBMISSION_INTERVAL_MS) {
                safeCloseImageProxy(imageProxy);
                return;
            }

            if (imageProxy.getImage() == null) {
                safeCloseImageProxy(imageProxy);
                return;
            }

            if (!isSupportedAnalysisFrame(imageProxy)) {
                safeCloseImageProxy(imageProxy);
                runOnUiThread(() -> updateBackendStatus(
                        getString(R.string.status_backend_error_template, "Unsupported camera frame format")
                ));
                return;
            }

            byte[] jpegBytes = imageProxyToJpegBytes(imageProxy);
            int rawImageWidth = imageProxy.getWidth();
            int rawImageHeight = imageProxy.getHeight();
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            int imageWidth = (rotation == 90 || rotation == 270) ? rawImageHeight : rawImageWidth;
            int imageHeight = (rotation == 90 || rotation == 270) ? rawImageWidth : rawImageHeight;
            safeCloseImageProxy(imageProxy);

            requestInFlight = true;
            lastFrameSubmissionTime = now;
            runOnUiThread(() -> updateBackendStatus(getString(R.string.status_backend_processing)));

            detectionBackendClient.detectFrame(
                    jpegBytes,
                    rotation,
                    response -> runOnUiThread(() -> {
                        requestInFlight = false;
                        handleDetectionResponse(response, imageWidth, imageHeight);
                    }),
                    errorMessage -> runOnUiThread(() -> {
                        requestInFlight = false;
                        if (detectionActive) {
                            overlayView.clearBoundingBoxes();
                            objectTextView.setText(R.string.detected_object_default);
                            distanceTextView.setText(R.string.distance_default);
                            updateBackendStatus(getString(R.string.status_backend_error_template, errorMessage));
                        }
                    })
            );
        } catch (Throwable throwable) {
            requestInFlight = false;
            safeCloseImageProxy(imageProxy);
            runOnUiThread(() -> updateBackendStatus(
                    getString(R.string.status_backend_error_template, safeMessage(throwable))
            ));
        }
    }

    private void handleDetectionResponse(
            DetectionResponse response,
            int imageWidth,
            int imageHeight
    ) {
        if (!detectionActive) {
            return;
        }

        if (response == null) {
            overlayView.clearBoundingBoxes();
            objectTextView.setText(R.string.detected_object_default);
            distanceTextView.setText(R.string.distance_default);
            updateBackendStatus(getString(R.string.status_backend_error_template, getString(R.string.invalid_detection_response)));
            return;
        }

        List<DetectedItem> detections = response.getDetections();
        if (detections == null || detections.isEmpty()) {
            overlayView.clearBoundingBoxes();
            objectTextView.setText(R.string.no_object_found);
            distanceTextView.setText(R.string.distance_default);
            updateBackendStatus(getString(R.string.status_no_objects));
            return;
        }

        DetectedItem closest = choosePrimaryDetection(detections);
        if (closest == null) {
            overlayView.clearBoundingBoxes();
            objectTextView.setText(R.string.no_object_found);
            distanceTextView.setText(R.string.distance_default);
            updateBackendStatus(getString(R.string.status_no_objects));
            return;
        }

        RectF primaryBox = scaleRect(closest, imageWidth, imageHeight);
        if (primaryBox.width() > 0f && primaryBox.height() > 0f) {
            overlayView.setBoundingBoxes(Collections.singletonList(primaryBox));
        } else {
            overlayView.clearBoundingBoxes();
        }

        String displayName = getDisplayName(closest.getLabel());
        double meters = roundOneDecimal(closest.getDistanceMeters());
        int steps = Math.max(1, (int) Math.round(meters / 0.75d));
        String distanceText = getResources().getQuantityString(R.plurals.distance_steps_meters, steps, steps, meters);
        String spokenMessage = getSpokenDetectionMessage(displayName, steps, meters);
        Log.i(
                TAG,
                "Primary detection label=" + closest.getLabel()
                        + ", displayName=" + displayName
                        + ", distanceMeters=" + meters
                        + ", estimatedSteps=" + steps
                        + ", confidence=" + closest.getConfidence()
        );
        Log.i(TAG, "Spoken detection message=" + spokenMessage);

        objectTextView.setText(getString(R.string.detected_object, displayName));
        distanceTextView.setText(distanceText);
        updateBackendStatus(getString(R.string.status_detected_template, response.getModelVersion(), closest.getConfidence()));

        long now = System.currentTimeMillis();
        if ((now - lastAnnouncementTime) >= ANNOUNCEMENT_INTERVAL_MS || !spokenMessage.equals(lastSpokenMessage)) {
            speakText(spokenMessage);
            lastAnnouncementTime = now;
            lastSpokenMessage = spokenMessage;
        }
    }

    private DetectedItem choosePrimaryDetection(@NonNull List<DetectedItem> detections) {
        DetectedItem best = null;
        for (DetectedItem item : detections) {
            if (item == null || item.getBoundingBox() == null || item.getBoundingBox().size() != 4) {
                continue;
            }
            if (!Double.isFinite(item.getDistanceMeters()) || !Float.isFinite(item.getConfidence())) {
                continue;
            }
            if (best == null) {
                best = item;
                continue;
            }

            double bestDistance = best.getDistanceMeters();
            double candidateDistance = item.getDistanceMeters();
            if (candidateDistance < bestDistance) {
                best = item;
                continue;
            }
            if (Double.compare(candidateDistance, bestDistance) == 0
                    && item.getConfidence() > best.getConfidence()) {
                best = item;
            }
        }
        return best;
    }

    private RectF scaleRect(@NonNull DetectedItem item, int imageWidth, int imageHeight) {
        List<Float> box = item.getBoundingBox();
        int viewWidth = previewView.getWidth();
        int viewHeight = previewView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return new RectF();
        }
        if (box == null || box.size() != 4) {
            return new RectF();
        }

        float scale = Math.min((float) viewWidth / imageWidth, (float) viewHeight / imageHeight);
        float displayedWidth = imageWidth * scale;
        float displayedHeight = imageHeight * scale;
        float offsetX = (viewWidth - displayedWidth) / 2f;
        float offsetY = (viewHeight - displayedHeight) / 2f;

        RectF scaled = new RectF(
                offsetX + (box.get(0) * scale),
                offsetY + (box.get(1) * scale),
                offsetX + (box.get(2) * scale),
                offsetY + (box.get(3) * scale)
        );

        RectF bounds = new RectF(0f, 0f, viewWidth, viewHeight);
        if (!scaled.intersect(bounds)) {
            return new RectF();
        }

        return scaled;
    }

    private void checkBackendHealth(boolean startOnSuccess) {
        isCheckingBackend = true;
        detectionBackendClient.checkHealth(
                health -> runOnUiThread(() -> {
                    isCheckingBackend = false;
                    detectionStarting = false;
                    updateDetectionButtonState();
                    handleBackendHealth(health, startOnSuccess);
                }),
                errorMessage -> runOnUiThread(() -> {
                    isCheckingBackend = false;
                    detectionStarting = false;
                    updateDetectionButtonState();
                    updateBackendStatus(getString(R.string.status_backend_error_template, errorMessage));
                    if (startOnSuccess) {
                        overlayView.clearBoundingBoxes();
                        objectTextView.setText(R.string.detected_object_default);
                        distanceTextView.setText(R.string.distance_default);
                    }
                })
        );
    }

    private void handleBackendHealth(@NonNull BackendHealth health, boolean startOnSuccess) {
        if (!"ok".equalsIgnoreCase(health.getStatus())) {
            detectionStarting = false;
            updateDetectionButtonState();
            updateBackendStatus(getString(R.string.status_backend_unhealthy, health.getStatus()));
            return;
        }

        if (!health.isModelLoaded()) {
            detectionStarting = false;
            updateDetectionButtonState();
            updateBackendStatus(getString(R.string.status_backend_model_not_loaded, health.getModelPath()));
            return;
        }

        updateBackendStatus(getString(R.string.status_backend_ready_model, health.getModelPath()));
        if (startOnSuccess) {
            beginDetectionSession();
        } else {
            detectionStarting = false;
            updateDetectionButtonState();
        }
    }

    private void beginDetectionSession() {
        try {
            if (!bindPreviewAndAnalysis()) {
                detectionStarting = false;
                updateDetectionButtonState();
                return;
            }
        } catch (Throwable throwable) {
            detectionStarting = false;
            detectionActive = false;
            requestInFlight = false;
            overlayView.clearBoundingBoxes();
            objectTextView.setText(R.string.detected_object_default);
            distanceTextView.setText(R.string.distance_default);
            updateDetectionButtonState();
            updateBackendStatus(getString(R.string.status_camera_failed_detail, safeMessage(throwable)));
            return;
        }

        detectionActive = true;
        detectionStarting = false;
        requestInFlight = false;
        lastSpokenMessage = "";
        updateDetectionButtonState();
        objectTextView.setText(R.string.detected_object_waiting);
        distanceTextView.setText(R.string.distance_default);
        updateBackendStatus(getString(R.string.status_detection_starting));
        speakText(getString(R.string.detection_started));
    }

    private byte[] imageProxyToJpegBytes(@NonNull ImageProxy imageProxy) {
        byte[] nv21 = yuv420888ToNv21(imageProxy);

        android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new android.graphics.Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                80,
                outputStream
        );
        return outputStream.toByteArray();
    }

    private byte[] yuv420888ToNv21(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Expected YUV_420_888 but received format " + imageProxy.getFormat());
        }

        PlaneProxy[] planes = imageProxy.getPlanes();
        if (planes == null || planes.length < 3) {
            throw new IllegalArgumentException("Camera frame did not contain the expected YUV planes");
        }

        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        byte[] nv21 = new byte[width * height * 3 / 2];

        int position = 0;
        position = copyLumaPlane(planes[0], width, height, nv21, position);
        copyChromaPlanes(planes[1], planes[2], width / 2, height / 2, nv21, position);
        return nv21;
    }

    private boolean isSupportedAnalysisFrame(@NonNull ImageProxy imageProxy) {
        PlaneProxy[] planes = imageProxy.getPlanes();
        return imageProxy.getFormat() == ImageFormat.YUV_420_888
                && planes != null
                && planes.length >= 3;
    }

    private int copyLumaPlane(
            @NonNull PlaneProxy plane,
            int width,
            int height,
            @NonNull byte[] out,
            int offset
    ) {
        ByteBuffer buffer = plane.getBuffer().duplicate();
        buffer.rewind();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        byte[] rowData = new byte[rowStride];
        int output = offset;

        for (int row = 0; row < height; row++) {
            int bytesToRead = Math.min(rowStride, buffer.remaining());
            if (bytesToRead <= 0) {
                break;
            }

            Arrays.fill(rowData, (byte) 0);
            buffer.get(rowData, 0, bytesToRead);

            for (int col = 0; col < width; col++) {
                int inputIndex = col * pixelStride;
                if (inputIndex >= bytesToRead) {
                    break;
                }
                out[output++] = rowData[inputIndex];
            }
        }

        return output;
    }

    private void copyChromaPlanes(
            @NonNull PlaneProxy uPlane,
            @NonNull PlaneProxy vPlane,
            int width,
            int height,
            @NonNull byte[] out,
            int offset
    ) {
        ByteBuffer uBuffer = uPlane.getBuffer().duplicate();
        ByteBuffer vBuffer = vPlane.getBuffer().duplicate();
        uBuffer.rewind();
        vBuffer.rewind();

        int uRowStride = uPlane.getRowStride();
        int vRowStride = vPlane.getRowStride();
        int uPixelStride = uPlane.getPixelStride();
        int vPixelStride = vPlane.getPixelStride();

        byte[] uRow = new byte[uRowStride];
        byte[] vRow = new byte[vRowStride];
        int output = offset;

        for (int row = 0; row < height; row++) {
            int uBytesToRead = Math.min(uRowStride, uBuffer.remaining());
            int vBytesToRead = Math.min(vRowStride, vBuffer.remaining());
            if (uBytesToRead <= 0 || vBytesToRead <= 0) {
                break;
            }

            Arrays.fill(uRow, (byte) 0);
            Arrays.fill(vRow, (byte) 0);
            uBuffer.get(uRow, 0, uBytesToRead);
            vBuffer.get(vRow, 0, vBytesToRead);

            for (int col = 0; col < width; col++) {
                int vIndex = col * vPixelStride;
                int uIndex = col * uPixelStride;
                if (vIndex >= vBytesToRead || uIndex >= uBytesToRead) {
                    break;
                }
                out[output++] = vRow[vIndex];
                out[output++] = uRow[uIndex];
            }
        }
    }

    private void toggleLanguage() {
        englishMode = !englishMode;
        applyLanguageToTts();
        languageButton.setText(englishMode ? R.string.language_english : R.string.language_roman_urdu);
        Log.i(TAG, "Language switched to " + (englishMode ? "English" : "Roman Urdu"));
        speakText(getString(
                englishMode
                        ? R.string.language_changed_english
                        : R.string.language_changed_roman_urdu
        ));
    }

    private void applyLanguageToTts() {
        if (textToSpeech == null) {
            return;
        }
        // Roman Urdu is stored with Latin characters, so an English voice
        // pronounces it more naturally than the Urdu-script locale.
        textToSpeech.setLanguage(Locale.ENGLISH);
    }

    private void showSettingsDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding);
        container.setPadding(padding, padding, padding, padding);

        TextView endpointLabel = new TextView(this);
        endpointLabel.setText(R.string.backend_url_label);
        container.addView(endpointLabel);

        EditText endpointInput = new EditText(this);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setText(backendBaseUrl);
        container.addView(endpointInput);

        TextView speechLabel = new TextView(this);
        speechLabel.setText(getString(R.string.speech_rate_label, speechRate));
        container.addView(speechLabel);

        SeekBar speechRateSeekBar = new SeekBar(this);
        speechRateSeekBar.setMax(100);
        speechRateSeekBar.setProgress((int) (speechRate * 50f));
        speechRateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float candidateRate = Math.max(0.5f, progress / 50f);
                speechLabel.setText(getString(R.string.speech_rate_label, candidateRate));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        container.addView(speechRateSeekBar);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(container)
                .setPositiveButton(R.string.save_settings, (dialog, which) -> {
                    backendBaseUrl = endpointInput.getText().toString().trim();
                    if (backendBaseUrl.isEmpty()) {
                        backendBaseUrl = BuildConfig.DEFAULT_BACKEND_URL;
                    }

                    speechRate = Math.max(0.5f, speechRateSeekBar.getProgress() / 50f);
                    preferences.edit()
                            .putString(DetectionBackendClient.PREF_BACKEND_URL, backendBaseUrl)
                            .putFloat("speech_rate", speechRate)
                            .apply();

                    detectionBackendClient = new DetectionBackendClient(backendBaseUrl);
                    if (textToSpeech != null) {
                        textToSpeech.setSpeechRate(speechRate);
                    }
                    updateBackendStatus(getString(R.string.status_backend_template, backendBaseUrl));
                    speakText(getString(R.string.settings_saved));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getDisplayName(String label) {
        String normalized = label == null ? "" : label.toLowerCase(Locale.ROOT).trim();
        if (englishMode) {
            return normalized.isEmpty() ? getString(R.string.unknown_object) : normalized;
        }

        switch (normalized) {
            case "chair":
                return "kursi";
            case "table":
                return "mez";
            case "bottle":
                return "botal";
            case "cup":
                return "cup";
            case "laptop":
                return "laptop";
            case "cell phone":
                return "phone";
            case "backpack":
                return "jhola";
            case "book":
                return "kitaab";
            case "dog":
                return "kutta";
            case "cat":
                return "billee";
            case "car":
                return "gaari";
            case "bus":
                return "bus";
            case "truck":
                return "truck";
            case "bicycle":
                return "cycle";
            case "motorcycle":
                return "bike";
            case "door":
                return "darwaza";
            case "stairs":
                return "seedhiyaan";
            case "couch":
                return "sofa";
            case "bed":
                return "bistar";
            case "tv":
                return "tv";
            case "refrigerator":
                return "fridge";
            case "potted plant":
                return "podha";
            case "dining table":
                return "mez";
            default:
                return normalized.isEmpty() ? getString(R.string.unknown_object) : normalized;
        }
    }

    private String getDistanceSpeechEnglish(int steps, double meters) {
        if (steps <= 3) {
            return getResources().getQuantityString(R.plurals.steps_ahead, steps, steps);
        }
        return getString(R.string.meters_away, (int) Math.round(meters));
    }

    private String getDistanceSpeechRomanUrdu(int steps, double meters) {
        if (steps == 1) {
            return getString(R.string.roman_urdu_one_step_distance);
        }
        if (steps <= 6) {
            return getString(R.string.roman_urdu_steps_distance, toRomanUrduNumber(steps));
        }
        return getString(R.string.roman_urdu_meters_distance, Math.max(1, (int) Math.round(meters)));
    }

    private String getSpokenDetectionMessage(String displayName, int steps, double meters) {
        if (englishMode) {
            return getString(
                    R.string.spoken_detection_template,
                    displayName,
                    getDistanceSpeechEnglish(steps, meters)
            );
        }
        return getString(
                R.string.spoken_detection_roman_urdu_template,
                displayName,
                getDistanceSpeechRomanUrdu(steps, meters)
        );
    }

    private String toRomanUrduNumber(int number) {
        switch (number) {
            case 1:
                return "aik";
            case 2:
                return "do";
            case 3:
                return "teen";
            case 4:
                return "chaar";
            case 5:
                return "paanch";
            case 6:
                return "chay";
            case 7:
                return "saat";
            case 8:
                return "aath";
            case 9:
                return "nau";
            case 10:
                return "das";
            default:
                return Integer.toString(number);
        }
    }

    private void speakText(String text) {
        if (!isTtsReady || textToSpeech == null || text == null || text.trim().isEmpty()) {
            return;
        }
        Log.i(TAG, "TTS speak request=" + text);
        textToSpeech.stop();
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuideNestTts");
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    private void updateBackendStatus(String message) {
        statusTextView.setText(message);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? exception.getClass().getSimpleName() : message;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private void safeCloseImageProxy(@NonNull ImageProxy imageProxy) {
        try {
            imageProxy.close();
        } catch (Exception ignored) {
            // Ignore double-close or device-specific close races.
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateDetectionButtonState() {
        if (detectButton == null || stopButton == null) {
            return;
        }

        boolean shouldShowStop = detectionActive || detectionStarting || isCheckingBackend;
        detectButton.setVisibility(shouldShowStop ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(shouldShowStop ? View.VISIBLE : View.GONE);
        stopButton.setEnabled(detectionActive || detectionStarting || isCheckingBackend);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            showToast(getString(R.string.camera_permission_required));
        }
    }

    @Override
    protected void onDestroy() {
        detectionActive = false;
        detectionStarting = false;
        requestInFlight = false;
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }
}
