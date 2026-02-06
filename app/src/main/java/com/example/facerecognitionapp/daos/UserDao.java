package com.example.facerecognitionapp.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.facerecognitionapp.entities.User;

import java.util.List;

@Dao
public interface UserDao {
    // Lưu người dùng mới (Lúc đăng ký khuôn mặt)
    @Insert
    void insertUser(User user);
    // Lấy tất cả người dùng để so sánh khi đăng nhập
    @Query("SELECT * FROM users")
    List<User> getAllUsers();
    // Tìm kiếm nhanh theo tên (nếu cần)
    @Query("SELECT * FROM users WHERE user_name LIKE :name LIMIT 1")
    User findByName(String name);
    // Xóa dữ liệu (nếu muốn reset app)
    @Query("DELETE FROM users")
    void deleteAll();
    // Tìm người dùng dựa trên email và tên người dùng
    @Query("SELECT * FROM users WHERE user_name = :username AND user_email = :email")
    User findUserByUsernameAndEmail(String username, String email);
    @Query("SELECT * FROM users WHERE user_email = :email")
    User findUserByEmail(String email);
}