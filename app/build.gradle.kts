plugins { id("com.android.application") }

android {
    namespace = "com.cosmerearchives.audiobookresearcher"
    compileSdk = 35

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

dependencies { }
