plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.depressometer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.depressometer"
        minSdk = 21
        targetSdk = 36
        versionCode = 13
        versionName = "1.9.3"

        // AdMob ids come from gradle.properties (admobAppId / admobRewardedUnit).
        // Required on purpose: no test-id fallback, so a build can never silently ship test ads.
        val admobAppId = (project.findProperty("admobAppId") as String?)
            ?: error("Set admobAppId in gradle.properties (or pass -PadmobAppId=...)")
        val admobRewardedUnit = (project.findProperty("admobRewardedUnit") as String?)
            ?: error("Set admobRewardedUnit in gradle.properties (or pass -PadmobRewardedUnit=...)")
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_REWARDED_UNIT", "\"$admobRewardedUnit\"")
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null && file(path).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // AdMob (rewarded ads for the points shop)
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // CameraX returns ListenableFuture; the Guava stub can vanish from the
    // compile classpath once AdMob is added, so pin the real Guava explicitly.
    implementation("com.google.guava:guava:32.1.3-android")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // ML Kit on-device face detection
    implementation("com.google.mlkit:face-detection:16.1.7")
}
