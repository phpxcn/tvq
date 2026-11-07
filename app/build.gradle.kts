plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvlock.app"
    compileSdk = 34

    defaultConfig {
        // 伪装 com.android.* 前缀：避开 JMGO 后台清理守护（com.sparkle.hippo）对
        // 后台进程的强杀——它按进程名正则 com.android.* 豁免「系统组件」。
        // namespace（代码包名）不变，仅安装标识变化
        applicationId = "com.android.tvlock"
        minSdk = 21          // 覆盖老款国产安卓电视
        // 27：保留 legacy 外置存储直写（/sdcard/TVLock）+ 解除 Android 9+
        // 「后台应用不能弹悬浮窗」限制（锁定防逃遮罩在 onPause 零空窗弹出的关键）
        targetSdk = 27
        // CI 打 tag 时注入（-PappVersionName/-PappVersionCode，如 v1.0 -> 1.0/10000）；
        // 本地默认 dev/99999：高于任何发版号，测试包可直接覆盖安装
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 99999
        versionName = (project.findProperty("appVersionName") as String?) ?: "dev"
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
