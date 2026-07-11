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
        versionCode = 96

        versionName = "1.5.8"
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

/**
 * 自定义 APK 输出文件名
 *
 * AGP 9.0+ 不再支持旧的 applicationVariants.all API，改用 androidComponents.onVariants 钩子
 * 在 assemble 任务完成后（doLast）重命名 APK 文件
 *
 * 输出格式：L7音频工具-{versionName}-{versionCode}-{构建类型}.apk
 * 示例：L7音频工具-1.4.2-42-调试.apk、L7音频工具-1.4.2-42-正式.apk
 */
androidComponents {
    onVariants { variant ->
        afterEvaluate {
            // 获取对应的 assemble 任务（如 assembleDebug、assembleRelease）
            val capitalizedVariantName = variant.name.replaceFirstChar { it.uppercase() }
            tasks.named("assemble${capitalizedVariantName}") {
                doLast {
                    // 获取 APK 输出目录（通过 Artifacts API）
                    val apkFolder = Paths.get(
                        variant.artifacts.get(
                            com.android.build.api.artifact.SingleArtifact.APK
                        ).get().toString()
                    )

                    // 构建类型映射：debug->调试，release->正式
                    //val buildTypeName = when (variant.buildType) {
                    //    "debug" -> "调试"
                   //     "release" -> "正式"
                    //    else -> variant.buildType
                    //}

		    val buildTypeName = variant.buildType
                    // 从 defaultConfig 读取版本信息
                    val appName = "L7音频工具"
                    val versionName = project.android.defaultConfig.versionName.orEmpty()
                    val versionCode = project.android.defaultConfig.versionCode ?: 0

                    // 原始文件名（AGP 默认命名）和新文件名
                    val originalApkName = "app-${variant.buildType}.apk"
                    val originalApkPath = apkFolder.resolve(originalApkName)
                    val newApkName = "${appName}-${versionName}-${versionCode}-${buildTypeName}.apk"
                    val newApkPath = apkFolder.resolve(newApkName)

                    // 重命名 APK 文件
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
    implementation(libs.media3.common)

    // Media 兼容库（MediaStyle 通知样式）
    implementation(libs.media)

    // JSON 解析
    implementation(libs.gson)

    // Lifecycle 组件（ViewModel / LiveData）
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    // LocalBroadcastManager（本地广播，替代 startService IPC）
    implementation(libs.localbroadcastmanager)
}