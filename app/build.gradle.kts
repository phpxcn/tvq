plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvlock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvlock.app"
        minSdk = 21          // 覆盖老款国产安卓电视
        targetSdk = 29       // 保留 legacy 外置存储直写能力（/sdcard/TVLock）
        versionCode = 1
        versionName = "1.0"
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

dependencies {
    // 仅三个依赖，全部纯本地运行
    implementation("org.nanohttpd:nanohttpd:2.3.1")   // 手机控制面板 HTTP 服务器
    implementation("com.google.zxing:core:3.5.3")     // 电视屏二维码
    testImplementation("junit:junit:4.13.2")
}
