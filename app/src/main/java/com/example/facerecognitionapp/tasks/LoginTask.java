package com.example.facerecognitionapp.tasks;

import android.os.AsyncTask;
import com.example.facerecognitionapp.daos.UserDao;
import com.example.facerecognitionapp.entities.User;
import java.util.List;

public class LoginTask extends AsyncTask<float[], Void, User> {
    private UserDao userDao;
    private LoginCallback callback;

    public interface LoginCallback {
        void onLoginResult(User matchedUser);
    }

    public LoginTask(UserDao userDao, LoginCallback callback) {
        this.userDao = userDao;
        this.callback = callback;
    }

    @Override
    protected User doInBackground(float[]... params) {
        float[] currentFace = params[0];
        List<User> allUsers = userDao.getAllUsers();

        for (User user : allUsers) {
            // Bạn sẽ lấy faceData từ Entity User để so sánh
            float distance = calculateDistance(currentFace, user.getFaceData());
            // Ngưỡng 1.0f cho MobileFaceNet. Thấp hơn = giống hơn.
            if (distance < 1.0f) {
                return user;
            }
        }
        return null;
    }

    private float calculateDistance(float[] v1, float[] v2) {
        float sum = 0;
        for (int i = 0; i < v1.length; i++) {
            float diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    @Override
    protected void onPostExecute(User result) {
        if (callback != null) callback.onLoginResult(result);
    }
}