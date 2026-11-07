plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvlock.watchdog"
    compileSdk = 34

    defaultConfig {
        // 伪装 com.android.* 前缀避开 hippo 强杀（同主 App 策略）
        applicationId = "com.android.tvlockwd"
        minSdk = 21
        targetSdk = 27
        // 与主 App 同步的版本号（CI 注入，本地默认 1.0）
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
