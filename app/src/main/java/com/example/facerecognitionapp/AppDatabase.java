package com.example.facerecognitionapp;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.facerecognitionapp.daos.UserDao;
import com.example.facerecognitionapp.entities.User;
import com.example.facerecognitionapp.utils.Converters;

@Database(entities = {User.class}, version = 1)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
}
