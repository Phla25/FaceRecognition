package com.example.facerecognitionapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.room.Room;

import com.example.facerecognitionapp.daos.UserDao;
import com.example.facerecognitionapp.entities.User;
import java.io.Serializable;

public class LoginActivity extends AppCompatActivity {
    private AppDatabase appDatabase;
    private UserDao userDao;
    private Button btnLogin;
    private Button btnRegister;
    private EditText etUsernameLogin;
    private EditText etEmailLogin;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        init();
        appDatabase = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "face-recognition-db").build();
        userDao = appDatabase.userDao();
        btnLogin.setOnClickListener(v -> {
            String username = etUsernameLogin.getText().toString();
            String email = etEmailLogin.getText().toString();

            if (username.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên và email!", Toast.LENGTH_SHORT).show();
            } else {
                // Tạo một luồng mới để truy vấn database
                new Thread(() -> {
                    try {
                        User user = userDao.findUserByUsernameAndEmail(username, email);

                        // Sau khi có kết quả, quay về luồng chính để cập nhật giao diện hoặc chuyển trang
                        runOnUiThread(() -> {
                            if (user != null) {
                                Intent intent = new Intent(LoginActivity.this, ValidationActivity.class);
                                intent.putExtra("user", (Serializable) user);
                                startActivity(intent);
                            } else {
                                Toast.makeText(this, "Tài khoản không tồn tại, vui lòng Đăng ký", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Lỗi truy vấn dữ liệu!", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            }
        });
        btnRegister.setOnClickListener(v->{
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
    private void init(){
        etEmailLogin = findViewById(R.id.etEmailLogin);
        etUsernameLogin = findViewById(R.id.etUsernameLogin);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
    }
    private User findUserByUsernameAndEmail(String username, String email){
        return userDao.findUserByUsernameAndEmail(username, email);
    }
}