plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.facerecognitionapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.facerecognitionapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    aaptOptions {
        noCompress("tflite")
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.6") // Sử dụng dấu nháy kép và ngoặc tròn
    // CameraX core library
    val camerax_version = "1.3.3" // Cập nhật phiên bản mới nhất
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    // Nếu bạn muốn dùng thêm tính năng hỗ trợ Java
    implementation("androidx.room:room-common:$room_version")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Cái đặt MQTT
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
}