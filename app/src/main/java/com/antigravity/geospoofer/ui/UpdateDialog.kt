package com.antigravity.geospoofer.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.core.RootHelper
import com.antigravity.geospoofer.core.UpdateInfo
import com.antigravity.geospoofer.core.UpdateInstallResult
import com.antigravity.geospoofer.core.UpdateManager
import com.antigravity.geospoofer.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class UpdateDialogStep {
    INFO,
    DOWNLOADING,
    INSTALLING,
    SUCCESS,
    ERROR
}

/**
 * 🚀 极客座舱风格 — 应用全自动更新与静默升级弹窗
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onRestartApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var step by remember { mutableStateOf(UpdateDialogStep.INFO) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(updateInfo.fileSize) }
    var errorMessage by remember { mutableStateOf("") }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var isSilentSuccess by remember { mutableStateOf(false) }

    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val isRooted = remember { RootHelper.isDeviceRooted() }

    val startDownloadAndInstall: () -> Unit = {
        step = UpdateDialogStep.DOWNLOADING
        downloadProgress = 0f
        errorMessage = ""

        downloadJob = coroutineScope.launch {
            val result = UpdateManager.downloadApk(
                context = context,
                rawDownloadUrl = updateInfo.downloadUrl
            ) { progress, downloaded, total ->
                downloadProgress = progress
                downloadedBytes = downloaded
                if (total > 0) totalBytes = total
            }

            result.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                step = UpdateDialogStep.INSTALLING

                val installResult = UpdateManager.installApk(context, apkFile)
                when (installResult) {
                    is UpdateInstallResult.SilentSuccess -> {
                        isSilentSuccess = true
                        step = UpdateDialogStep.SUCCESS
                    }
                    is UpdateInstallResult.IntentLaunched -> {
                        // 系统安装器已弹出，关闭更新弹窗
                        onDismiss()
                    }
                    is UpdateInstallResult.Failed -> {
                        errorMessage = installResult.error
                        step = UpdateDialogStep.ERROR
                    }
                }
            }.onFailure { ex ->
                errorMessage = ex.message ?: "下载过程中网络异常"
                step = UpdateDialogStep.ERROR
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (step != UpdateDialogStep.DOWNLOADING && step != UpdateDialogStep.INSTALLING) {
                onDismiss()
            }
        },
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TechBlue.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, TechCyanLight.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = TechCyanLight,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "发现新版本 ${updateInfo.versionName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "LocationMockPro 自动更新",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 标签栏：版本与模式提示
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (updateInfo.fileSize > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkCardSubtle,
                            border = BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2f MB", updateInfo.fileSize / 1024.0 / 1024.0),
                                fontSize = 11.sp,
                                color = TechCyanLight,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (isRooted) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TechEmeraldDark.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, TechEmerald.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = TechEmeraldLight, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "Root 静默秒级更新",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TechEmeraldLight
                                )
                            }
                        }
                    }
                }

                when (step) {
                    UpdateDialogStep.INFO -> {
                        // 更新日志卡片
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = DarkCardSubtle,
                            border = BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "更新内容：",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = updateInfo.changelog,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    UpdateDialogStep.DOWNLOADING -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "正在下载安装包...",
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TechCyanLight
                                )
                            }

                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = TechCyanLight,
                                trackColor = DarkCardBorder
                            )

                            if (totalBytes > 0) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.2f MB / %.2f MB",
                                        downloadedBytes / 1024.0 / 1024.0,
                                        totalBytes / 1024.0 / 1024.0
                                    ),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextTertiary,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }

                    UpdateDialogStep.INSTALLING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = TechCyanLight,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = if (isRooted) "正在执行 Root 静默覆盖升级..." else "正在准备安装...",
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    UpdateDialogStep.SUCCESS -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TechEmeraldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "升级完成！",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TechEmeraldLight
                                )
                            }
                            Text(
                                text = "应用已无感热替换至最新版本，可立即重启应用加载最新功能。",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    UpdateDialogStep.ERROR -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = TechRose,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "升级遇到问题",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TechRose
                                )
                            }
                            Text(
                                text = errorMessage,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                UpdateDialogStep.INFO -> {
                    Button(
                        onClick = startDownloadAndInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("立即更新", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                UpdateDialogStep.DOWNLOADING -> {
                    // 下载中无需确认按钮
                }
                UpdateDialogStep.INSTALLING -> {
                    // 安装中无需按钮
                }
                UpdateDialogStep.SUCCESS -> {
                    Button(
                        onClick = {
                            onDismiss()
                            onRestartApp()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TechEmerald),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("完成", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                UpdateDialogStep.ERROR -> {
                    Button(
                        onClick = startDownloadAndInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("重试", color = Color.White)
                    }
                }
            }
        },
        dismissButton = {
            when (step) {
                UpdateDialogStep.INFO -> {
                    if (!updateInfo.forceUpdate) {
                        TextButton(onClick = onDismiss) {
                            Text("稍后再说", color = TextSecondary)
                        }
                    }
                }
                UpdateDialogStep.DOWNLOADING -> {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        step = UpdateDialogStep.INFO
                    }) {
                        Text("取消下载", color = TextSecondary)
                    }
                }
                UpdateDialogStep.ERROR -> {
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = TextSecondary)
                    }
                }
                else -> {}
            }
        }
    )
}
