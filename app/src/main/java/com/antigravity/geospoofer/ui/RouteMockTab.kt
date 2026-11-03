package com.antigravity.geospoofer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.PresetRoute
import com.antigravity.geospoofer.core.CoordinateUtils
import com.antigravity.geospoofer.core.GpxExportHelper
import com.antigravity.geospoofer.core.RoadSnapHelper
import com.antigravity.geospoofer.core.RouteSimulator
import com.antigravity.geospoofer.core.RouteStorageHelper
import com.antigravity.geospoofer.core.SavedRoute
import com.antigravity.geospoofer.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun RouteMockTab(
    mapController: MapController,
    isMapExpanded: Boolean,
    onToggleMapExpand: () -> Unit,
    simState: RouteSimulator.SimulationState,
    waypoints: List<CoordinateUtils.LatLng>,
    onWaypointsChange: (List<CoordinateUtils.LatLng>) -> Unit,
    routeMode: RouteSimulator.RouteMode,
    onRouteModeChange: (RouteSimulator.RouteMode) -> Unit,
    targetDistanceKm: Double?,
    onTargetDistanceKmChange: (Double?) -> Unit,
    isGcj02Route: Boolean,
    onGcj02RouteChange: (Boolean) -> Unit,
    currentSpeedKmh: Float,
    onSpeedChange: (Float) -> Unit,
    selectedSpeedMode: RouteSimulator.SpeedMode,
    onSpeedModeChange: (RouteSimulator.SpeedMode) -> Unit,
    onStartRoute: () -> Unit,
    onStopRoute: () -> Unit,
    onPauseRoute: () -> Unit,
    onResumeRoute: () -> Unit,
    joystickEnabled: Boolean,
    onToggleJoystick: (Boolean) -> Unit,
    savedRoutes: List<SavedRoute>,
    presetRoutes: List<PresetRoute>,
    onOpenSaveDialog: () -> Unit,
    onOpenGpxDialog: () -> Unit,
    onOpenLoopDialog: () -> Unit,
    onLaunchGpxFilePicker: () -> Unit,
    onUpdateSavedRoutes: (List<SavedRoute>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSnappingRoad by remember { mutableStateOf(false) }
    var newLatText by remember { mutableStateOf("") }
    var newLonText by remember { mutableStateOf("") }
    var showPresetDrawer by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 🗺️ 交互式全景路线地图
        InteractiveMapCard(
            controller = mapController,
            mode = "route",
            height = if (isMapExpanded) 480.dp else 280.dp,
            onWaypointsUpdated = onWaypointsChange,
            onToggleExpand = onToggleMapExpand
        )

        // ==================== 状态 A: 实时仿真飞行仪表盘 (Aviation HUD) ====================
        if (simState.isSimulating) {
            GlassCard(
                borderColor = if (simState.isPaused) TechAmberDark else TechEmeraldDark,
                contentPadding = PaddingValues(14.dp)
            ) {
                // 1. 顶部状态与航段
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (simState.isPaused) TechAmber else TechEmerald,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Text(
                            text = if (simState.isPaused) "巡航已挂起 (心跳保活)" else "航向与动力学实时仿真中",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (simState.isPaused) TechAmberLight else TechEmeraldLight
                        )
                    }
                    Text(
                        text = "航段 ${simState.currentSegmentIndex} / ${simState.totalSegments}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 进度条
                LinearProgressIndicator(
                    progress = { simState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (simState.isPaused) TechAmber else TechEmerald,
                    trackColor = DarkDivider
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 4-Grid 航空级指标磁贴
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        icon = Icons.Default.Speed,
                        label = "实时时速",
                        value = "%.1f".format(currentSpeedKmh),
                        unit = "km/h",
                        accentColor = TechCyanLight,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        icon = Icons.Default.Route,
                        label = "累计里程",
                        value = "%.2f".format(simState.distanceCoveredMeters / 1000.0),
                        unit = "km",
                        accentColor = TechBlueLight,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        icon = Icons.Default.DirectionsRun,
                        label = "计步与步频",
                        value = "${simState.totalSteps}",
                        unit = "步 / ${simState.cadenceSpm}SPM",
                        accentColor = TechAmberLight,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        icon = Icons.Default.Landscape,
                        label = "仿真海拔/爬升",
                        value = "%.1f".format(simState.currentElevationMeters),
                        unit = "m (+${"%.1f".format(simState.elevationGainMeters)})",
                        accentColor = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. 达成目标与等红绿灯横幅
                if (simState.isGoalReached) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = TechEmeraldDark.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, TechEmerald.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🏆 ${simState.goalSummary}", fontSize = 11.sp, color = TechEmeraldLight, fontWeight = FontWeight.Bold)
                            Text("已自动减速平缓驻留", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }

                if (simState.isTemporarilyStopped) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = TechAmberDark.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, TechAmber.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🚦 拟真路口等待: ${simState.stopReason}", fontSize = 11.sp, color = TechAmberLight, fontWeight = FontWeight.Bold)
                            Text("倒计时 ${simState.stopCountdownSeconds}s", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. 动态无级调速滑块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("巡航时速无级微调:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${currentSpeedKmh.toInt()} km/h (${"%.1f".format(currentSpeedKmh / 3.6f)} m/s)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TechCyanLight,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = currentSpeedKmh,
                    onValueChange = {
                        onSpeedChange(it)
                    },
                    valueRange = 1f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = TechCyanLight,
                        activeTrackColor = TechBlue,
                        inactiveTrackColor = DarkDivider
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 6. 巡航主控按钮 (继续/暂停, 停止, 导出GPX, 悬浮窗)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { if (simState.isPaused) onResumeRoute() else onPauseRoute() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simState.isPaused) TechEmerald else TechAmber
                        ),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (simState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (simState.isPaused) "继续巡航" else "暂停模拟", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onStopRoute,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TechRose),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止巡航", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onToggleJoystick(!joystickEnabled) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (joystickEnabled) TechBlue else DarkCardBorder),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(if (joystickEnabled) "关闭悬浮窗" else "开启灵动悬浮窗", fontSize = 11.sp, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            if (waypoints.isNotEmpty()) {
                                val ok = GpxExportHelper.exportAndShareGpx(context, waypoints, "运动巡航轨迹", simState.currentSpeedMps)
                                if (!ok) {
                                    GpxExportHelper.copyGpxToClipboard(context, waypoints, "运动巡航轨迹", simState.currentSpeedMps)
                                    Toast.makeText(context, "已复制 GPX XML 至剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp), tint = TechBlueLight)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出实时GPX", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        }

        // ==================== 状态 B: 路线规划器与自动化设置 ====================
        if (!simState.isSimulating) {
            // 🛠️ 智能轨迹生成工具箱 (Quick Generators)
            GlassCard(
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = "智能轨迹工具箱",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. 400m操场
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkInputBg,
                        border = BorderStroke(1.dp, DarkDivider),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val center = waypoints.firstOrNull() ?: CoordinateUtils.LatLng(39.908823, 116.397470)
                                val track = CoordinateUtils.generateTrack400m(center.latitude, center.longitude)
                                onWaypointsChange(track)
                                mapController.setRouteWaypoints(track)
                                mapController.fitRoute()
                                onRouteModeChange(RouteSimulator.RouteMode.RING_LOOP)
                                onTargetDistanceKmChange(2.0)
                                Toast.makeText(context, "已生成 400m 操场跑道 (预设 5 圈 / 2km 闭环)", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🏟️", fontSize = 14.sp)
                            Text("400m操场", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    // 2. 智能环线
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkInputBg,
                        border = BorderStroke(1.dp, DarkDivider),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenLoopDialog() }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎲", fontSize = 14.sp)
                            Text("智能环线", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    // 3. 沿路贴合
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSnappingRoad) TechBlue.copy(alpha = 0.2f) else DarkInputBg,
                        border = BorderStroke(1.dp, if (isSnappingRoad) TechBlue else DarkDivider),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isSnappingRoad) {
                                if (waypoints.size >= 2) {
                                    isSnappingRoad = true
                                    coroutineScope.launch {
                                        Toast.makeText(context, "正在沿街道规划贴合路径...", Toast.LENGTH_SHORT).show()
                                        val result = RoadSnapHelper.snapWaypointsToRoad(waypoints, RoadSnapHelper.SnapProfile.WALKING, isGcj02Route)
                                        result.onSuccess { snapped ->
                                            onWaypointsChange(snapped)
                                            mapController.setRouteWaypoints(snapped)
                                            mapController.fitRoute()
                                            Toast.makeText(context, "已沿真实道路吸附生成 ${snapped.size} 个平滑路点！", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "吸附规划失败，保持原路线: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        isSnappingRoad = false
                                    }
                                } else {
                                    Toast.makeText(context, "至少需 2 个路点才能进行吸附", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (isSnappingRoad) "⏳" else "🛣️", fontSize = 14.sp)
                            Text(if (isSnappingRoad) "吸附中..." else "沿路贴合", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    // 4. 原路折返
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkInputBg,
                        border = BorderStroke(1.dp, DarkDivider),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (waypoints.size >= 2) {
                                    val reversed = waypoints.dropLast(1).reversed()
                                    val roundTrip = waypoints + reversed
                                    onWaypointsChange(roundTrip)
                                    mapController.setRouteWaypoints(roundTrip)
                                    mapController.fitRoute()
                                    Toast.makeText(context, "已生成原路折返闭环！", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔄", fontSize = 14.sp)
                            Text("原路折返", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    // 5. 反转
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkInputBg,
                        border = BorderStroke(1.dp, DarkDivider),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (waypoints.size >= 2) {
                                    val reversed = waypoints.reversed()
                                    onWaypointsChange(reversed)
                                    mapController.setRouteWaypoints(reversed)
                                    mapController.fitRoute()
                                    Toast.makeText(context, "已反转起点与终点！", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔁", fontSize = 14.sp)
                            Text("起终互换", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            // ⚙️ 巡航运动档位与守护目标
            GlassCard(
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "巡航动力学与自动化目标",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 运动速度模式
                Text("巡航基准档位:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RouteSimulator.SpeedMode.entries.forEach { mode ->
                        val isSelected = selectedSpeedMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TechBlue.copy(alpha = 0.2f) else DarkInputBg,
                            border = BorderStroke(1.dp, if (isSelected) TechBlue else DarkCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onSpeedModeChange(mode)
                                    onSpeedChange(mode.speedMps * 3.6f)
                                }
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TechBlueLight else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 巡航模式选择
                Text("巡航往返逻辑:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RouteSimulator.RouteMode.values().forEach { mode ->
                        val isSelected = routeMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TechCyan.copy(alpha = 0.2f) else DarkInputBg,
                            border = BorderStroke(1.dp, if (isSelected) TechCyan else DarkCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onRouteModeChange(mode) }
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TechCyanLight else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 目标里程守护
                Text("目标里程守护 (达成自动平缓刹车):", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                val targets = listOf(
                    Pair("🎯 无上限", null),
                    Pair("1.0 km", 1.0),
                    Pair("3.0 km", 3.0),
                    Pair("5.0 km", 5.0),
                    Pair("10.0 km", 10.0)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    targets.forEach { (label, distKm) ->
                        val isSelected = targetDistanceKm == distKm
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TechEmeraldDark.copy(alpha = 0.4f) else DarkInputBg,
                            border = BorderStroke(1.dp, if (isSelected) TechEmerald else DarkCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onTargetDistanceKmChange(distKm) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TechEmeraldLight else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isGcj02Route,
                        onCheckedChange = onGcj02RouteChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = TechBlue,
                            uncheckedColor = DarkCardBorder
                        )
                    )
                    Text("路点输入均为火星坐标 (高德/腾讯自动转标准GPS)", fontSize = 11.sp, color = TextSecondary)
                }
            }

            // 📍 途经路点清单管理
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
                        Text("途经路点清单", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Surface(
                            color = TechBlue.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${waypoints.size} 点",
                                fontSize = 10.sp,
                                color = TechBlueLight,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (waypoints.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                onWaypointsChange(emptyList())
                                mapController.setRouteWaypoints(emptyList())
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("清空路点", fontSize = 11.sp, color = TechRose)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 路点列表项
                waypoints.forEachIndexed { index, point ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkInputBg,
                        border = BorderStroke(1.dp, DarkDivider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = when (index) {
                                        0 -> TechEmerald
                                        waypoints.size - 1 -> TechRose
                                        else -> TechBlue
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = when (index) {
                                                0 -> "起"
                                                waypoints.size - 1 -> "终"
                                                else -> "$index"
                                            },
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "${"%.6f".format(point.latitude)}, ${"%.6f".format(point.longitude)}",
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val list = waypoints.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index - 1]
                                            list[index - 1] = temp
                                            onWaypointsChange(list)
                                            mapController.setRouteWaypoints(list)
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < waypoints.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val list = waypoints.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index + 1]
                                            list[index + 1] = temp
                                            onWaypointsChange(list)
                                            mapController.setRouteWaypoints(list)
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val updated = waypoints.filterIndexed { i, _ -> i != index }
                                        onWaypointsChange(updated)
                                        mapController.setRouteWaypoints(updated)
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "删除", tint = TextTertiary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 手动添加自定义路点输入行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newLatText,
                        onValueChange = { newLatText = it },
                        label = { Text("纬度", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkInputBg,
                            unfocusedContainerColor = DarkInputBg,
                            focusedBorderColor = TechBlue,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )
                    OutlinedTextField(
                        value = newLonText,
                        onValueChange = { newLonText = it },
                        label = { Text("经度", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkInputBg,
                            unfocusedContainerColor = DarkInputBg,
                            focusedBorderColor = TechBlue,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )
                    Button(
                        onClick = {
                            val lat = newLatText.toDoubleOrNull()
                            val lon = newLonText.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                val updated = waypoints + CoordinateUtils.LatLng(lat, lon)
                                onWaypointsChange(updated)
                                mapController.setRouteWaypoints(updated)
                                mapController.fitRoute()
                                newLatText = ""
                                newLonText = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Text("添加", fontSize = 11.sp)
                    }
                }
            }

            // 💾 经典预设与路线收藏夹管理
            GlassCard(
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("路线收藏与经典预设", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    TextButton(
                        onClick = { showPresetDrawer = !showPresetDrawer },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(if (showPresetDrawer) "收起预设" else "展开经典预设", fontSize = 11.sp, color = TechBlueLight)
                    }
                }

                // 4 个数据流转工具按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSaveDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text("💾 收藏", fontSize = 10.sp, color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onLaunchGpxFilePicker,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text("📂 选GPX", fontSize = 10.sp, color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onOpenGpxDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text("📥 粘贴", fontSize = 10.sp, color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            if (waypoints.isNotEmpty()) {
                                val ok = GpxExportHelper.exportAndShareGpx(context, waypoints, "路线规划导出", 3.2f)
                                if (!ok) {
                                    GpxExportHelper.copyGpxToClipboard(context, waypoints, "路线规划导出", 3.2f)
                                    Toast.makeText(context, "已复制 GPX XML 文本至剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "暂无路点可导出", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text("📤 导出", fontSize = 10.sp, color = TextPrimary)
                    }
                }

                // 经典预设折叠区
                AnimatedVisibility(visible = showPresetDrawer) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetRoutes.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkInputBg,
                                border = BorderStroke(1.dp, DarkDivider),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onWaypointsChange(preset.waypoints)
                                        mapController.setRouteWaypoints(preset.waypoints)
                                        mapController.fitRoute()
                                        Toast.makeText(context, "已载入: ${preset.title}", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(preset.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(preset.description, fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // 已保存自定义路线
                if (savedRoutes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("已存路线 (${savedRoutes.size}):", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    savedRoutes.forEach { route ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkInputBg,
                            border = BorderStroke(1.dp, DarkDivider),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(route.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                    Text(
                                        "${route.waypoints.size} 点 · ${"%.2f".format(route.totalDistanceMeters / 1000.0)} km",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            onWaypointsChange(route.waypoints)
                                            mapController.setRouteWaypoints(route.waypoints)
                                            mapController.fitRoute()
                                            Toast.makeText(context, "已载入: ${route.name}", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("载入", fontSize = 11.sp, color = TechBlueLight)
                                    }
                                    IconButton(
                                        onClick = {
                                            val gpx = RouteStorageHelper.exportToGpx(route.name, route.waypoints)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("GPX", gpx))
                                            Toast.makeText(context, "GPX XML 已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "导出", tint = TextSecondary, modifier = Modifier.size(13.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            RouteStorageHelper.deleteRoute(context, route.id)
                                            onUpdateSavedRoutes(RouteStorageHelper.loadSavedRoutes(context))
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = TextTertiary, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 🚀 主执行操作按钮
            PrimaryActionButton(
                text = "开始路线模拟巡航",
                icon = Icons.Default.DirectionsRun,
                isActive = false,
                onClick = onStartRoute
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
