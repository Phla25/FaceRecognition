package com.example.facerecognitionapp.utils;

import androidx.room.TypeConverter;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class Converters {
    @TypeConverter
    public static byte[] fromFloatArray(float[] values) {
        if (values == null) return null;
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    @TypeConverter
    public static float[] toFloatArray(byte[] bytes) {
        if (bytes == null) return null;
        FloatBuffer floatBuffer = ByteBuffer.wrap(bytes).asFloatBuffer();
        float[] array = new float[floatBuffer.remaining()];
        floatBuffer.get(array);
        return array;
    }
}
