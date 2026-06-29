# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留行号信息，便于调试崩溃
-keepattributes SourceFile,LineNumberTable

# 保留泛型信息（用于 Gson 反射）
-keepattributes Signature
-keepattributes *Annotation*

# Remove log statements in release build
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove AppLog statements in release build
-assumenosideeffects class com.aug32.l7audio.AppLog {
    public static void v(...);
    public static void i(...);
    public static void w(...);
    public static void d(...);
    public static void e(...);
}

# 保留应用的所有类、方法和字段
-keep class com.aug32.l7audio.** {
    *;
}

# 保留应用的所有内部包类
-keep class com.aug32.l7audio.audio.** {
    *;
}

-keep class com.aug32.l7audio.service.** {
    *;
}

# 保留基本组件（确保系统能找到）
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 保留 WorkManager Worker 类
-keep public class * extends androidx.work.Worker
-keep public class * extends androidx.work.ListenableWorker
-keep class androidx.work.** { *; }

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留R类
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Gson 序列化需要保留的规则
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# 保留 Media3 相关类（音频播放核心）
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# 保留 Material Design 组件
-keep class com.google.android.material.** { *; }

# 保留 androidx 相关类
-keep class androidx.appcompat.** { *; }
-keep class androidx.recyclerview.** { *; }
-keep class androidx.constraintlayout.** { *; }
-keep class androidx.cardview.** { *; }
-keep class androidx.activity.** { *; }

# 保留自定义 View
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
