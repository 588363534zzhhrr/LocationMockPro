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

# 2. 保护跨进程 ContentProvider 状态共享类与实体
-keep class com.antigravity.geospoofer.provider.** { *; }
-keepclassmembers class com.antigravity.geospoofer.provider.** { *; }

# 3. 保护算法动力学与模拟状态实体类及服务
-keep class com.antigravity.geospoofer.core.** { *; }
-keepclassmembers class com.antigravity.geospoofer.core.** { *; }
-keep class com.antigravity.geospoofer.service.** { *; }
-keepclassmembers class com.antigravity.geospoofer.service.** { *; }

# 4. 保留反射与泛型签名及注解
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepattributes JavascriptInterface
-dontwarn kotlin.**
