package com.antigravity.geospoofer.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Root 权限与系统级特权管理助手
 */
object RootHelper {

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su"
    )

    /**
     * 检测设备是否存在 su 二进制文件
     */
    fun isDeviceRooted(): Boolean {
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * 校验当前应用是否能成功请求并获取 Root 权限
     */
    fun checkRootPermission(): Boolean {
        return executeRootCommand("id").success
    }

    data class CommandResult(val success: Boolean, val output: String, val error: String)

    /**
     * 通过 su 执行命令
     */
    fun executeRootCommand(command: String): CommandResult {
        var process: java.lang.Process? = null
        var os: DataOutputStream? = null
        var reader: BufferedReader? = null
        var errorReader: BufferedReader? = null

        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\nexit\n")
            os.flush()

            val exitCode = process.waitFor()
            reader = BufferedReader(InputStreamReader(process.inputStream))
            errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = reader.readText()
            val error = errorReader.readText()

            CommandResult(exitCode == 0, output, error)
        } catch (e: Exception) {
            CommandResult(false, "", e.message ?: "Execution failed")
        } finally {
            try { os?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { errorReader?.close() } catch (_: Exception) {}
            process?.destroy()
        }
    }

    /**
     * 利用 Root 权限自动授予 MOCK_LOCATION 权限
     * 免去在开发者选项中手动寻找并勾选的步骤
     */
    fun grantMockLocationPermissionViaRoot(packageName: String): Boolean {
        val cmd = "appops set $packageName android:mock_location allow"
        val result = executeRootCommand(cmd)
        return result.success
    }

    /**
     * 检查是否具备系统级 INSTALL_LOCATION_PROVIDER 权限
     * （当应用被转换为 /system/priv-app 或系统集成时具备）
     */
    fun hasSystemLocationProviderPermission(context: Context): Boolean {
        return context.checkPermission(
            "android.permission.INSTALL_LOCATION_PROVIDER",
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 生成将本 App 挂载为系统特权 App (Priv-app) 的 Magisk/KernelSU 模块说明命令
     */
    fun getPrivAppInstallScript(context: Context): String {
        val apkPath = context.packageCodePath
        val pkgName = context.packageName
        return """
            # 挂载为系统 Priv-app 命令示例（需要解锁 system 分区或使用 Magisk Magic Mount）
            mount -o remount,rw /system
            mkdir -p /system/priv-app/$pkgName
            cp $apkPath /system/priv-app/$pkgName/$pkgName.apk
            chmod 644 /system/priv-app/$pkgName/$pkgName.apk
            mount -o remount,ro /system
        """.trimIndent()
    }
}
