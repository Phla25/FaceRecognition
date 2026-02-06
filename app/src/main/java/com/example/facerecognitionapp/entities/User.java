package com.example.facerecognitionapp.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.facerecognitionapp.utils.Converters;

import java.io.Serializable;

@Entity(tableName = "users")
@TypeConverters(Converters.class)
public class User implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo(name = "user_name")
    private String name;
    @ColumnInfo(name = "user_email")
    private String email;
    @ColumnInfo(name = "face_data")
    private float[] faceData;
    // Constructor, getters, setters, ...
    public User(String name, String email, float[] faceData) {
        this.name = name;
        this.email = email;
        this.faceData = faceData;
    }
    public int getId(){
        return id;
    }
    public void setId(int id){
        this.id = id;
    }
    public String getName(){
        return name;
    }
    public String getEmail(){
        return email;
    }

    public float[] getFaceData() {
        return faceData;
    }

}
