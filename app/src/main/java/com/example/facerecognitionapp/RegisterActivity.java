package com.example.facerecognitionapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.room.Room;

import com.example.facerecognitionapp.daos.UserDao;
import com.example.facerecognitionapp.entities.User;
import com.example.facerecognitionapp.tasks.RegisterTask;
import com.example.facerecognitionapp.utils.FaceNetModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class RegisterActivity extends AppCompatActivity {
    private PreviewView previewView;
    private EditText etName;
    private EditText etEmail;
    private Button btnCaptureAndSave;
    private FaceNetModel faceNetModel;
    private UserDao userDao;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        init();
        startCamera();
    }
    private void init(){
        previewView = findViewById(R.id.previewViewRegister);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        btnCaptureAndSave = findViewById(R.id.btnCaptureAndSave);
        faceNetModel = new FaceNetModel(this);
        AppDatabase appDatabase = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "face-recognition-db").build();
        userDao = appDatabase.userDao();
        btnCaptureAndSave.setOnClickListener(v -> {
            if (etName.getText().toString().trim().isEmpty() || etEmail.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên và email!", Toast.LENGTH_SHORT).show();
            } else if (!isValidEmail(etEmail.getText().toString())) {
                Toast.makeText(this, "Email không hợp lệ!", Toast.LENGTH_SHORT).show();
            } else {
                new Thread(()->{
                    if (userDao.findUserByEmail(etEmail.getText().toString()) != null){
                        runOnUiThread(() -> Toast.makeText(this, "Email đã tồn tại!", Toast.LENGTH_SHORT).show());
                    } else {
                        isProcessing = true;
                    }
                }).start();
            }
        });
    }
    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try{
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    if (isProcessing){
                        Bitmap bitmap = imageProxyToBitmap(imageProxy);
                        if (bitmap != null) {

                            float[] faceVector = faceNetModel.recognize(bitmap);
                            // Tạo Đối tượng User mới
                            User user = new User(etName.getText().toString(),
                                    etEmail.getText().toString(),
                                    faceVector
                            );
                            // Thực hiện lưu vào Database
                            new RegisterTask(userDao, ()->{
                                runOnUiThread(() -> {
                                    Toast.makeText(RegisterActivity.this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                                    cameraProvider.unbindAll();
                                    finish(); // Quay lại Login
                                });
                            }).execute(user);
                        }
                        isProcessing = false;
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
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
    private boolean isValidEmail(String email) {
        String emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";
        return email.matches(emailPattern);
    }
}