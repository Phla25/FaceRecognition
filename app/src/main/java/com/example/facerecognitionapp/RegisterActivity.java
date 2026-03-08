package com.example.facerecognitionapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class RegisterActivity extends AppCompatActivity {
    private PreviewView previewView;
    private EditText etName, etEmail;
    private Button btnCapture;
    private FaceNetModel faceNetModel;
    private Bitmap latestFaceBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        previewView = findViewById(R.id.previewViewRegister);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        btnCapture = findViewById(R.id.btnCaptureAndSave);

        faceNetModel = new FaceNetModel(this);
        startCamera();

        btnCapture.setOnClickListener(v -> {
            String name = etName.getText().toString();
            String email = etEmail.getText().toString();

            // Khóa ảnh ngay lập tức bằng cách tạo bản sao để tránh bị luồng Analyzer recycle()
            Bitmap bitmapToSave = null;
            if (latestFaceBitmap != null && !latestFaceBitmap.isRecycled()) {
                bitmapToSave = latestFaceBitmap.copy(latestFaceBitmap.getConfig(), false);
            }

            if (name.isEmpty() || email.isEmpty() || bitmapToSave == null) {
                Toast.makeText(this, "Vui lòng nhập đủ thông tin và soi mặt vào khung", Toast.LENGTH_SHORT).show();
                return;
            }

            // Thực hiện nhận diện trên bản sao an toàn
            float[] faceVector = faceNetModel.recognize(bitmapToSave);
            com.example.facerecognitionapp.entities.User user = new com.example.facerecognitionapp.entities.User(name, email, faceVector);

            btnCapture.setEnabled(false);
            Toast.makeText(this, "Đang gửi dữ liệu...", Toast.LENGTH_SHORT).show();

            // Gửi bản sao an toàn đi
            sendRegistrationToMQTT(user, bitmapToSave);
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    Bitmap bmp = imageProxyToBitmap(imageProxy);
                    if (bmp != null) {
                        if (latestFaceBitmap != null) latestFaceBitmap.recycle();
                        latestFaceBitmap = bmp;
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

    private void sendRegistrationToMQTT(com.example.facerecognitionapp.entities.User user, Bitmap bitmap) {
        new Thread(() -> {
            try {
                org.eclipse.paho.client.mqttv3.MqttClient client = new org.eclipse.paho.client.mqttv3.MqttClient("tcp://broker.emqx.io:1883", org.eclipse.paho.client.mqttv3.MqttClient.generateClientId(), new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
                client.connect();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // Nén ảnh từ bản sao an toàn
                Bitmap small = Bitmap.createScaledBitmap(bitmap, 120, 120, false);
                small.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                String base64Image = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

                JSONObject json = new JSONObject();
                json.put("name", user.getName());
                json.put("email", user.getEmail());
                json.put("image", base64Image);

                org.json.JSONArray vectorJson = new org.json.JSONArray();
                for (float v : user.getFaceData()) vectorJson.put((double) v);
                json.put("face_vector", vectorJson);

                org.eclipse.paho.client.mqttv3.MqttMessage msg = new org.eclipse.paho.client.mqttv3.MqttMessage(json.toString().getBytes());
                msg.setQos(1);
                client.publish("face_app/register_logs", msg);
                client.disconnect();

                // Giải phóng bản sao sau khi gửi xong
                bitmap.recycle();
                small.recycle();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e("MQTT", "Lỗi: " + e.getMessage());
                // Giải phóng ảnh nếu lỗi
                if (bitmap != null) bitmap.recycle();
                runOnUiThread(() -> {
                    btnCapture.setEnabled(true);
                    Toast.makeText(this, "Lỗi gửi MQTT!", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}