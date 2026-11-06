# LocationMockPro ProGuard / R8 Release Protection Rules

-keepclassmembers class * {
    native <methods>;
}

# 1. 保护 LSPosed / Xposed Hook 核心入口与反射类（严禁内联剥离或混淆）
-keep class com.antigravity.geospoofer.hook.** { *; }
-keepclassmembers class com.antigravity.geospoofer.hook.** { *; }
-keepnames class com.antigravity.geospoofer.hook.XposedEntry { *; }
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }

# 2. 保护跨进程 ContentProvider 状态共享类与前台服务入口（允许 core 内部算法类被 R8 深度混淆）
-keep class com.antigravity.geospoofer.provider.LocationStateProvider { *; }
-keep class com.antigravity.geospoofer.service.MockLocationService { *; }

# 3. 保护 WebView JavascriptInterface 桥接方法
-keepclassmembers class com.antigravity.geospoofer.ui.AndroidMapBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# 4. 保留反射与泛型签名及注解
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepattributes JavascriptInterface
-dontwarn kotlin.**
