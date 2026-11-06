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
import androidx.compose.ui.text.TextStyle
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
    savedRoutes: List<SavedRoute>,
    presetRoutes: List<PresetRoute>,
    onOpenSaveDialog: () -> Unit,
    onOpenGpxDialog: () -> Unit,
    onOpenLoopDialog: () -> Unit,
    onOpenTrackDialog: () -> Unit,
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
    var showAllWaypoints by remember { mutableStateOf(false) }

    // 页面进入或切换 Tab 时，自动同步已规划路网与轨迹航线
    LaunchedEffect(mapController.isReady) {
        if (mapController.isReady && waypoints.isNotEmpty()) {
            mapController.setRouteWaypoints(waypoints)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==================== 状态 A: 实时仿真飞行仪表盘 (Aviation HUD) ====================
        if (simState.isSimulating) {
            GlassCard(
                accentGlowColor = if (simState.isPaused) TechAmber else TechEmerald,
                borderColor = if (simState.isPaused) TechAmberLight.copy(alpha = 0.5f) else TechEmeraldLight.copy(alpha = 0.45f),
                contentPadding = PaddingValues(16.dp)
            ) {
                // 1. 顶部状态与航段
                TechSectionHeader(
                    title = if (simState.isPaused) "巡航挂起 (心跳保活)" else "航向与动力学实时推流中",
                    icon = if (simState.isPaused) Icons.Default.PauseCircleFilled else Icons.Default.Radar,
                    iconTint = if (simState.isPaused) TechAmberLight else TechEmeraldLight,
                    badgeText = "航段 ${simState.currentSegmentIndex} / ${simState.totalSegments}",
                    badgeColor = if (simState.isPaused) TechAmberLight else TechCyanLight
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 进度条
                LinearProgressIndicator(
                    progress = { simState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (simState.isPaused) TechAmberLight else TechCyanLight,
                    trackColor = DarkInputBg
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. 4-Grid 航空级指标磁贴
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        icon = Icons.Default.Speed,
                        label = "实时时速",
                        value = "%.1f".format(simState.currentSpeedMps * 3.6f),
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
                        accentColor = TechEmeraldLight,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. 达成目标与等红绿灯横幅
                if (simState.isGoalReached) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = AppleCardSubtle,
                        border = BorderStroke(0.5.dp, AppleGreen.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AppleGreen, modifier = Modifier.size(16.dp))
                                Text(simState.goalSummary, fontSize = 12.sp, color = AppleGreen, fontWeight = FontWeight.Bold)
                            }
                            Text("已平缓降速驻留", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                if (simState.isTemporarilyStopped) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = AppleCardSubtle,
                        border = BorderStroke(0.5.dp, AppleOrange.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Traffic, contentDescription = null, tint = AppleOrange, modifier = Modifier.size(16.dp))
                                Text("路口驻留等待: ${simState.stopReason}", fontSize = 12.sp, color = AppleOrange, fontWeight = FontWeight.Bold)
                            }
                            Text("倒计时 ${simState.stopCountdownSeconds}s", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. 动态无级调速滑块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("巡航时速无级微调", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${currentSpeedKmh.toInt()} km/h (${"%.1f".format(currentSpeedKmh / 3.6f)} m/s)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleBlue,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = currentSpeedKmh,
                    onValueChange = { onSpeedChange(it) },
                    valueRange = 1f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppleBlue,
                        activeTrackColor = AppleBlue,
                        inactiveTrackColor = AppleCardSubtle
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 6. 巡航主控按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { if (simState.isPaused) onResumeRoute() else onPauseRoute() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simState.isPaused) AppleGreen else AppleOrange
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (simState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (simState.isPaused) "继续巡航" else "暂停模拟", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onStopRoute,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停止巡航", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        if (waypoints.isNotEmpty()) {
                            val ok = GpxExportHelper.exportAndShareGpx(
                                context,
                                waypoints,
                                "运动巡航轨迹",
                                simState.currentSpeedMps.coerceAtLeast(1.0f),
                                isGcj02Input = isGcj02Route
                            )
                            if (!ok) {
                                GpxExportHelper.copyGpxToClipboard(
                                    context,
                                    waypoints,
                                    "运动巡航轨迹",
                                    simState.currentSpeedMps.coerceAtLeast(1.0f),
                                    isGcj02Input = isGcj02Route
                                )
                                Toast.makeText(context, "已复制 GPX XML 至剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, AppleBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = AppleCardSubtle),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppleBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导出实时 GPX 轨迹文件 (国际标准 WGS-84)", fontSize = 11.sp, color = TextPrimary)
                }
            }
        }

        // ==================== 状态 B: 路线规划器与自动化设置 ====================
        if (!simState.isSimulating) {
            // 智能轨迹生成工具箱 (Toolbox)
            GlassCard(
                accentGlowColor = TechPurple,
                contentPadding = PaddingValues(16.dp)
            ) {
                TechSectionHeader(
                    title = "智能轨迹工具箱",
                    icon = Icons.Default.AutoFixHigh,
                    iconTint = TechPurpleLight,
                    badgeText = "AI 几何",
                    badgeColor = TechPurpleLight
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 400m操场
                    ToolboxTile(
                        icon = Icons.Default.SportsScore,
                        title = "400m操场",
                        onClick = onOpenTrackDialog,
                        accentColor = TechAmberLight,
                        modifier = Modifier.weight(1f)
                    )

                    // 2. 智能环线
                    ToolboxTile(
                        icon = Icons.Default.Shuffle,
                        title = "智能环线",
                        onClick = onOpenLoopDialog,
                        accentColor = TechPurpleLight,
                        modifier = Modifier.weight(1f)
                    )

                    // 3. 沿路贴合
                    ToolboxTile(
                        icon = Icons.Default.AltRoute,
                        title = if (isSnappingRoad) "吸附中..." else "沿路贴合",
                        onClick = {
                            if (waypoints.size >= 2) {
                                isSnappingRoad = true
                                coroutineScope.launch {
                                    Toast.makeText(context, "正在沿真实道路规划吸附...", Toast.LENGTH_SHORT).show()
                                    val result = RoadSnapHelper.snapWaypointsToRoad(waypoints, RoadSnapHelper.SnapProfile.WALKING, isGcj02Route)
                                    result.onSuccess { snapped ->
                                        onWaypointsChange(snapped)
                                        mapController.setRouteWaypoints(snapped)
                                        mapController.fitRoute()
                                        Toast.makeText(context, "已吸附生成 ${snapped.size} 个道路路点！", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "吸附失败，保留原路点: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    isSnappingRoad = false
                                }
                            } else {
                                Toast.makeText(context, "至少需 2 个路点才能进行吸附", Toast.LENGTH_SHORT).show()
                            }
                        },
                        isLoading = isSnappingRoad,
                        accentColor = TechCyanLight,
                        modifier = Modifier.weight(1f)
                    )

                    // 4. 原路折返
                    ToolboxTile(
                        icon = Icons.Default.SyncAlt,
                        title = "原路折返",
                        onClick = {
                            if (waypoints.size >= 2) {
                                val reversed = waypoints.dropLast(1).reversed()
                                val roundTrip = waypoints + reversed
                                onWaypointsChange(roundTrip)
                                mapController.setRouteWaypoints(roundTrip)
                                mapController.fitRoute()
                                Toast.makeText(context, "已生成原路折返路线", Toast.LENGTH_SHORT).show()
                            }
                        },
                        accentColor = TechEmeraldLight,
                        modifier = Modifier.weight(1f)
                    )

                    // 5. 反转
                    ToolboxTile(
                        icon = Icons.Default.SwapVert,
                        title = "起终互换",
                        onClick = {
                            if (waypoints.size >= 2) {
                                val reversed = waypoints.reversed()
                                onWaypointsChange(reversed)
                                mapController.setRouteWaypoints(reversed)
                                mapController.fitRoute()
                                Toast.makeText(context, "已反转起点与终点", Toast.LENGTH_SHORT).show()
                            }
                        },
                        accentColor = TechBlueLight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 巡航运动档位与守护目标
            GlassCard(
                accentGlowColor = TechBlue,
                contentPadding = PaddingValues(16.dp)
            ) {
                TechSectionHeader(
                    title = "巡航动力学与自动目标",
                    icon = Icons.Default.Tune,
                    iconTint = TechCyanLight
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 运动速度模式
                Text("巡航基准档位", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RouteSimulator.SpeedMode.entries.forEach { mode ->
                        val isSelected = selectedSpeedMode == mode
                        TechSelectorChip(
                            text = mode.label,
                            isSelected = isSelected,
                            onClick = {
                                onSpeedModeChange(mode)
                                onSpeedChange(mode.speedMps * 3.6f)
                            },
                            modifier = Modifier.weight(1f),
                            activeColor = TechCyan,
                            activeTextColor = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 巡航模式选择
                Text("往返巡航逻辑", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RouteSimulator.RouteMode.values().forEach { mode ->
                        val isSelected = routeMode == mode
                        TechSelectorChip(
                            text = mode.label,
                            isSelected = isSelected,
                            onClick = { onRouteModeChange(mode) },
                            modifier = Modifier.weight(1f),
                            activeColor = TechBlue,
                            activeTextColor = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 目标里程守护
                Text("目标里程守护 (达成自动平缓刹车)", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                val targets = listOf(
                    Pair("无上限", null),
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
                        TechSelectorChip(
                            text = label,
                            isSelected = isSelected,
                            onClick = { onTargetDistanceKmChange(distKm) },
                            modifier = Modifier.weight(1f),
                            activeColor = TechEmerald,
                            activeTextColor = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isGcj02Route,
                        onCheckedChange = onGcj02RouteChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppleBlue,
                            uncheckedColor = AppleCardSubtle
                        )
                    )
                    Text("路点输入均为火星坐标 (高德/腾讯自动转标准GPS)", fontSize = 12.sp, color = TextSecondary)
                }
            }

            // 途经路点清单管理
            GlassCard(
                contentPadding = PaddingValues(16.dp)
            ) {
                TechSectionHeader(
                    title = "途经路点清单",
                    icon = Icons.Default.PinDrop,
                    iconTint = AppleBlue,
                    badgeText = "${waypoints.size} 点",
                    badgeColor = AppleBlue,
                    actionSlot = if (waypoints.isNotEmpty()) {
                        {
                            TextButton(
                                onClick = {
                                    onWaypointsChange(emptyList())
                                    mapController.setRouteWaypoints(emptyList())
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("清空路点", fontSize = 11.sp, color = AppleRed, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else null
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 路点列表项（当路点数超过 30 个时自动折叠中间密集点，避免长轨迹造成卡顿）
                val isDenseWaypoints = waypoints.size > 30
                val visibleIndices = if (isDenseWaypoints && !showAllWaypoints) {
                    (0 until 5).toList() + listOf(-1) + ((waypoints.size - 5) until waypoints.size).toList()
                } else {
                    waypoints.indices.toList()
                }

                visibleIndices.forEach { index ->
                    if (index == -1) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AppleInputBg,
                            border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { showAllWaypoints = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已智能折叠中间 ${waypoints.size - 10} 个密集轨迹点（点击展开全部）",
                                    fontSize = 11.sp,
                                    color = AppleBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        val point = waypoints[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppleCardSubtle,
                            border = BorderStroke(0.5.dp, AppleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val (badgeColor, label) = when (index) {
                                        0 -> Pair(AppleGreen, "起")
                                        waypoints.size - 1 -> Pair(AppleRed, "终")
                                        else -> Pair(AppleBlue, "$index")
                                    }
                                    Surface(
                                        color = badgeColor,
                                        shape = CircleShape,
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${"%.6f".format(point.latitude)}, ${"%.6f".format(point.longitude)}",
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", tint = TextSecondary, modifier = Modifier.size(17.dp))
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
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", tint = TextSecondary, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val updated = waypoints.filterIndexed { i, _ -> i != index }
                                            onWaypointsChange(updated)
                                            mapController.setRouteWaypoints(updated)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "删除", tint = TextTertiary, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (isDenseWaypoints && showAllWaypoints) {
                    TextButton(
                        onClick = { showAllWaypoints = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("收起中间密集轨迹点", fontSize = 11.sp, color = AppleBlue)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 手动添加自定义路点输入行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newLatText,
                        onValueChange = { newLatText = it },
                        label = { Text("纬度", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AppleInputBg,
                            unfocusedContainerColor = AppleInputBg,
                            focusedBorderColor = AppleBlue,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    OutlinedTextField(
                        value = newLonText,
                        onValueChange = { newLonText = it },
                        label = { Text("经度", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AppleInputBg,
                            unfocusedContainerColor = AppleInputBg,
                            focusedBorderColor = AppleBlue,
                            unfocusedBorderColor = Color.Transparent
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
                        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 经典预设与路线收藏夹管理
            GlassCard(
                contentPadding = PaddingValues(16.dp)
            ) {
                TechSectionHeader(
                    title = "路线收藏与经典预设",
                    icon = Icons.Default.FolderSpecial,
                    iconTint = AppleBlue,
                    actionSlot = {
                        TextButton(
                            onClick = { showPresetDrawer = !showPresetDrawer },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (showPresetDrawer) "收起经典库" else "浏览经典库", fontSize = 11.sp, color = AppleBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 4 个数据流转工具按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSaveDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, AppleBorder),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppleCardSubtle),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("收藏", fontSize = 11.sp, color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onLaunchGpxFilePicker,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, AppleBorder),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppleCardSubtle),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选GPX", fontSize = 11.sp, color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onOpenGpxDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, AppleBorder),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppleCardSubtle),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("粘贴", fontSize = 11.sp, color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            if (waypoints.isNotEmpty()) {
                                val ok = GpxExportHelper.exportAndShareGpx(
                                    context,
                                    waypoints,
                                    "路线规划导出",
                                    (currentSpeedKmh / 3.6f).coerceAtLeast(1.0f),
                                    isGcj02Input = isGcj02Route
                                )
                                if (!ok) {
                                    GpxExportHelper.copyGpxToClipboard(
                                        context,
                                        waypoints,
                                        "路线规划导出",
                                        (currentSpeedKmh / 3.6f).coerceAtLeast(1.0f),
                                        isGcj02Input = isGcj02Route
                                    )
                                    Toast.makeText(context, "已复制 GPX XML 文本至剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "暂无路点可导出", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, AppleBorder),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppleCardSubtle),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出", fontSize = 11.sp, color = TextPrimary)
                    }
                }

                // 经典预设折叠区
                AnimatedVisibility(visible = showPresetDrawer) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetRoutes.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppleCardSubtle,
                                border = BorderStroke(0.5.dp, AppleBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onWaypointsChange(preset.waypoints)
                                        mapController.setRouteWaypoints(preset.waypoints)
                                        mapController.fitRoute()
                                        Toast.makeText(context, "已载入: ${preset.title}", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(preset.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AppleInputBg
                                        ) {
                                            Text(
                                                "${preset.waypoints.size} 点",
                                                fontSize = 10.sp,
                                                color = AppleBlue,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(preset.description, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // 已保存自定义路线
                if (savedRoutes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("已存自定义路线 (${savedRoutes.size})", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    savedRoutes.forEach { route ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppleCardSubtle,
                            border = BorderStroke(0.5.dp, AppleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(route.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${route.waypoints.size} 点 · ${"%.2f".format(route.totalDistanceMeters / 1000.0)} km",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
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
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("载入", fontSize = 12.sp, color = AppleBlue, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = {
                                            val gpx = RouteStorageHelper.exportToGpx(route.name, route.waypoints, isGcj02Input = isGcj02Route)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("GPX", gpx))
                                            Toast.makeText(context, "GPX XML 已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "导出", tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            RouteStorageHelper.deleteRoute(context, route.id)
                                            onUpdateSavedRoutes(RouteStorageHelper.loadSavedRoutes(context))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = TextTertiary, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 主执行操作按钮
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
