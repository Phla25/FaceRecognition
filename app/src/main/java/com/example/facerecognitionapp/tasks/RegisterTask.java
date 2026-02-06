package com.example.facerecognitionapp.tasks;

import android.os.AsyncTask;
import com.example.facerecognitionapp.daos.UserDao;
import com.example.facerecognitionapp.entities.User;

public class RegisterTask extends AsyncTask<User, Void, Void> {
    private UserDao userDao;
    private RegisterCallback callback;

    public interface RegisterCallback {
        void onRegistrationComplete();
    }

    public RegisterTask(UserDao userDao, RegisterCallback callback) {
        this.userDao = userDao;
        this.callback = callback;
    }

    @Override
    protected Void doInBackground(User... users) {
        if (users[0] != null) {
            userDao.insertUser(users[0]);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (callback != null) callback.onRegistrationComplete();
    }
}