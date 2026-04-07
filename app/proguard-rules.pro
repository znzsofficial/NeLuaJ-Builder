# ============================================================
# ProGuard rules for NeLuaJ+ Builder
# ============================================================

# ---- 保留行号信息，便于调试崩溃堆栈 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- 保留注解和签名信息 ----
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---- 防止 R8 破坏 AndroidX / AppCompat 接口实现 ----
# AppCompatDelegateImpl 实现 LayoutInflater.Factory2，方法名不能被混淆
-keep class androidx.appcompat.app.AppCompatDelegateImpl { *; }
-keep class * implements android.view.LayoutInflater$Factory2 {
    public android.view.View onCreateView(...);
}
-keep class * implements android.view.LayoutInflater$Factory {
    public android.view.View onCreateView(...);
}

# ============================================================
# 【关键】Application 入口类 - 在 Manifest 中通过 android:name 引用
# ============================================================
-keep class com.nekolaska.App { *; }

# ============================================================
# 【关键】Activity - 在 Manifest 中声明
# ============================================================
-keep class com.nekolaska.MainActivity { *; }

# ============================================================
# 【关键】Fragment 类 - Android 框架通过反射（无参构造函数）重建
# 配置变更（旋转屏幕等）时系统会用 Class.forName + newInstance 恢复
# ============================================================
-keep class com.nekolaska.fragments.** { *; }
-keep class com.nekolaska.base.ProviderFragment { *; }

# ============================================================
# 【关键】自定义 View - 在 XML 布局中通过全限定类名引用
# R8 重命名后 LayoutInflater 找不到类会导致白屏/崩溃
# ============================================================
# dialog_select.xml 中引用: <com.f3401pal.CheckableTreeView>
# item_checkable_text.xml 中引用: <com.f3401pal.CheckBoxEx>
-keep class com.f3401pal.CheckableTreeView { *; }
-keep class com.f3401pal.CheckBoxEx { *; }
-keep class com.f3401pal.ExpandToggleButton { *; }
-keep class com.f3401pal.TreeNode { *; }
-keep class com.f3401pal.CheckableTree { *; }
# 确保自定义 View 的构造函数签名被保留（LayoutInflater 需要）
-keepclassmembers class com.f3401pal.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ============================================================
# 【关键】ViewBinding / DataBinding 生成类
# inflate() 方法内部通过 ID 查找 View，不能被移除
# ============================================================
-keep class com.nekolaska.Builder.databinding.** { *; }

# ============================================================
# 【关键】@Parcelize 数据类 - CREATOR 字段通过反射访问
# ============================================================
-keep class com.nekolaska.data.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

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
# libs/ 目录中的 JAR 依赖
# ============================================================
# apksig.jar - APK 签名
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# smali.jar - Smali/Baksmali 反编译工具
-keep class org.jf.** { *; }
-keep class com.android.tools.smali.** { *; }
-dontwarn org.jf.**
-dontwarn com.android.tools.smali.**

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

# SingletonHolder - 泛型反射模式
-keep class com.nekolaska.utils.SingletonHolder { *; }
-keep class com.nekolaska.utils.Toaster { *; }

# ============================================================
# FastScroll 库 - 自定义 View
# ============================================================
-keep class me.zhanghai.android.fastscroll.** { *; }

# ============================================================
# OkHttp (Coil 内部依赖)
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================
# Coil 3.x 图片加载库
# ============================================================
-keep class coil3.** { *; }
# Coil 3 使用 ServiceLoader 发现组件
-keepnames class coil3.** implements coil3.ComponentRegistry$Key

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

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================
# 通用安全规则 - 防止 R8 移除枚举的 values()/valueOf()
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# 抑制 LuaJ 核心 JAR 引用的运行时类缺失警告
# 这些类仅在 NeLuaJ+ 运行时存在，Builder 项目中不需要
# ============================================================
-dontwarn b.a.a.a.**
-dontwarn coil.**
-dontwarn com.androlua.**
-dontwarn com.android.cglib.**
-dontwarn github.daisukiKaffuChino.**
-dontwarn javax.annotation.**
