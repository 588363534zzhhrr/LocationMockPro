package com.antigravity.geospoofer.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.core.MockLocationEngine
import com.antigravity.geospoofer.core.UpdateManager
import com.antigravity.geospoofer.provider.LocationStateProvider
import com.antigravity.geospoofer.ui.theme.*

@Composable
fun EngineDefenseTab(
    isRooted: Boolean,
    onGrantMockViaRoot: () -> Unit,
    isIgnoringBattery: Boolean,
    onRequestIgnoreBattery: () -> Unit,
    onOpenAuditDialog: () -> Unit,
    isAntiJumpHighFreq: Boolean,
    onSetAntiJumpHighFreq: (Boolean) -> Unit,
    onOpenAntiJumpGuide: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    enableJitter: Boolean,
    onSetJitterEnabled: (Boolean) -> Unit,
    selectedJitterProfile: MockLocationEngine.JitterProfile,
    onSetJitterProfile: (MockLocationEngine.JitterProfile) -> Unit,
    enablePhysiologicalGait: Boolean,
    onSetPhysiologicalGait: (Boolean) -> Unit,
    enableRandomStops: Boolean,
    onSetRandomStops: (Boolean) -> Unit,
    enableFatigueCurve: Boolean,
    onSetFatigueCurve: (Boolean) -> Unit,
    enableTerrainElevation: Boolean,
    onSetTerrainElevation: (Boolean) -> Unit,
    onOpenRadioSpoofDialog: () -> Unit,
    showNmeaTerminal: Boolean,
    onToggleNmeaTerminal: () -> Unit,
    liveRmc: String,
    liveGga: String,
    liveGsv: String,
    liveSats: Int,
    liveCn0: Float,
    onCheckUpdate: () -> Unit = {},
    isCheckingUpdate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 系统权限与环境常驻卡片
        GlassCard(
            accentGlowColor = if (isRooted) TechEmerald else TechBlue,
            contentPadding = PaddingValues(16.dp)
        ) {
            TechSectionHeader(
                title = "系统底座与保活常驻",
                icon = if (isRooted) Icons.Default.Security else Icons.Default.Shield,
                iconTint = if (isRooted) TechEmeraldLight else TechCyanLight,
                badgeText = if (isRooted) "ROOT 提权" else "免 ROOT 模式",
                badgeColor = if (isRooted) TechEmeraldLight else TechCyanLight,
                actionSlot = if (isRooted) {
                    {
                        Button(
                            onClick = onGrantMockViaRoot,
                            colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("注入底座", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else null
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isRooted) "已获取底层 Root 权限，激活 LSPosed 拦截与 Mock 标记抹除" else "基于系统标准模拟位置接口，在【开发者选项】中勾选本应用即可生效",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = AppleDivider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 后台保活免休眠
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("后台免休眠常驻 (忽略电池优化)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isIgnoringBattery) "已激活白名单，灭屏长时间巡航不被系统强杀" else "建议加入系统电池白名单，防止后台息屏时进程挂起",
                        fontSize = 11.sp,
                        color = if (isIgnoringBattery) AppleGreen else TextSecondary
                    )
                }
                if (isIgnoringBattery) {
                    Surface(
                        color = AppleCardSubtle,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, AppleGreen.copy(alpha = 0.5f))
                    ) {
                        Text("已激活", color = AppleGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                } else {
                    Button(
                        onClick = onRequestIgnoreBattery,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("加白名单", fontSize = 11.sp)
                    }
                }
            }
        }

        // 2. 六维防检测体检中心
        GlassCard(
            accentGlowColor = TechEmerald,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TechEmerald.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, TechEmeraldLight.copy(alpha = 0.4f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TechEmeraldLight, modifier = Modifier.size(21.dp))
                        }
                    }
                    Column {
                        Text("六维防检测深度体检", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("钉钉、企业微信、Keep 等风控特征深度审计", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Button(
                    onClick = onOpenAuditDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = TechEmerald.copy(alpha = 0.22f)),
                    border = BorderStroke(0.8.dp, TechEmeraldLight.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("开始体检", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TechEmeraldLight)
                }
            }
        }

        // 3. 高德防跳专区 (AMap Anti-Jump)
        GlassCard(
            accentGlowColor = TechAmber,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TechAmber.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, TechAmberLight.copy(alpha = 0.4f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = TechAmberLight, modifier = Modifier.size(21.dp))
                            }
                        }
                        Column {
                            Text("高德基站防跳 4Hz 脉冲压制", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("250ms 高频虚拟脉冲强行覆盖基站信号", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = isAntiJumpHighFreq,
                        onCheckedChange = {
                            onSetAntiJumpHighFreq(it)
                            Toast.makeText(context, if (it) "已开启 250ms 高频防跳脉冲" else "已恢复 1000ms 标准脉冲", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TechEmerald,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = DarkInputBg,
                            uncheckedBorderColor = Color.White.copy(alpha = 0.14f)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAntiJumpGuide,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, TechAmberLight.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkInputBg),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = TechAmberLight, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("防跳排查指南", fontSize = 11.sp, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = onOpenLocationSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, TechCyanLight.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkInputBg),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = TechCyanLight, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("关闭系统扫描", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        }

        // 4. 动力学物理状态机与高拟真算法
        GlassCard(
            accentGlowColor = TechPurple,
            contentPadding = PaddingValues(16.dp)
        ) {
            TechSectionHeader(
                title = "物理动力学与拟真状态机",
                icon = Icons.Default.Timeline,
                iconTint = TechPurpleLight,
                badgeText = "5 维拟真",
                badgeColor = TechPurpleLight
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 1. 高斯微抖动
            SettingToggleRow(
                title = "GPS 自然高斯微抖动 (防绝对直线)",
                description = "自动注入自然卫星噪点，避免绝对匀速直线被风控判外挂",
                checked = enableJitter,
                onCheckedChange = onSetJitterEnabled,
                icon = Icons.Default.Grain,
                iconColor = TechCyanLight
            )

            if (enableJitter) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MockLocationEngine.JitterProfile.values().filter { it != MockLocationEngine.JitterProfile.NONE }.forEach { prof ->
                        val isSelected = selectedJitterProfile == prof
                        TechSelectorChip(
                            text = prof.label,
                            isSelected = isSelected,
                            onClick = { onSetJitterProfile(prof) },
                            modifier = Modifier.weight(1f),
                            activeColor = TechCyan,
                            activeTextColor = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = AppleDivider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // 2. 生理步态
            SettingToggleRow(
                title = "人类步态生理波动与转弯减速",
                description = "模拟步伐呼吸周期微震荡，大转弯前 12 米平滑减速",
                checked = enablePhysiologicalGait,
                onCheckedChange = onSetPhysiologicalGait,
                icon = Icons.Default.DirectionsWalk,
                iconColor = TechEmeraldLight
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = AppleDivider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // 3. 红绿灯驻留
            SettingToggleRow(
                title = "拟真路口等待与动态红绿灯",
                description = "路口随机驻留 10~20 秒，杜绝全程不停被 AI 判定作弊",
                checked = enableRandomStops,
                onCheckedChange = onSetRandomStops,
                icon = Icons.Default.Traffic,
                iconColor = TechAmberLight
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = AppleDivider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // 4. 体能衰减
            SettingToggleRow(
                title = "真实体能消耗与配速衰减波形",
                description = "随行进里程模拟真实疲劳曲线，Keep/悦跑圈配速图表完美拟真",
                checked = enableFatigueCurve,
                onCheckedChange = onSetFatigueCurve,
                icon = Icons.Default.TrendingDown,
                iconColor = TechPurpleLight
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = AppleDivider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // 5. 海拔起伏
            SettingToggleRow(
                title = "真实地形海拔起伏与坡度拟真",
                description = "自然道路微坡度升降，生成 Strava/Keep 真实爬坡高度",
                checked = enableTerrainElevation,
                onCheckedChange = onSetTerrainElevation,
                icon = Icons.Default.Landscape,
                iconColor = TechBlueLight
            )
        }

        // 5. 无线电环境指纹伪装
        GlassCard(
            accentGlowColor = TechCyan,
            contentPadding = PaddingValues(16.dp)
        ) {
            TechSectionHeader(
                title = "无线电环境指纹伪装",
                icon = Icons.Default.Wifi,
                iconTint = TechCyanLight,
                actionSlot = {
                    Button(
                        onClick = onOpenRadioSpoofDialog,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("配置伪装", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (LocationStateProvider.wifiMockEnabled || LocationStateProvider.cellMockEnabled)
                    "已激活: ${if (LocationStateProvider.wifiMockEnabled) "Wi-Fi (${LocationStateProvider.wifiSsid}) " else ""}${if (LocationStateProvider.cellMockEnabled) "基站 (CID:${LocationStateProvider.cellCid})" else ""}"
                else "伪装企业 Wi-Fi 与移动基站，杜绝打卡风控环境比对",
                fontSize = 11.sp,
                color = if (LocationStateProvider.wifiMockEnabled) TechCyanLight else TextSecondary
            )
        }

        // 6. NMEA-0183 硬件卫星数据流终端
        GlassCard(
            accentGlowColor = TechEmerald,
            contentPadding = PaddingValues(16.dp)
        ) {
            TechSectionHeader(
                title = "NMEA-0183 硬件卫星终端",
                icon = Icons.Default.Terminal,
                iconTint = TechEmeraldLight,
                badgeText = if (liveRmc.isNotBlank()) "实时推流" else "待命中",
                badgeColor = if (liveRmc.isNotBlank()) TechEmeraldLight else TextSecondary,
                actionSlot = {
                    TextButton(
                        onClick = onToggleNmeaTerminal,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (showNmeaTerminal) "收起终端" else "展开终端", fontSize = 11.sp, color = TechCyanLight, fontWeight = FontWeight.SemiBold)
                    }
                }
            )

            AnimatedVisibility(visible = showNmeaTerminal) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("卫星锁定: ${liveSats} 颗", fontSize = 11.sp, color = AppleGreen, fontWeight = FontWeight.Bold)
                        Text("平均 SNR: ${"%.1f".format(liveCn0)} dB-Hz", fontSize = 11.sp, color = AppleBlue, fontWeight = FontWeight.Bold)
                    }

                    Surface(
                        color = AppleInputBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, AppleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (liveRmc.isNotBlank()) liveRmc else "\$GPRMC (待命中，启动模拟后将实时推流)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = AppleGreen
                            )
                            Text(
                                text = if (liveGga.isNotBlank()) liveGga else "\$GPGGA (待命中，启动模拟后将实时推流)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = AppleBlue
                            )
                            if (liveGsv.isNotBlank()) {
                                Text(
                                    text = liveGsv,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = AppleOrange
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. 软件版本与自动在线更新
        GlassCard(
            contentPadding = PaddingValues(16.dp)
        ) {
            TechSectionHeader(
                title = "软件与内核自动更新",
                icon = Icons.Default.SystemUpdate,
                iconTint = AppleBlue,
                badgeText = "v${UpdateManager.getCurrentVersionName(context)}",
                badgeColor = AppleBlue,
                actionSlot = {
                    Button(
                        onClick = onCheckUpdate,
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleBlue,
                            disabledContainerColor = AppleCardSubtle
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text("检查更新", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRooted) "已获 Root 提权，支持静默无感覆盖安装" else "基于 GitHub Releases 在线极速更新与校验",
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = AppleDivider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 开关 1: 启动时自动检查
            var autoCheck by remember { mutableStateOf(UpdateManager.isAutoCheckEnabled(context)) }
            SettingToggleRow(
                title = "启动时自动检测新版本",
                description = "应用冷启动时轻量检测云端构建并在发现新版时提醒",
                checked = autoCheck,
                onCheckedChange = {
                    autoCheck = it
                    UpdateManager.setAutoCheckEnabled(context, it)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = AppleDivider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 开关 2: 国内镜像下载加速
            var useMirror by remember { mutableStateOf(UpdateManager.isMirrorEnabled(context)) }
            SettingToggleRow(
                title = "国内节点下载加速 (gh-proxy)",
                description = "针对大陆网络自动路由至国内高速反代，支持双源容灾",
                checked = useMirror,
                onCheckedChange = {
                    useMirror = it
                    UpdateManager.setMirrorEnabled(context, it)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
