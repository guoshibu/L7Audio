# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留行号信息，便于调试崩溃
-keepattributes SourceFile,LineNumberTable

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

# 保留应用的关键类
-keep class com.aug32.l7audio.** {
    *;
}

# 保留基本组件
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留R类
-keepclassmembers class **.R$* {
    public static <fields>;
}
