package com.example.facerecognitionapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Base64;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.facerecognitionapp.entities.User;
import com.example.facerecognitionapp.utils.FaceNetModel;
import com.google.common.util.concurrent.ListenableFuture;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ValidationActivity extends AppCompatActivity {
    private PreviewView previewViewValidation;
    private TextView tvStatus;
    private FaceNetModel faceNetModel;
    private User user;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private boolean isValidated = false; // Biến cờ đánh dấu

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_validation);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        init();
        checkCameraPermission();
    }
    private void init(){
        previewViewValidation = findViewById(R.id.previewViewValidation);
        tvStatus = findViewById(R.id.tvStatus);
        faceNetModel = new FaceNetModel(this);
        user = (User)getIntent().getSerializableExtra("user");
    }
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_DENIED) {
            // Nếu chưa có quyền, thì hỏi người dùng
            ActivityCompat.requestPermissions(this,
                    new String[] {android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            // Nếu đã có quyền rồi thì mở Camera
            startCamera();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền Camera", Toast.LENGTH_SHORT).show();
                startCamera();
            } else {
                Toast.makeText(this, "Bạn cần cấp quyền Camera để sử dụng tính năng này", Toast.LENGTH_LONG).show();
            }
        }
    }
    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try{
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewViewValidation.getSurfaceProvider());
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    if (isValidated) {
                        imageProxy.close();
                        return;
                    }
                    Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
                    if (fullBitmap != null) {
                        // 1. Tính toán vùng Crop (Lấy vùng giữa ảnh tương ứng với khung trên màn hình)
                        int width = fullBitmap.getWidth();
                        int height = fullBitmap.getHeight();

                        // Cắt một vùng hình chữ nhật đứng ở giữa ảnh
                        int cropW = (int) (width * 0.7); // Lấy 70% chiều rộng
                        int cropH = (int) (height * 0.6); // Lấy 60% chiều cao
                        int left = (width - cropW) / 2;
                        int top = (height - cropH) / 2;

                        Bitmap croppedFace = Bitmap.createBitmap(fullBitmap, left, top, cropW, cropH);

                        // 2. Đưa ảnh đã CROP vào nhận diện
                        float[] currentFaceVector = faceNetModel.recognize(croppedFace);
                        float[] storedFaceVector = user.getFaceData();

                        double distance = calculateDistance(currentFaceVector, storedFaceVector);
                        // === Debug ===
                        android.util.Log.d("FaceDebug", "=============================");
                        android.util.Log.d("FaceDebug", "Current vector length: " + currentFaceVector.length);
                        android.util.Log.d("FaceDebug", "Stored vector length: " + storedFaceVector.length);
                        android.util.Log.d("FaceDebug", "Current[0-5]: " + java.util.Arrays.toString(
                                java.util.Arrays.copyOfRange(currentFaceVector, 0, Math.min(5, currentFaceVector.length))
                        ));
                        android.util.Log.d("FaceDebug", "Stored[0-5]: " + java.util.Arrays.toString(
                                java.util.Arrays.copyOfRange(storedFaceVector, 0, Math.min(5, storedFaceVector.length))
                        ));
                        // === Debug ===
                        if (distance < 0.6) {
                            // Gửi lên Server Qua MQTT
                            isValidated = true;
                            sendLoginNotification(user, croppedFace);

                            runOnUiThread(() -> {
                                Toast.makeText(this, "Xác thực thành công!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(ValidationActivity.this, HomeActivity.class);
                                intent.putExtra("matched_user", (Serializable) user);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            // XÁC THỰC THẤT BẠI
                            runOnUiThread(() -> {
                                tvStatus.setText("Khuôn mặt không khớp! (" + String.format("%.2f", distance) + ")");
                            });
                        }
                    }
                    imageProxy.close();
                });
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        // 1. Chuyển đổi cơ bản sang Bitmap
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees()); // Xoay 270 độ theo máy
        //Lật gương cho camera trước
        matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    private double calculateDistance(float[] v1, float[] v2) {
        double sum = 0;
        for (int i = 0; i < v1.length; i++) {
            float diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
    private void sendLoginNotification(User user, Bitmap faceBitmap) {
        String brokerUrl = "tcp://broker.emqx.io:1883"; // Broker miễn phí để test
        String clientId = "AndroidFaceApp_" + System.currentTimeMillis();
        String topic = "face_app/login_logs";
        new Thread(() -> {
            try {
                MqttClient client = new MqttClient(brokerUrl, clientId, null);
                client.connect();
                String name = user.getName();
                String email = user.getEmail();

                // 1. Chuyển Bitmap thành Base64 (Resize nhỏ để gửi nhanh)
                Bitmap smallBitmap = Bitmap.createScaledBitmap(faceBitmap, 160, 160, false);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                smallBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                // 2. Tạo JSON payload
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("email", email);
                json.put("time", new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(new Date()));
                json.put("image", base64Image);

                // 3. Gửi tin nhắn
                MqttMessage message = new MqttMessage(json.toString().getBytes());
                client.publish(topic, message);
                client.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}