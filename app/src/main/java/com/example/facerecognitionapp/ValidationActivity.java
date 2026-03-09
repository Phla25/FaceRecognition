package com.example.facerecognitionapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.facerecognitionapp.entities.User;
import com.example.facerecognitionapp.utils.FaceNetModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ValidationActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView tvStatus;
    private FaceNetModel faceNetModel;
    private List<User> userList;
    private boolean isDataLoaded = false;
    private boolean isValidated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_validation);

        previewView = findViewById(R.id.previewViewValidation);
        tvStatus = findViewById(R.id.tvStatus);
        faceNetModel = new FaceNetModel(this);

        fetchUserFromServer();
        startCamera();
    }

    private void fetchUserFromServer() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://nc72z4cv-3000.asse.devtunnels.ms/api/users").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> tvStatus.setText("Lỗi kết nối server!"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    userList = new Gson().fromJson(response.body().string(), new TypeToken<List<User>>(){}.getType());
                    isDataLoaded = true;
                    Log.d("Validation", "Đã tải " + userList.size() + " users");
                }
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    if (isValidated || !isDataLoaded || userList == null) {
                        imageProxy.close();
                        return;
                    }

                    Bitmap faceBitmap = imageProxyToBitmap(imageProxy);
                    if (faceBitmap != null) {
                        float[] currentVector = faceNetModel.recognize(faceBitmap);
                        for (User user : userList) {
                            double dist = calculateDistance(currentVector, user.getFaceData());
                            Log.d("FaceCheck", "Distance với " + user.getName() + ": " + dist);

                            if (dist < 1.0) {
                                isValidated = true;

                                // TẠO BẢN SAO ĐỂ GỬI LOG (QUAN TRỌNG)
                                // Việc copy này giúp luồng MQTT có ảnh riêng, không bị ảnh hưởng khi faceBitmap.recycle()
                                Bitmap bitmapForLog = faceBitmap.copy(faceBitmap.getConfig(), false);

                                handleSuccess(user, bitmapForLog);
                                break;
                            }
                        }
                        faceBitmap.recycle(); // Giải phóng ảnh gốc
                    }
                    imageProxy.close();
                });

                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // U và V trong ImageProxy thường có stride khác nhau, cách lấy trực tiếp của bạn dễ gây lỗi màu
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        // Xoay và Lật gương (Camera trước luôn cần lật gương để không bị ngược mặt)
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // CROP: Cắt đúng vùng khuôn mặt theo khung 0.7x0.6
        int width = rotatedBitmap.getWidth();
        int height = rotatedBitmap.getHeight();
        int cropW = (int) (width * 0.7);
        int cropH = (int) (height * 0.6);
        int left = (width - cropW) / 2;
        int top = (height - cropH) / 2;

        Bitmap croppedFace = Bitmap.createBitmap(rotatedBitmap, left, top, cropW, cropH);

        // Giải phóng bộ nhớ ngay lập tức
        bitmap.recycle();
        rotatedBitmap.recycle();

        return croppedFace;
    }

    private double calculateDistance(float[] v1, float[] v2) {
        float sum = 0;
        for (int i = 0; i < v1.length; i++) sum += Math.pow(v1[i] - v2[i], 2);
        return Math.sqrt(sum);
    }

    private void handleSuccess(User user, Bitmap bitmap) {
        runOnUiThread(() -> {
            tvStatus.setText("Xin chào " + user.getName());
            Toast.makeText(this, "Xác thực thành công!", Toast.LENGTH_SHORT).show();
            sendLoginLogToMQTT(user, bitmap);
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("matched_user", user);
            startActivity(intent);
            finish();
        });
    }

    private void sendLoginLogToMQTT(User user, Bitmap bitmap) {
        new Thread(() -> {
            MqttClient client = null;
            try {
                // Sử dụng MemoryPersistence để tránh lỗi ghi file khi activity đã đóng
                client = new MqttClient("tcp://broker.emqx.io:1883", MqttClient.generateClientId(), new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
                client.connect();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                // Nén ảnh từ bản sao
                Bitmap small = Bitmap.createScaledBitmap(bitmap, 120, 120, false);
                small.compress(Bitmap.CompressFormat.JPEG, 60, out);

                JSONObject json = new JSONObject();
                json.put("name", user.getName());
                json.put("email", user.getEmail());
                // Sử dụng NO_WRAP để chuỗi Base64 không bị ngắt dòng, giúp Server nhận JSON chuẩn
                json.put("image", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                json.put("time", new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(new Date()));

                MqttMessage message = new MqttMessage(json.toString().getBytes());
                message.setQos(1); // Đảm bảo gửi ít nhất 1 lần
                client.publish("face_app/login_logs", message);
                client.disconnect();

                Log.d("MQTT_LOG", "Gửi log thành công cho user: " + user.getName());

            } catch (Exception e) {
                Log.e("MQTT_LOG", "Lỗi gửi log: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Luôn giải phóng bitmap để tránh tràn bộ nhớ
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        }).start();
    }
}