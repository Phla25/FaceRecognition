package com.example.facerecognitionapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FaceNetModel {
    private static final String TAG = "FaceNetModel";
    private Interpreter interpreter;
    private final int INPUT_SIZE = 112;

    public FaceNetModel(Context context) {
        try {
            interpreter = new Interpreter(FileUtil.loadMappedFile(context, "mobilefacenet.tflite"));
            Log.d(TAG, "Model loaded successfully!");
        } catch (IOException e) {
            Log.e(TAG, "FAILED to load model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public float[] recognize(Bitmap faceBitmap) {
        // Kiểm tra interpreter có null không
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is NULL! Model not loaded!");
            return new float[128]; // Trả về vector rỗng
        }

        // 1. Resize ảnh về 112x112
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);

        // 2. Chuyển Bitmap thành ByteBuffer
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

        // 3. Chuẩn bị đầu ra
        float[][] output = new float[1][192];

        // 4. Chạy model
        try {
            interpreter.run(inputBuffer, output);

            // Log để debug
            Log.d(TAG, "Inference success! Vector[0-5]: " +
                    output[0][0] + ", " + output[0][1] + ", " + output[0][2] + ", " +
                    output[0][3] + ", " + output[0][4]);

        } catch (Exception e) {
            Log.e(TAG, "Inference FAILED: " + e.getMessage());
            e.printStackTrace();
        }

        return output[0];
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];

        // ===== SỬA LỖI Ở ĐÂY =====
        // Syntax đúng: getPixels(pixels, offset, stride, x, y, width, height)
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Normalize về [-1, 1] cho MobileFaceNet
        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int val = intValues[pixel++];

                // Tách RGB và normalize
                float r = (((val >> 16) & 0xFF) - 127.5f) / 128.0f;
                float g = (((val >> 8) & 0xFF) - 127.5f) / 128.0f;
                float b = ((val & 0xFF) - 127.5f) / 128.0f;

                byteBuffer.putFloat(r);
                byteBuffer.putFloat(g);
                byteBuffer.putFloat(b);
            }
        }

        return byteBuffer;
    }

    // Thêm hàm đóng model khi không dùng nữa
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            Log.d(TAG, "Model closed");
        }
    }
}