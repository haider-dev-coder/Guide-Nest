package com.example.guidenest.network;
import android.util.Base64;
import com.example.guidenest.model.BackendHealth;
import com.example.guidenest.model.DetectionResponse;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DetectionBackendClient {
    public static final String PREF_BACKEND_URL = "backend_url";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Gson gson = new Gson();
    private final OkHttpClient client;
    private final String baseUrl;

    public interface SuccessCallback {
        void onSuccess(DetectionResponse response);
    }

    public interface ErrorCallback {
        void onError(String errorMessage);
    }

    public interface HealthCallback {
        void onSuccess(BackendHealth health);
    }

    public DetectionBackendClient(String baseUrl) {
        this.baseUrl = sanitizeBaseUrl(baseUrl);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void detectFrame(
            byte[] jpegBytes,
            int rotation,
            SuccessCallback successCallback,
            ErrorCallback errorCallback
    ) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            errorCallback.onError("Camera frame was empty");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("imageBase64", Base64.encodeToString(jpegBytes, Base64.NO_WRAP));
        payload.put("rotationDegrees", rotation);

        Request request = new Request.Builder()
                .url(baseUrl + "/detect")
                .post(RequestBody.create(gson.toJson(payload), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                errorCallback.onError(buildNetworkErrorMessage(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response safeResponse = response) {
                    if (!safeResponse.isSuccessful()) {
                        String responseBody = safeResponse.body() != null
                                ? safeResponse.body().string()
                                : "";
                        errorCallback.onError(buildHttpErrorMessage(safeResponse.code(), responseBody));
                        return;
                    }

                    if (safeResponse.body() == null) {
                        errorCallback.onError("Backend returned an empty response");
                        return;
                    }

                    String responseBody = safeResponse.body().string();
                    DetectionResponse detectionResponse = parseDetectionResponse(responseBody);
                    successCallback.onSuccess(detectionResponse);
                } catch (Exception exception) {
                    errorCallback.onError(buildParsingErrorMessage("detection", exception));
                }
            }
        });
    }

    public void checkHealth(HealthCallback successCallback, ErrorCallback errorCallback) {
        Request request = new Request.Builder()
                .url(baseUrl + "/health")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                errorCallback.onError(buildNetworkErrorMessage(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response safeResponse = response) {
                    if (!safeResponse.isSuccessful()) {
                        String responseBody = safeResponse.body() != null
                                ? safeResponse.body().string()
                                : "";
                        errorCallback.onError(buildHttpErrorMessage(safeResponse.code(), responseBody));
                        return;
                    }

                    if (safeResponse.body() == null) {
                        errorCallback.onError("Backend returned an empty health response");
                        return;
                    }

                    String responseBody = safeResponse.body().string();
                    BackendHealth health = parseHealthResponse(responseBody);
                    successCallback.onSuccess(health);
                } catch (Exception exception) {
                    errorCallback.onError(buildParsingErrorMessage("health", exception));
                }
            }
        });
    }

    private DetectionResponse parseDetectionResponse(String responseBody) {
        DetectionResponse detectionResponse = gson.fromJson(responseBody, DetectionResponse.class);
        if (detectionResponse == null) {
            throw new IllegalStateException("Backend returned an unreadable detection response");
        }
        return detectionResponse;
    }

    private BackendHealth parseHealthResponse(String responseBody) {
        BackendHealth health = gson.fromJson(responseBody, BackendHealth.class);
        if (health == null) {
            throw new IllegalStateException("Backend returned an unreadable health response");
        }
        return health;
    }

    private String sanitizeBaseUrl(String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) {
            throw new IllegalArgumentException("Backend URL must not be empty");
        }
        String sanitized = candidate.trim();
        while (sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }

    private String buildNetworkErrorMessage(IOException exception) {
        if (exception instanceof UnknownHostException || exception instanceof ConnectException) {
            return "Cannot reach backend. If you are using the Android emulator, use http://10.0.2.2:8000. If you are using a phone, enter your computer's current LAN IP in Settings.";
        }
        if (exception instanceof SocketTimeoutException) {
            return "Backend request timed out. Check that the server is running and model inference is available.";
        }

        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Unable to reach backend"
                : message;
    }

    private String buildHttpErrorMessage(int statusCode, String responseBody) {
        String detail = extractDetail(responseBody);
        if (detail.isEmpty()) {
            return "Backend HTTP " + statusCode;
        }
        return "Backend HTTP " + statusCode + ": " + detail;
    }

    private String buildParsingErrorMessage(String endpoint, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        return "Backend returned an invalid " + endpoint + " response: " + message;
    }

    private String extractDetail(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "";
        }

        try {
            Map<?, ?> parsed = gson.fromJson(responseBody, Map.class);
            if (parsed == null) {
                return "";
            }

            Object detail = parsed.get("detail");
            if (detail instanceof String) {
                return ((String) detail).trim();
            }

            Object message = parsed.get("message");
            if (message instanceof String) {
                return ((String) message).trim();
            }
        } catch (Exception ignored) {
            // Fall through and return a compact raw response when JSON parsing fails.
        }

        String compact = responseBody.trim().replaceAll("\\s+", " ");
        return compact.length() > 160 ? compact.substring(0, 160) + "..." : compact;
    }
}
