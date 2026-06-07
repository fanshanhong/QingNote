plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.fan.hwnote"
//    compileSdk = flutter.compileSdkVersion
//    ndkVersion = flutter.ndkVersion
    compileSdk = 36
    buildToolsVersion = "33.0.2"
    // 固定使用本地已装的 NDK 版本(国内下载 Google NDK 经常被墙)。
    // 升级方法:先用 sdkmanager 装好新版本,再改这里。
    ndkVersion = "25.1.8937393"


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.fan.hwnote"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = 23
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.12.0")
        force("androidx.core:core-ktx:1.12.0")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

flutter {
    source = "../.."
}
