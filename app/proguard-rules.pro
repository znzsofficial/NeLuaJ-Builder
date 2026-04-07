# ============================================================
# ProGuard rules for NeLuaJ+ Builder
# ============================================================

# ---- 保留行号信息，便于调试崩溃堆栈 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- 保留注解和签名信息 ----
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ============================================================
# LuaJ 库 - 大量使用反射和动态方法调用，必须完整保留
# ============================================================
-keep class org.luaj.** { *; }
-keepclassmembers class org.luaj.** { *; }

# ============================================================
# REAndroid APKEditor 库 - 包含 APK 操作和资源加载
# ============================================================
-keep class com.reandroid.** { *; }
-keepclassmembers class com.reandroid.** { *; }

# ============================================================
# 应用中使用反射的类
# ============================================================

# CrashHandler - 通过反射读取 Build 字段收集设备信息
-keep class com.nekolaska.CrashHandler { *; }

# PermissionHelper - 通过反射枚举 Manifest.permission 常量
-keep class com.nekolaska.utils.PermissionHelper { *; }

# Agent - 使用动态代理 (Proxy.newProxyInstance)
-keep class com.nekolaska.utils.Agent { *; }
-keep interface com.nekolaska.** { *; }

# ConfigDialog - 通过反射访问 Dialog 内部字段
-keep class com.nekolaska.dialog.ConfigDialog { *; }

# ============================================================
# Data classes - 保留构造器和属性
# ============================================================
-keep class com.nekolaska.data.** { *; }
-keep class com.f3401pal.** { *; }

# ============================================================
# OkHttp (Coil 内部依赖)
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================
# Coil 图片加载库
# ============================================================
-keep class coil3.** { *; }

# ============================================================
# Kotlin 相关
# ============================================================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
