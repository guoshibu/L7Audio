import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

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

androidComponents {
    onVariants { variant ->
        afterEvaluate {
            val capitalizedVariantName = variant.name.replaceFirstChar { it.uppercase() }
            tasks.named("assemble${capitalizedVariantName}") {
                doLast {
                    val apkFolder = Paths.get(
                        variant.artifacts.get(
                            com.android.build.api.artifact.SingleArtifact.APK
                        ).get().toString()
                    )
                    val buildTypeName = when (variant.buildType) {
                        "debug" -> "调试"
                        "release" -> "正式"
                        else -> variant.buildType
                    }
                    val appName = "L7音频工具"
                    val versionName = project.android.defaultConfig.versionName.orEmpty()
                    val versionCode = project.android.defaultConfig.versionCode ?: 0
                    val originalApkName = "app-${variant.buildType}.apk"
                    val originalApkPath = apkFolder.resolve(originalApkName)
                    val newApkName = "${appName}-${versionName}-${versionCode}-${buildTypeName}.apk"
                    val newApkPath = apkFolder.resolve(newApkName)
                    if (Files.exists(originalApkPath)) {
                        Files.move(originalApkPath, newApkPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
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