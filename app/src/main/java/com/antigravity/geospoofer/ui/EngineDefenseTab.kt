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
import com.antigravity.geospoofer.provider.LocationStateProvider
import com.antigravity.geospoofer.ui.theme.*

@Composable
fun EngineDefenseTab(
    isRooted: Boolean,
    onGrantMockViaRoot: () -> Unit,
    isIgnoringBattery: Boolean,
    onRequestIgnoreBattery: () -> Unit,
    joystickEnabled: Boolean,
    onToggleJoystick: (Boolean) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 系统权限与环境常驻卡片
        GlassCard(
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isRooted) Icons.Default.Security else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isRooted) TechEmerald else TechCyanLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (isRooted) "Root: 已获取 SU 核心权限" else "运行环境: 免Root模式 (标准模拟位置)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isRooted) "支持底层抹除 Mock 标志与 LSPosed 拦截" else "在【开发者选项】勾选本应用即可全局生效",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (isRooted) {
                    Button(
                        onClick = onGrantMockViaRoot,
                        colors = ButtonDefaults.buttonColors(containerColor = TechEmerald),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("提权注入", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DarkDivider)
            Spacer(modifier = Modifier.height(10.dp))

            // 后台保活免休眠
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("后台免休眠常驻 (忽略电池优化)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = if (isIgnoringBattery) "已激活白名单，灭屏长时间巡航不被系统挂起" else "建议加入白名单，防止后台息屏时进程被杀",
                        fontSize = 11.sp,
                        color = if (isIgnoringBattery) TechEmeraldLight else TextSecondary
                    )
                }
                if (isIgnoringBattery) {
                    Surface(
                        color = TechEmeraldDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, TechEmerald.copy(alpha = 0.4f))
                    ) {
                        Text("已激活", color = TechEmeraldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                } else {
                    Button(
                        onClick = onRequestIgnoreBattery,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("加白名单", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DarkDivider)
            Spacer(modifier = Modifier.height(10.dp))

            // 全局灵动悬浮窗
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("全局灵动悬浮控制岛", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("在微信、游戏或打卡上方悬浮监控里程时速与方向摇杆", fontSize = 11.sp, color = TextSecondary)
                }
                Switch(
                    checked = joystickEnabled,
                    onCheckedChange = onToggleJoystick,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TechBlue,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = DarkCardSubtle,
                        uncheckedBorderColor = DarkCardBorder
                    )
                )
            }
        }

        // 2. 六维防检测体检中心横幅
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F1F17),
            border = BorderStroke(1.dp, TechEmerald.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TechEmeraldLight, modifier = Modifier.size(18.dp))
                        Text("六维防检测体检中心", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("针对钉钉、企业微信、Keep 等大厂风控特征深度审计", fontSize = 10.sp, color = Color(0xFFA7F3D0))
                }
                Button(
                    onClick = onOpenAuditDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = TechEmerald),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("开始体检", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. 非 Root 高德防跳专区 (AMap Anti-Jump)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1F180F),
            border = BorderStroke(1.dp, TechAmber.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = TechAmberLight, modifier = Modifier.size(18.dp))
                            Text("4Hz 高频防跳脉冲压制", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TechAmberLight)
                        }
                        Text("静止心跳提速至 250ms，以密集虚拟信号压制高德真实定位", fontSize = 10.sp, color = Color(0xFFFDE68A))
                    }
                    Switch(
                        checked = isAntiJumpHighFreq,
                        onCheckedChange = {
                            onSetAntiJumpHighFreq(it)
                            Toast.makeText(context, if (it) "已开启 250ms 高频防跳脉冲" else "已恢复 1000ms 标准脉冲", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TechAmber,
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = DarkCardSubtle,
                            uncheckedBorderColor = DarkCardBorder
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenAntiJumpGuide,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF332015))
                    ) {
                        Text("📖 高德防跳排查指南", fontSize = 11.sp, color = TechAmberLight, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = onOpenLocationSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF3A241A))
                    ) {
                        Text("⚙️ 一键关闭系统扫描", fontSize = 11.sp, color = Color(0xFFFFCCBC), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. 动力学物理状态机与高拟真算法
        GlassCard(
            contentPadding = PaddingValues(14.dp)
        ) {
            Text("动力学物理状态机与拟真算法", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            // 1. 高斯抖动
            SettingToggleRow(
                title = "GPS 自然高斯微抖动 (防绝对直线)",
                description = "自动注入自然卫星噪点，避免匀速直线被风控判外挂",
                checked = enableJitter,
                onCheckedChange = onSetJitterEnabled,
                icon = Icons.Default.Grain,
                iconColor = TechBlueLight
            )

            if (enableJitter) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MockLocationEngine.JitterProfile.values().filter { it != MockLocationEngine.JitterProfile.NONE }.forEach { prof ->
                        val isSelected = selectedJitterProfile == prof
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TechBlue.copy(alpha = 0.2f) else DarkInputBg,
                            border = BorderStroke(1.dp, if (isSelected) TechBlue else DarkCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetJitterProfile(prof) }
                        ) {
                            Text(
                                text = prof.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TechBlueLight else TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DarkDivider)
            Spacer(modifier = Modifier.height(8.dp))

            // 2. 生理步态
            SettingToggleRow(
                title = "人类步态生理波动与转弯减速",
                description = "模拟步伐呼吸周期微震荡，大转弯前 12 米平滑减速",
                checked = enablePhysiologicalGait,
                onCheckedChange = onSetPhysiologicalGait,
                icon = Icons.Default.DirectionsWalk,
                iconColor = TechEmeraldLight
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DarkDivider)
            Spacer(modifier = Modifier.height(8.dp))

            // 3. 红绿灯驻留
            SettingToggleRow(
                title = "拟真等红绿灯与路口驻留",
                description = "路口随机驻留 10~20 秒，杜绝全程不停被 AI 判定作弊",
                checked = enableRandomStops,
                onCheckedChange = onSetRandomStops,
                icon = Icons.Default.Traffic,
                iconColor = TechAmberLight
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DarkDivider)
            Spacer(modifier = Modifier.height(8.dp))

            // 4. 体能衰减
            SettingToggleRow(
                title = "真实体能消耗与配速衰减波形",
                description = "随行进里程模拟真实疲劳曲线，Keep/悦跑圈图表完美拟真",
                checked = enableFatigueCurve,
                onCheckedChange = onSetFatigueCurve,
                icon = Icons.Default.Timeline,
                iconColor = Color(0xFFA78BFA)
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DarkDivider)
            Spacer(modifier = Modifier.height(8.dp))

            // 5. 海拔起伏
            SettingToggleRow(
                title = "真实地形海拔起伏与坡度拟真",
                description = "自然道路微坡度升降，生成 Strava/Keep 真实爬坡高度",
                checked = enableTerrainElevation,
                onCheckedChange = onSetTerrainElevation,
                icon = Icons.Default.Landscape,
                iconColor = TechCyanLight
            )
        }

        // 5. 无线电环境指纹伪装 (Wi-Fi 考勤与基站)
        GlassCard(
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("无线电环境指纹伪装", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = if (LocationStateProvider.wifiMockEnabled || LocationStateProvider.cellMockEnabled)
                            "已激活: ${if (LocationStateProvider.wifiMockEnabled) "Wi-Fi(${LocationStateProvider.wifiSsid}) " else ""}${if (LocationStateProvider.cellMockEnabled) "基站(CID:${LocationStateProvider.cellCid})" else ""}"
                        else "伪装企业 Wi-Fi 与移动基站，杜绝打卡风控环境比对",
                        fontSize = 11.sp,
                        color = if (LocationStateProvider.wifiMockEnabled) TechCyanLight else TextSecondary
                    )
                }
                Button(
                    onClick = onOpenRadioSpoofDialog,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("配置伪装", fontSize = 11.sp)
                }
            }
        }

        // 6. NMEA-0183 硬件卫星数据流终端
        GlassCard(
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(color = TechEmerald, shape = CircleShape, modifier = Modifier.size(7.dp)) {}
                    Text("NMEA-0183 硬件卫星终端", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                TextButton(
                    onClick = onToggleNmeaTerminal,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(if (showNmeaTerminal) "收起终端" else "展开终端", fontSize = 11.sp, color = TechBlueLight)
                }
            }

            AnimatedVisibility(visible = showNmeaTerminal) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("卫星锁定: ${liveSats} 颗", fontSize = 11.sp, color = TechEmeraldLight, fontWeight = FontWeight.Bold)
                        Text("平均 SNR: ${"%.1f".format(liveCn0)} dB-Hz", fontSize = 11.sp, color = TechCyanLight, fontWeight = FontWeight.Bold)
                    }

                    Surface(
                        color = Color(0xFF070B11),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (liveRmc.isNotBlank()) liveRmc else "\$GPRMC (待命中，启动模拟后将实时推流)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF34D399)
                            )
                            Text(
                                text = if (liveGga.isNotBlank()) liveGga else "\$GPGGA (待命中，启动模拟后将实时推流)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8)
                            )
                            if (liveGsv.isNotBlank()) {
                                Text(
                                    text = liveGsv,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
