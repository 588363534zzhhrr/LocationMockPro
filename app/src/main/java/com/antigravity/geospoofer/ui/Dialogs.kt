package com.antigravity.geospoofer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.PresetRoute
import com.antigravity.geospoofer.core.AntiDetectionAuditHelper
import com.antigravity.geospoofer.core.CoordinateUtils
import com.antigravity.geospoofer.core.FavoritePlacesHelper
import com.antigravity.geospoofer.core.RouteStorageHelper
import com.antigravity.geospoofer.provider.LocationStateProvider
import com.antigravity.geospoofer.ui.theme.*

/**
 * 六维防检测体检报告弹窗
 */
@Composable
fun AuditDialog(
    enableJitter: Boolean,
    enableRandomStops: Boolean,
    enableFatigueCurve: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val auditReport = remember(enableJitter, enableRandomStops, enableFatigueCurve) {
        AntiDetectionAuditHelper.performAudit(
            context = context,
            isJitterActive = enableJitter,
            isStopsActive = enableRandomStops,
            isFatigueActive = enableFatigueCurve
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TechEmerald.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TechEmeraldLight, modifier = Modifier.size(18.dp))
                    }
                }
                Text("六维防检测体检报告", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 安全评分横幅
                Surface(
                    color = Color(auditReport.gradeColorHex).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(auditReport.gradeColorHex).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(auditReport.gradeTitle, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("基于底层 Hook 与 6 维行为特征全面审计", fontSize = 11.sp, color = TextSecondary)
                        }
                        Text(
                            "${auditReport.safetyScore}分",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp,
                            color = Color(auditReport.gradeColorHex),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 审计子项列表
                auditReport.items.forEach { item ->
                    Surface(
                        color = DarkInputBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (item.isPassed) TechEmerald.copy(alpha = 0.15f) else TechAmber.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        if (item.isPassed) "通过" else "建议优化",
                                        color = if (item.isPassed) TechEmeraldLight else TechAmberLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("状态: ${item.statusText}", fontSize = 11.sp, color = TechCyanLight)
                            Text(item.suggestion, fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }

                // LSPosed 免杀代码与指导说明
                Surface(
                    color = DarkCardSubtle,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("LSPosed 免杀 Hook 规则", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TechBlueLight)
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("LSPosed Hook", AntiDetectionAuditHelper.getLsposedHookGuideSnippet())
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "已复制 LSPosed 免杀 Hook 源码到剪贴板！", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("复制源码", fontSize = 10.sp)
                            }
                        }
                        Text(
                            "对 Keep / 钉钉 等风控严苛的 App，可通过 LSPosed 模块直接拦截 Location.isFromMockProvider()，使其永久返回 false 达到 100% 隐形！",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
            ) {
                Text("完成体检", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Wi-Fi 考勤与移动基站伪装弹窗
 */
@Composable
fun RadioSpoofDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var wifiMockEnabled by remember { mutableStateOf(LocationStateProvider.wifiMockEnabled) }
    var wifiSsidInput by remember { mutableStateOf(LocationStateProvider.wifiSsid) }
    var wifiBssidInput by remember { mutableStateOf(LocationStateProvider.wifiBssid) }
    var wifiRssiInput by remember { mutableStateOf(LocationStateProvider.wifiRssi.toString()) }

    var cellMockEnabled by remember { mutableStateOf(LocationStateProvider.cellMockEnabled) }
    var cellMccInput by remember { mutableStateOf(LocationStateProvider.cellMcc.toString()) }
    var cellMncInput by remember { mutableStateOf(LocationStateProvider.cellMnc.toString()) }
    var cellLacInput by remember { mutableStateOf(LocationStateProvider.cellLac.toString()) }
    var cellCidInput by remember { mutableStateOf(LocationStateProvider.cellCid.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TechCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = TechCyanLight, modifier = Modifier.size(18.dp))
                    }
                }
                Text("Wi-Fi 考勤与基站伪装", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("绕过钉钉、企业微信对办公室 Wi-Fi BSSID 与移动基站 LAC/CID 的硬件比对。", fontSize = 11.sp, color = TextSecondary)

                // 1. Wi-Fi
                Surface(
                    color = DarkInputBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Wi-Fi 考勤伪装", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                            Switch(checked = wifiMockEnabled, onCheckedChange = { wifiMockEnabled = it })
                        }

                        if (wifiMockEnabled) {
                            OutlinedTextField(
                                value = wifiSsidInput,
                                onValueChange = { wifiSsidInput = it },
                                label = { Text("Wi-Fi 名称 (SSID)") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = wifiBssidInput,
                                onValueChange = { wifiBssidInput = it },
                                label = { Text("路由 MAC (BSSID)") },
                                placeholder = { Text("00:1a:2b:3c:4d:5e") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = wifiRssiInput,
                                onValueChange = { wifiRssiInput = it },
                                label = { Text("信号强度 RSSI (dBm)") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        wifiSsidInput = "Corporate-Office-5G"
                                        wifiBssidInput = "00:1a:2b:3c:4d:5e"
                                        wifiRssiInput = "-48"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("企业 5G 预设", fontSize = 10.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        wifiSsidInput = "Tech-Park-FreeWiFi"
                                        wifiBssidInput = "74:05:a5:88:21:bb"
                                        wifiRssiInput = "-55"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("园区 Wi-Fi 预设", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // 2. 基站
                Surface(
                    color = DarkInputBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("蜂窝基站伪装", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                            Switch(checked = cellMockEnabled, onCheckedChange = { cellMockEnabled = it })
                        }

                        if (cellMockEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value = cellMccInput, onValueChange = { cellMccInput = it }, label = { Text("MCC") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = cellMncInput, onValueChange = { cellMncInput = it }, label = { Text("MNC") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value = cellLacInput, onValueChange = { cellLacInput = it }, label = { Text("LAC") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = cellCidInput, onValueChange = { cellCidInput = it }, label = { Text("CID") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        cellMccInput = "460"
                                        cellMncInput = "0"
                                        cellLacInput = "41021"
                                        cellCidInput = "1892341"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("中国移动基站", fontSize = 10.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        cellMccInput = "460"
                                        cellMncInput = "11"
                                        cellLacInput = "32188"
                                        cellCidInput = "9241852"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("中国电信基站", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rssiVal = wifiRssiInput.toIntOrNull() ?: -52
                    val mccVal = cellMccInput.toIntOrNull() ?: 460
                    val mncVal = cellMncInput.toIntOrNull() ?: 0
                    val lacVal = cellLacInput.toIntOrNull() ?: 41021
                    val cidVal = cellCidInput.toIntOrNull() ?: 1892341

                    LocationStateProvider.updateWifiSpoof(wifiMockEnabled, wifiSsidInput.trim(), wifiBssidInput.trim(), rssiVal)
                    LocationStateProvider.updateCellSpoof(cellMockEnabled, mccVal, mncVal, lacVal, cidVal)
                    Toast.makeText(context, "Wi-Fi 与基站伪装配置已保存并实时生效！", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
            ) {
                Text("保存生效", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 高德地图防跳排查指南弹窗
 */
@Composable
fun AntiJumpGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TechAmber.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = TechAmberLight, modifier = Modifier.size(18.dp))
                    }
                }
                Text("高德防跳排查指南", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0xFF2A1C14),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TechAmberDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("为什么位置会偶发跳回真实地点？", fontWeight = FontWeight.Bold, color = TechAmberLight, fontSize = 12.sp)
                        Text(
                            "高德采用融合定位。非 Root 手机上，系统只允许模拟 GPS，而高德后台会扫描周围真实 Wi-Fi MAC 与基站。收到 Wi-Fi 云端定位时蓝点拉回真实；收到模拟 GPS 时又跳回虚拟，导致两点来回拉扯！",
                            fontSize = 11.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text("免 Root 防跳 4 步规范", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)

                listOf(
                    Pair("1. 彻底关闭【Wi-Fi扫描】与【蓝牙扫描】", "即便关掉 Wi-Fi，系统默认依然在后台扫描周围路由。点击下方按钮前往系统设置彻底关闭。"),
                    Pair("2. 断开 Wi-Fi，改用蜂窝移动数据流量", "路由器 MAC 地址被高德服务器收录过，连着 Wi-Fi 会直接暴露真实位置。断开 Wi-Fi 即可切断该数据源。"),
                    Pair("3. 启动顺序：先启模拟，再开高德", "测试前在多任务彻底划掉高德后台。在 LocationMockPro 中先启动单点或路线模拟，最后再打开高德地图。"),
                    Pair("4. 保持开启【4Hz 高频防跳脉冲】", "心跳提速至 250ms (每秒4次)，以极密集的模拟信号压制任何突发真实定位。")
                ).forEach { (title, desc) ->
                    Surface(
                        color = DarkInputBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, fontWeight = FontWeight.Bold, color = TechEmeraldLight, fontSize = 11.sp)
                            Text(desc, fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechAmber)
            ) {
                Text("前往系统位置设置", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("已了解")
            }
        }
    )
}

/**
 * 收藏打卡地点弹窗
 */
@Composable
fun AddFavoriteDialog(
    initialLat: Double,
    initialLon: Double,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("常用地点 (${"%.4f".format(initialLat)}, ${"%.4f".format(initialLon)})") }
    var tagInput by remember { mutableStateOf("打卡") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = TechAmberLight)
                Text("收藏为常用打卡地点", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("坐标: ${"%.6f".format(initialLat)}, ${"%.6f".format(initialLon)}", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("地点名称 (如: 公司工位/运动场)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("标签分类 (如: 打卡/公司/家)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameInput.isNotBlank()) {
                        FavoritePlacesHelper.addFavorite(context, nameInput.trim(), initialLat, initialLon, tagInput.trim().ifEmpty { "常去" })
                        onAdded()
                        onDismiss()
                        Toast.makeText(context, "已收藏地点: $nameInput", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
            ) {
                Text("确认收藏", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 保存路线弹窗
 */
@Composable
fun SaveRouteDialog(
    waypoints: List<CoordinateUtils.LatLng>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("自定义路线 (${waypoints.size} 点)") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = TechCyanLight)
                Text("保存当前规划路线", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前规划共包含 ${waypoints.size} 个途经路点。", fontSize = 12.sp, color = TextSecondary)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("路线名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameInput.isNotBlank()) {
                        RouteStorageHelper.saveRoute(context, nameInput.trim(), waypoints)
                        onSaved()
                        onDismiss()
                        Toast.makeText(context, "路线已保存到收藏夹", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
            ) {
                Text("确认保存", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * GPX 导入弹窗
 */
@Composable
fun GpxImportDialog(
    presetRoutes: List<PresetRoute>,
    onDismiss: () -> Unit,
    onImported: (List<CoordinateUtils.LatLng>) -> Unit
) {
    val context = LocalContext.current
    var gpxXmlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FileOpen, contentDescription = null, tint = TechBlueLight)
                Text("导入 GPX 轨迹", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("可快速载入经典路线，或在下方粘贴 <gpx> 内容：", fontSize = 11.sp, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val sample = presetRoutes[0].waypoints
                            onImported(sample)
                            onDismiss()
                            Toast.makeText(context, "已载入奥森跑步轨迹", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("奥森 5km", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val sample = presetRoutes[3].waypoints
                            onImported(sample)
                            onDismiss()
                            Toast.makeText(context, "已载入西湖轨迹", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("西湖 3.8km", fontSize = 11.sp)
                    }
                }
                OutlinedTextField(
                    value = gpxXmlInput,
                    onValueChange = { gpxXmlInput = it },
                    label = { Text("在此粘贴 <gpx> XML 文本") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (gpxXmlInput.isNotBlank()) {
                        val pts = RouteStorageHelper.importFromGpx(gpxXmlInput)
                        if (pts.isNotEmpty()) {
                            onImported(pts)
                            onDismiss()
                            Toast.makeText(context, "成功导入 ${pts.size} 个轨迹点！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "未能解析到有效轨迹点", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
            ) {
                Text("解析并应用", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 智能环线生成弹窗
 */
@Composable
fun LoopGeneratorDialog(
    centerLat: Double,
    centerLon: Double,
    onDismiss: () -> Unit,
    onGenerated: (List<CoordinateUtils.LatLng>) -> Unit
) {
    val context = LocalContext.current
    var loopDistanceKmText by remember { mutableStateOf("2.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = TechPurpleLight)
                Text("智能生成闭环路线", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("以当前位置为中心，自动生成符合人体运动习惯的闭合拟真环线。", fontSize = 11.sp, color = TextSecondary)
                Text("快捷目标里程", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1.5", "2.0", "3.0", "5.0").forEach { dist ->
                        FilterChip(
                            selected = loopDistanceKmText == dist,
                            onClick = { loopDistanceKmText = dist },
                            label = { Text("${dist}km", fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = loopDistanceKmText,
                    onValueChange = { loopDistanceKmText = it },
                    label = { Text("自定义目标总里程 (km)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val km = loopDistanceKmText.toDoubleOrNull() ?: 2.0
                    val meters = km * 1000.0
                    val generated = CoordinateUtils.generateOrganicLoopRoute(
                        centerLat = centerLat,
                        centerLon = centerLon,
                        targetDistanceMeters = meters,
                        numPoints = 12,
                        irregularity = 0.22
                    )
                    onGenerated(generated)
                    onDismiss()
                    Toast.makeText(context, "已智能规划 ${"%.2f".format(km)} km 闭环路线！", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
            ) {
                Text("立即生成", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 400m 标准操场跑道校准与精细对齐弹窗
 */
@Composable
fun Track400mDialog(
    initialCenterLat: Double,
    initialCenterLon: Double,
    onDismiss: () -> Unit,
    onPreview: (List<CoordinateUtils.LatLng>) -> Unit,
    onConfirm: (List<CoordinateUtils.LatLng>, targetKm: Double) -> Unit
) {
    val context = LocalContext.current
    var centerLat by remember { mutableDoubleStateOf(initialCenterLat) }
    var centerLon by remember { mutableDoubleStateOf(initialCenterLon) }
    var orientationDeg by remember { mutableFloatStateOf(0f) }
    var selectedLane by remember { mutableIntStateOf(1) }
    var isCounterClockwise by remember { mutableStateOf(true) }
    var nudgeStepMeters by remember { mutableDoubleStateOf(5.0) }
    var targetLapsText by remember { mutableStateOf("5") }

    LaunchedEffect(centerLat, centerLon, orientationDeg, selectedLane, isCounterClockwise) {
        val pts = CoordinateUtils.generateTrack400m(
            centerLat = centerLat,
            centerLon = centerLon,
            orientationDeg = orientationDeg,
            lane = selectedLane,
            isCounterClockwise = isCounterClockwise
        )
        onPreview(pts)
    }

    val lapDistanceMeters = if (selectedLane == 1) {
        400.0
    } else {
        2.0 * 84.39 + 2.0 * Math.PI * (36.50 + (selectedLane - 1) * 1.22 + 0.20)
    }

    val laps = targetLapsText.toDoubleOrNull() ?: 5.0
    val totalEstimatedKm = (lapDistanceMeters * laps) / 1000.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SportsScore, contentDescription = null, tint = TechAmberLight)
                Text("400m 操场跑道校准与对齐", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "提示：微调参数将实时驱动地图渲染，可对照卫星图层使跑道精准重合现实操场。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                // 1. 跑道航向旋转角度
                Surface(
                    color = DarkInputBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("跑道长轴航向角", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(
                                "${orientationDeg.toInt()}° (${getOrientationDesc(orientationDeg)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechCyanLight,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Slider(
                            value = orientationDeg,
                            onValueChange = { orientationDeg = it },
                            valueRange = 0f..359f,
                            colors = SliderDefaults.colors(
                                thumbColor = TechCyanLight,
                                activeTrackColor = TechBlue,
                                inactiveTrackColor = DarkCardBorder
                            )
                        )

                        // 快捷方向预设
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Pair("正北 0°", 0f),
                                Pair("正东 90°", 90f),
                                Pair("东北 45°", 45f),
                                Pair("东南 135°", 135f)
                            ).forEach { (label, angle) ->
                                FilterChip(
                                    selected = (orientationDeg.toInt() % 180) == (angle.toInt() % 180),
                                    onClick = { orientationDeg = angle },
                                    label = { Text(label, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 细粒度微调步进按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(-15f, -5f, -1f, 1f, 5f, 15f).forEach { step ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DarkCardSubtle,
                                    border = BorderStroke(1.dp, DarkCardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            orientationDeg = ((orientationDeg + step + 360f) % 360f)
                                        }
                                ) {
                                    Text(
                                        text = if (step > 0) "+${step.toInt()}°" else "${step.toInt()}°",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 位置四向微调平移
                Surface(
                    color = DarkInputBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("中心位置四向平移", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(2.0, 5.0, 10.0).forEach { dist ->
                                    FilterChip(
                                        selected = nudgeStepMeters == dist,
                                        onClick = { nudgeStepMeters = dist },
                                        label = { Text("${dist.toInt()}m", fontSize = 9.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 四向平移按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("北移 ↑", 0f, TechBlue),
                                Triple("南移 ↓", 180f, TechBlue),
                                Triple("西移 ←", 270f, TechBlue),
                                Triple("东移 →", 90f, TechBlue)
                            ).forEach { (label, bearing, color) ->
                                Button(
                                    onClick = {
                                        val moved = CoordinateUtils.movePoint(centerLat, centerLon, nudgeStepMeters, bearing)
                                        centerLat = moved.latitude
                                        centerLon = moved.longitude
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 3. 道次与奔跑方向
                Surface(
                    color = DarkInputBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("跑步道次与方向", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                Pair(1, "1道 400m"),
                                Pair(2, "2道 407m"),
                                Pair(4, "4道 423m"),
                                Pair(8, "8道 453m")
                            ).forEach { (lane, desc) ->
                                FilterChip(
                                    selected = selectedLane == lane,
                                    onClick = { selectedLane = lane },
                                    label = { Text(desc, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = isCounterClockwise,
                                onClick = { isCounterClockwise = true },
                                label = { Text("逆时针 (田联标准/左转)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !isCounterClockwise,
                                onClick = { isCounterClockwise = false },
                                label = { Text("顺时针 (右转)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 4. 目标圈数与总里程
                Surface(
                    color = DarkInputBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("目标圈数与里程", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(
                                "预估总计: ${"%.2f".format(totalEstimatedKm)} km",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechEmeraldLight,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("3", "5", "8", "10", "12.5").forEach { lap ->
                                FilterChip(
                                    selected = targetLapsText == lap,
                                    onClick = { targetLapsText = lap },
                                    label = { Text("${lap}圈", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = targetLapsText,
                            onValueChange = { targetLapsText = it },
                            label = { Text("自定义圈数 (圈)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPts = CoordinateUtils.generateTrack400m(
                        centerLat = centerLat,
                        centerLon = centerLon,
                        orientationDeg = orientationDeg,
                        lane = selectedLane,
                        isCounterClockwise = isCounterClockwise
                    )
                    onConfirm(finalPts, totalEstimatedKm)
                    Toast.makeText(context, "已精准对齐 400m 操场跑道 (预设 ${targetLapsText} 圈 / ${"%.2f".format(totalEstimatedKm)} km)", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
            ) {
                Text("确定应用", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun getOrientationDesc(deg: Float): String {
    val norm = ((deg % 360f) + 360f) % 360f
    return when {
        norm < 22.5f || norm >= 337.5f -> "南北向"
        norm < 67.5f -> "东北-西南"
        norm < 112.5f -> "东西向"
        norm < 157.5f -> "东南-西北"
        norm < 202.5f -> "南北向"
        norm < 247.5f -> "东北-西南"
        norm < 292.5f -> "东西向"
        else -> "东南-西北"
    }
}
