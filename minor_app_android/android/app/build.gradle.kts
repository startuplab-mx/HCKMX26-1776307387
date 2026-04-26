plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

// Leer OPENROUTER_API_KEY de local.properties (NO commitear)
val localPropertiesFile = rootProject.file("local.properties")
val openRouterApiKey: String = if (localPropertiesFile.exists()) {
    localPropertiesFile.readLines()
        .firstOrNull { it.startsWith("OPENROUTER_API_KEY=") }
        ?.substringAfter("=")?.trim() ?: ""
} else ""

android {
    namespace = "com.example.minor_app_android"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.example.minor_app_android"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // OpenRouter API key — leer de local.properties (NO commitear)
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"$openRouterApiKey\""
        )
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    aaptOptions {
        noCompress += "tflite"
    }
}

flutter {
    source = "../.."
}

dependencies {
    // ML Kit Text Recognition — on-device, sin internet (Latin incluido por defecto)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Kotlin coroutines — para procesamiento async en los servicios
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Core KTX — extensions de Kotlin para Android
    implementation("androidx.core:core-ktx:1.12.0")

    // TFLite runtime
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    // Support library — utilidades para cargar modelos desde assets
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}