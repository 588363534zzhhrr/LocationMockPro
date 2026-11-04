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
     * 通过 su 执行命令（具备双向流并发读取与防管道死锁保护，支持超时熔断）
     */
    fun executeRootCommand(command: String, timeoutMs: Long = 8000L): CommandResult {
        var process: java.lang.Process? = null
        var os: DataOutputStream? = null

        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\nexit\n")
            os.flush()

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            // 并发读取标准输出与错误流，杜绝底层缓冲区写满导致父子进程互相等待的死锁悲剧
            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { r ->
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            if (stdoutBuilder.isNotEmpty()) stdoutBuilder.append("\n")
                            stdoutBuilder.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { r ->
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            if (stderrBuilder.isNotEmpty()) stderrBuilder.append("\n")
                            stderrBuilder.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            outThread.start()
            errThread.start()

            outThread.join(timeoutMs)
            errThread.join(timeoutMs)

            val exitCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (process.waitFor(1500L, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    process.exitValue()
                } else {
                    process.destroyForcibly()
                    -1
                }
            } else {
                process.waitFor()
            }

            CommandResult(exitCode == 0, stdoutBuilder.toString(), stderrBuilder.toString())
        } catch (e: Exception) {
            CommandResult(false, "", e.message ?: "Execution failed")
        } finally {
            try { os?.close() } catch (_: Exception) {}
            process?.destroy()
        }
    }

    /**
     * 协程异步调用 Root 执行命令
     */
    suspend fun executeRootCommandAsync(command: String, timeoutMs: Long = 8000L): CommandResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            executeRootCommand(command, timeoutMs)
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

    suspend fun grantMockLocationPermissionViaRootAsync(packageName: String): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            grantMockLocationPermissionViaRoot(packageName)
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
