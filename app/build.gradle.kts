plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.aug32.l7audio"
    compileSdk = 36

    buildFeatures {
        buildConfig = true  // 启用 BuildConfig 类生成（release 模式用 BuildConfig.DEBUG 关闭日志）
    }

    defaultConfig {
        applicationId = "com.aug32.l7audio"
        minSdk = 30
        targetSdk = 30
        versionCode = 42
        versionName = "1.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "password123"
            keyAlias = "l7audio"
            keyPassword = "password123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // 关闭混淆，确保应用能运行
            isShrinkResources = false  // 资源压缩需要混淆开启
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"]
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        checkAllWarnings = true
        checkReleaseBuilds = true
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }

}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)

    // Media3库
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.session)  // MediaSession

    // Media 兼容库（MediaStyle 通知样式）
    implementation(libs.media)

    // JSON 解析
    implementation(libs.gson)

    // WorkManager 定时任务（用于保活）
    implementation(libs.workRuntime)

    // Lifecycle 组件（ViewModel / LiveData）
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.extJunit)
    androidTestImplementation(libs.espressoCore)
}