plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") version "2.0.21" }

android {
    namespace = "com.cosmerearchives.audiobookresearcher"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.cosmerearchives.audiobookresearcher"
        minSdk = 26
        targetSdk = 35
        versionCode = (providers.gradleProperty("releaseVersionCode").orNull ?: "1").toInt()
        versionName = providers.gradleProperty("releaseVersionName").orNull ?: "0.1.0"
    }

    signingConfigs {
        create("production") {
            val storePath = System.getenv("ANDROID_KEYSTORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("production")
        }
    }
}

dependencies { implementation(files("libs/sherpa-onnx.aar")) }
