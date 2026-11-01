# LocationMockPro ProGuard / R8 Release Protection Rules

-keepclassmembers class * {
    native <methods>;
}

# 1. 保护 LSPosed / Xposed Hook 核心入口与反射类
-keep class com.antigravity.geospoofer.hook.** { *; }
-keepclassmembers class com.antigravity.geospoofer.hook.** { *; }
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }

# 2. 保护跨进程 ContentProvider 状态共享类与实体
-keep class com.antigravity.geospoofer.provider.** { *; }
-keepclassmembers class com.antigravity.geospoofer.provider.** { *; }

# 3. 保护算法动力学与模拟状态实体类
-keep class com.antigravity.geospoofer.core.** { *; }
-keepclassmembers class com.antigravity.geospoofer.core.** { *; }

# 4. 保留反射与泛型签名
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepattributes JavascriptInterface
