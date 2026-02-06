package com.example.facerecognitionapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.camera.core.ImageProxy;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FaceNetModel {
    private Interpreter interpreter;
    private final int INPUT_SIZE = 112; // Kích thước chuẩn của MobileFaceNet

    public FaceNetModel(Context context) {
        try {
            // Nạp file model từ thư mục assets
            interpreter = new Interpreter(FileUtil.loadMappedFile(context, "mobile_face_net.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public float[] recognize(Bitmap faceBitmap) {
        // 1. Resize ảnh về kích thước model yêu cầu (112x112)
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);

        // 2. Chuyển Bitmap thành ByteBuffer để đưa vào AI
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

        // 3. Chuẩn bị mảng đầu ra (thường là 128 số cho FaceNet)
        float[][] output = new float[1][128];

        // 4. Chạy model
        if (interpreter != null) {
            interpreter.run(inputBuffer, output);
        }

        return output[0]; // Trả về vector khuôn mặt
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int pixelValue : intValues) {
            // Chuẩn hóa màu sắc về khoảng [-1, 1] hoặc [0, 1] tùy model
            byteBuffer.putFloat((((pixelValue >> 16) & 0xFF) - 127.5f) / 128.0f);
            byteBuffer.putFloat((((pixelValue >> 8) & 0xFF) - 127.5f) / 128.0f);
            byteBuffer.putFloat(((pixelValue & 0xFF) - 127.5f) / 128.0f);
        }
        return byteBuffer;
    }
}