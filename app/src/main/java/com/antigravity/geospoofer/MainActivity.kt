package com.antigravity.geospoofer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.core.CoordinateUtils
import com.antigravity.geospoofer.core.FavoritePlacesHelper
import com.antigravity.geospoofer.core.MockLocationEngine
import com.antigravity.geospoofer.core.RootHelper
import com.antigravity.geospoofer.core.RouteSimulator
import com.antigravity.geospoofer.core.RouteStorageHelper
import com.antigravity.geospoofer.core.UpdateCheckResult
import com.antigravity.geospoofer.core.UpdateInfo
import com.antigravity.geospoofer.core.UpdateManager
import com.antigravity.geospoofer.service.MockLocationService
import com.antigravity.geospoofer.ui.*
import com.antigravity.geospoofer.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// 路线预设数据结构
data class PresetRoute(
    val title: String,
    val description: String,
    val waypoints: List<CoordinateUtils.LatLng>
)

val PRESET_ROUTES = listOf(
    PresetRoute(
        title = "北京奥森 5km 跑步闭环",
        description = "奥林匹克森林公园经典闭环跑道，全长约 4.8 km",
        waypoints = listOf(
            CoordinateUtils.LatLng(40.015200, 116.388800),
            CoordinateUtils.LatLng(40.015200, 116.398000),
            CoordinateUtils.LatLng(40.024500, 116.398000),
            CoordinateUtils.LatLng(40.024500, 116.388800),
            CoordinateUtils.LatLng(40.015200, 116.388800)
        )
    ),
    PresetRoute(
        title = "上海外滩沿江漫步",
        description = "陈毅广场 -> 外白渡桥 -> 十六铺码头，全长约 2.1 km",
        waypoints = listOf(
            CoordinateUtils.LatLng(31.239688, 121.499763),
            CoordinateUtils.LatLng(31.245000, 121.492000),
            CoordinateUtils.LatLng(31.233000, 121.498000),
            CoordinateUtils.LatLng(31.226000, 121.503000)
        )
    ),
    PresetRoute(
        title = "深圳湾沿海骑行道",
        description = "红树林海滨公园 -> 观桥公园 -> 潮汐公园 -> 运动公园，全长约 5.2 km",
        waypoints = listOf(
            CoordinateUtils.LatLng(22.528000, 113.982000),
            CoordinateUtils.LatLng(22.512000, 113.965000),
            CoordinateUtils.LatLng(22.498000, 113.951000),
            CoordinateUtils.LatLng(22.485000, 113.942000)
        )
    ),
    PresetRoute(
        title = "杭州西湖苏堤白堤游线",
        description = "断桥残雪 -> 白堤 -> 平湖秋月 -> 孤山路 -> 苏堤春晓，全长约 3.8 km",
        waypoints = listOf(
            CoordinateUtils.LatLng(30.259200, 120.149500),
            CoordinateUtils.LatLng(30.254100, 120.142000),
            CoordinateUtils.LatLng(30.251200, 120.136800),
            CoordinateUtils.LatLng(30.246500, 120.131200),
            CoordinateUtils.LatLng(30.238000, 120.128500)
        )
    )
)

class MainActivity : ComponentActivity() {

    private var mockService by mutableStateOf<MockLocationService?>(null)
    private var isBound by mutableStateOf(false)
    private var isMocking by mutableStateOf(false)
    private var isRooted by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MockLocationService.LocalBinder
            mockService = binder?.getService()
            isBound = true
            isMocking = mockService?.engine?.isRunning == true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mockService = null
            isBound = false
            isMocking = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!locationGranted) {
            Toast.makeText(this, "需要定位权限以正常使用模拟功能", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        isRooted = RootHelper.isDeviceRooted()

        val serviceIntent = Intent(this, MockLocationService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            LocationMockProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val simState by (mockService?.routeSimulator?.simulationState
                        ?: MutableStateFlow(RouteSimulator.SimulationState())).collectAsState()

                    MainScreen(
                        isRooted = isRooted,
                        isMocking = isMocking || simState.isSimulating,
                        simState = simState,
                        onGrantMockViaRoot = {
                            lifecycleScope.launch {
                                Toast.makeText(this@MainActivity, "正在请求 Root 授权注入...", Toast.LENGTH_SHORT).show()
                                val success = RootHelper.grantMockLocationPermissionViaRootAsync(packageName)
                                Toast.makeText(
                                    this@MainActivity,
                                    if (success) "Root 授权模拟位置成功！" else "Root 执行失败，请检查授权管理器",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onStartMock = { lat, lon, isGcj02 ->
                            val finalCoords = if (isGcj02) {
                                CoordinateUtils.gcj02ToWgs84(lat, lon)
                            } else {
                                CoordinateUtils.LatLng(lat, lon)
                            }

                            try {
                                androidx.core.content.ContextCompat.startForegroundService(
                                    this,
                                    Intent(this, MockLocationService::class.java)
                                )
                            } catch (_: Exception) {}

                            val success = mockService?.startMock(finalCoords.latitude, finalCoords.longitude) ?: false
                            if (success) {
                                isMocking = true
                                Toast.makeText(this, "定位模拟已启动", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "启动失败！请在【开发者选项】中勾选本应用为模拟位置应用，或点击上方Root授权",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onStopMock = {
                            mockService?.stopMock()
                            isMocking = false
                            Toast.makeText(this, "定位模拟已停止", Toast.LENGTH_SHORT).show()
                        },
                        onStartRoute = { waypoints, mode, targetDist, isGcj02 ->
                            try {
                                androidx.core.content.ContextCompat.startForegroundService(
                                    this,
                                    Intent(this, MockLocationService::class.java)
                                )
                            } catch (_: Exception) {}

                            val finalPts = if (isGcj02) {
                                waypoints.map { CoordinateUtils.gcj02ToWgs84(it.latitude, it.longitude) }
                            } else {
                                waypoints
                            }

                            val success = mockService?.startRoute(
                                waypoints = finalPts,
                                mode = mode,
                                targetDistanceMeters = targetDist
                            ) ?: false

                            if (success) {
                                isMocking = true
                                Toast.makeText(this, "路线巡航仿真已启动", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "启动路线失败！请确认已在开发者选项授权模拟位置或赋予Root权限",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onStopRoute = {
                            mockService?.stopRoute()
                            isMocking = false
                            Toast.makeText(this, "路线模拟已停止", Toast.LENGTH_SHORT).show()
                        },
                        onPauseRoute = {
                            mockService?.pauseRoute()
                        },
                        onResumeRoute = {
                            mockService?.resumeRoute()
                        },
                        onSpeedChanged = { speedMps ->
                            mockService?.updateSpeed(speedMps)
                        },
                        onSetJitterEnabled = { enabled ->
                            mockService?.setJitterEnabled(enabled)
                        },
                        onSetJitterProfile = { profile ->
                            mockService?.setJitterProfile(profile)
                        },
                        onSetPhysiologicalGait = { enabled ->
                            mockService?.routeSimulator?.enablePhysiologicalGait = enabled
                        },
                        onSetRandomStops = { enabled ->
                            mockService?.routeSimulator?.enableRandomStops = enabled
                        },
                        onSetFatigueCurve = { enabled ->
                            mockService?.routeSimulator?.enableFatigueCurve = enabled
                        },
                        onSetTerrainElevation = { enabled ->
                            mockService?.routeSimulator?.enableTerrainElevation = enabled
                        },
                        onSetAntiJumpHighFreq = { enabled ->
                            mockService?.isAntiJumpHighFreqEnabled = enabled
                        },
                        engine = mockService?.engine
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isRooted: Boolean,
    isMocking: Boolean,
    simState: RouteSimulator.SimulationState,
    onGrantMockViaRoot: () -> Unit,
    onStartMock: (Double, Double, Boolean) -> Unit,
    onStopMock: () -> Unit,
    onStartRoute: (List<CoordinateUtils.LatLng>, RouteSimulator.RouteMode, Double?, Boolean) -> Unit,
    onStopRoute: () -> Unit,
    onPauseRoute: () -> Unit,
    onResumeRoute: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onSetJitterEnabled: (Boolean) -> Unit,
    onSetJitterProfile: (MockLocationEngine.JitterProfile) -> Unit,
    onSetPhysiologicalGait: (Boolean) -> Unit,
    onSetRandomStops: (Boolean) -> Unit,
    onSetFatigueCurve: (Boolean) -> Unit,
    onSetTerrainElevation: (Boolean) -> Unit = {},
    onSetAntiJumpHighFreq: (Boolean) -> Unit = {},
    engine: MockLocationEngine? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    // 从系统设置页返回时自动刷新电池优化白名单状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                    isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 0: 单点模拟, 1: 路线模拟, 2: 引擎与安全
    var selectedTab by remember { mutableIntStateOf(0) }

    // 地图与视图状态（跨 Tab 共享单实例 WebView，切换 Tab 零重载）
    var isMapExpanded by remember { mutableStateOf(false) }
    val mapController = rememberMapController()

    // 单点定位状态提升（防止切换 Tab 丢失输入坐标）
    var singleLatText by remember { mutableStateOf("39.908823") }
    var singleLonText by remember { mutableStateOf("116.397470") }
    var coordSystemSingle by remember { mutableStateOf("GCJ02") }

    // 常用打卡地点收藏夹
    var favoritePlaces by remember { mutableStateOf(FavoritePlacesHelper.loadFavorites(context)) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteAddLat by remember { mutableDoubleStateOf(39.908823) }
    var favoriteAddLon by remember { mutableDoubleStateOf(116.397470) }

    // 弹窗状态
    var showAuditDialog by remember { mutableStateOf(false) }
    var showRadioSpoofDialog by remember { mutableStateOf(false) }
    var showAntiJumpGuideDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showGpxDialog by remember { mutableStateOf(false) }
    var showLoopGeneratorDialog by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var originalWaypointsBeforeTrack by remember { mutableStateOf<List<CoordinateUtils.LatLng>?>(null) }

    // 自动更新系统状态
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoToPrompt by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val triggerUpdateCheck: (Boolean) -> Unit = { isManual ->
        if (!isCheckingUpdate) {
            isCheckingUpdate = true
            if (isManual) {
                Toast.makeText(context, "正在查询云端最新版本...", Toast.LENGTH_SHORT).show()
            }
            coroutineScope.launch {
                val result = UpdateManager.checkForUpdates(context)
                isCheckingUpdate = false
                when (result) {
                    is UpdateCheckResult.NewVersion -> {
                        updateInfoToPrompt = result.info
                        showUpdateDialog = true
                    }
                    is UpdateCheckResult.IsLatest -> {
                        if (isManual) {
                            Toast.makeText(context, "当前已是最新版本 (${result.currentVersion})", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is UpdateCheckResult.Error -> {
                        if (isManual) {
                            Toast.makeText(context, "检查更新失败: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (UpdateManager.isAutoCheckEnabled(context)) {
            triggerUpdateCheck(false)
        }
    }

    // 防检测与高频脉冲
    var enableTerrainElevation by remember { mutableStateOf(true) }
    var isAntiJumpHighFreq by remember { mutableStateOf(true) }
    var showNmeaTerminal by remember { mutableStateOf(false) }

    // NMEA 遥测数据
    var liveRmc by remember { mutableStateOf("") }
    var liveGga by remember { mutableStateOf("") }
    var liveGsv by remember { mutableStateOf("") }
    var liveSats by remember { mutableIntStateOf(16) }
    var liveCn0 by remember { mutableFloatStateOf(33.5f) }

    LaunchedEffect(isMocking, simState.isSimulating, engine) {
        while (true) {
            if (engine != null && engine.isRunning) {
                liveRmc = engine.lastNmeaRmc
                liveGga = engine.lastNmeaGga
                liveGsv = engine.lastNmeaGsv
                liveSats = engine.lastSatelliteCount
                liveCn0 = engine.lastMeanCn0
            }
            kotlinx.coroutines.delay(500)
        }
    }

    // 路线模拟状态
    var waypoints by remember {
        mutableStateOf(
            listOf(
                CoordinateUtils.LatLng(40.015200, 116.388800),
                CoordinateUtils.LatLng(40.015200, 116.398000),
                CoordinateUtils.LatLng(40.024500, 116.398000),
                CoordinateUtils.LatLng(40.024500, 116.388800),
                CoordinateUtils.LatLng(40.015200, 116.388800)
            )
        )
    }
    var routeMode by remember { mutableStateOf(RouteSimulator.RouteMode.SHUTTLE) }
    var targetDistanceKm by remember { mutableStateOf<Double?>(null) }
    var isGcj02Route by remember { mutableStateOf(true) }

    // 仿真参数与防检测（初始速度与默认 WALK 模式 5.0 km/h 保持一致）
    var enableJitter by remember { mutableStateOf(true) }
    var selectedJitterProfile by remember { mutableStateOf(MockLocationEngine.JitterProfile.OPEN_SKY) }
    var enablePhysiologicalGait by remember { mutableStateOf(true) }
    var enableRandomStops by remember { mutableStateOf(false) }
    var enableFatigueCurve by remember { mutableStateOf(false) }
    var selectedSpeedMode by remember { mutableStateOf(RouteSimulator.SpeedMode.WALK) }
    var currentSpeedKmh by remember { mutableFloatStateOf(RouteSimulator.SpeedMode.WALK.speedMps * 3.6f) }

    // 路线持久化管理与 GPX 文件选择器
    var savedRoutes by remember { mutableStateOf(RouteStorageHelper.loadSavedRoutes(context)) }

    val gpxFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (!content.isNullOrBlank()) {
                    val pts = RouteStorageHelper.importFromGpx(content, convertToGcj02 = isGcj02Route)
                    if (pts.isNotEmpty()) {
                        waypoints = pts
                        mapController.setRouteWaypoints(pts)
                        mapController.fitRoute()
                        Toast.makeText(context, "成功从文件导入 ${pts.size} 个轨迹点！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "未能识别到有效 GPX/KML 轨迹点", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 监听模拟运行状态，实时驱动地图车标与航向（若启用火星坐标则逆转纠偏，确保车标完美贴合高德图层与规划轨迹）
    LaunchedEffect(simState.isSimulating, simState.currentLat, simState.currentLon, simState.currentBearing, isGcj02Route) {
        if (simState.isSimulating) {
            val displayPt = if (isGcj02Route) {
                CoordinateUtils.wgs84ToGcj02(simState.currentLat, simState.currentLon)
            } else {
                CoordinateUtils.LatLng(simState.currentLat, simState.currentLon)
            }
            mapController.updateSimPosition(displayPt.latitude, displayPt.longitude, simState.currentBearing)
        } else {
            mapController.stopSimPosition()
        }
    }

    // 切换 Tab 时更新地图模式及初始化显示
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            mapController.setMode("single")
            val lat = singleLatText.toDoubleOrNull() ?: 39.908823
            val lon = singleLonText.toDoubleOrNull() ?: 116.397470
            mapController.setSinglePoint(lat, lon)
        } else if (selectedTab == 1) {
            mapController.setMode("route")
            mapController.setRouteWaypoints(waypoints)
            mapController.fitRoute()
        }
    }

    val isAnyMockActive = isMocking || simState.isSimulating

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBg,
                        Color(0xFF0A1020),
                        DarkBgDeep
                    )
                )
            )
    ) {
        // ==================== 星夜极光环境散射光晕 (Ambient Aurora Nebula) ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            (if (isAnyMockActive) TechEmerald else TechBlue).copy(alpha = 0.14f),
                            TechCyan.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(240f, 80f),
                        radius = 720f
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ==================== 流光毛玻璃顶栏 (Luminous Frosted Top Bar) ====================
            Surface(
                color = DarkCard.copy(alpha = 0.86f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.09f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧流光品牌徽标与引擎状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            TechBlue,
                                            TechCyan
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(
                                    text = "LocationMock Pro",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    letterSpacing = (-0.3).sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = TechBlue.copy(alpha = 0.16f),
                                    border = BorderStroke(0.8.dp, TechCyanLight.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { triggerUpdateCheck(true) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "v${UpdateManager.getCurrentVersionName(context)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TechCyanLight
                                        )
                                        if (isCheckingUpdate) {
                                            CircularProgressIndicator(
                                                color = TechCyanLight,
                                                modifier = Modifier.size(8.dp),
                                                strokeWidth = 1.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isRooted) TechEmeraldLight else TechCyanLight)
                                )
                                Text(
                                    text = if (isRooted) "LSPosed 免杀底座已激活" else "高精度动力学引擎已就绪",
                                    fontSize = 11.sp,
                                    color = if (isRooted) TechEmeraldLight.copy(alpha = 0.9f) else TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 右侧呼吸胶囊与安全体检徽章
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(
                            text = when {
                                simState.isSimulating && simState.isPaused -> "巡航挂起"
                                simState.isSimulating -> "巡航推流"
                                isMocking -> "模拟生效"
                                else -> "待命中"
                            },
                            isActive = isAnyMockActive,
                            isPaused = simState.isPaused
                        )

                        // 六维体检快捷入口
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            TechEmerald.copy(alpha = 0.22f),
                                            TechCyan.copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = TechEmeraldLight.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { showAuditDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "安全体检",
                                tint = TechEmeraldLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ==================== 极光悬浮分段导航栏 ====================
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                ModernSegmentedTabs(
                    tabs = listOf("单点定位", "路线巡航", "引擎安全"),
                    icons = listOf(Icons.Default.MyLocation, Icons.Default.Route, Icons.Default.Shield),
                    selectedTabIndex = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            // ==================== Tab 内容区 ====================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 跨 Tab 0 / Tab 1 共享单个高德 WebView 实例，并附带地图底部流光速控条
                if (selectedTab == 0 || selectedTab == 1) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InteractiveMapCard(
                            controller = mapController,
                            mode = if (selectedTab == 0) "single" else "route",
                            height = if (isMapExpanded) 480.dp else if (selectedTab == 0) 295.dp else 280.dp,
                            onWaypointsUpdated = { waypoints = it },
                            onSinglePointSelected = { lat, lon ->
                                singleLatText = "%.6f".format(lat)
                                singleLonText = "%.6f".format(lon)
                            },
                            onToggleExpand = { isMapExpanded = !isMapExpanded }
                        )

                        // 地图下方悬浮流光速控舱 (无需滚到底部即可一键启停与查看实时坐标)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkCardElevated.copy(alpha = 0.92f),
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        (if (isAnyMockActive) TechEmeraldLight else TechCyanLight).copy(alpha = 0.4f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedTab == 0) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GpsFixed,
                                                contentDescription = null,
                                                tint = if (isMocking) TechEmeraldLight else TechCyanLight,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (isMocking) "实时锁止坐标 ($coordSystemSingle)" else "当前锚定坐标 ($coordSystemSingle)",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$singleLatText, $singleLonText",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (isMocking) {
                                                onStopMock()
                                            } else {
                                                val lat = singleLatText.toDoubleOrNull() ?: 39.908823
                                                val lon = singleLonText.toDoubleOrNull() ?: 116.397470
                                                val finalCoords = when (coordSystemSingle) {
                                                    "BD09" -> CoordinateUtils.bd09ToWgs84(lat, lon)
                                                    "GCJ02" -> CoordinateUtils.gcj02ToWgs84(lat, lon)
                                                    else -> CoordinateUtils.LatLng(lat, lon)
                                                }
                                                onStartMock(finalCoords.latitude, finalCoords.longitude, false)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isMocking) TechRose else TechBlue
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isMocking) "停止模拟" else "立即模拟",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    val routeTotalDistKm = remember(waypoints) {
                                        if (waypoints.size < 2) 0.0
                                        else waypoints.zipWithNext { a, b ->
                                            CoordinateUtils.calculateDistance(a.latitude, a.longitude, b.latitude, b.longitude)
                                        }.sum() / 1000.0
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Route,
                                                contentDescription = null,
                                                tint = if (simState.isSimulating) TechEmeraldLight else TechCyanLight,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (simState.isSimulating) "实时巡航推流中" else "当前规划航线概览",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (simState.isSimulating) {
                                                "已行进 ${"%.2f".format(simState.distanceCoveredMeters / 1000.0)} km · ${"%.1f".format(simState.currentSpeedMps * 3.6f)} km/h"
                                            } else {
                                                "${waypoints.size} 个路点 · 全长 ${"%.2f".format(routeTotalDistKm)} km · ${selectedSpeedMode.label}"
                                            },
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (simState.isSimulating) {
                                                onStopRoute()
                                            } else {
                                                onSpeedChanged(currentSpeedKmh / 3.6f)
                                                onStartRoute(waypoints, routeMode, targetDistanceKm?.let { it * 1000.0 }, isGcj02Route)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (simState.isSimulating) TechRose else TechBlue
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (simState.isSimulating) Icons.Default.Stop else Icons.Default.DirectionsRun,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (simState.isSimulating) "结束巡航" else "启动巡航",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            when (selectedTab) {
                0 -> SingleMockTab(
                    mapController = mapController,
                    latText = singleLatText,
                    onLatTextChange = { singleLatText = it },
                    lonText = singleLonText,
                    onLonTextChange = { singleLonText = it },
                    coordSystemSingle = coordSystemSingle,
                    onCoordSystemChange = { coordSystemSingle = it },
                    isMocking = isMocking,
                    onStartMock = onStartMock,
                    onStopMock = onStopMock,
                    favoritePlaces = favoritePlaces,
                    onUpdateFavoritePlaces = { favoritePlaces = it },
                    onOpenAddFavoriteDialog = { lat, lon ->
                        favoriteAddLat = lat
                        favoriteAddLon = lon
                        showAddFavoriteDialog = true
                    }
                )
                1 -> RouteMockTab(
                    mapController = mapController,
                    simState = simState,
                    waypoints = waypoints,
                    onWaypointsChange = { waypoints = it },
                    routeMode = routeMode,
                    onRouteModeChange = { routeMode = it },
                    targetDistanceKm = targetDistanceKm,
                    onTargetDistanceKmChange = { targetDistanceKm = it },
                    isGcj02Route = isGcj02Route,
                    onGcj02RouteChange = { isGcj02Route = it },
                    currentSpeedKmh = currentSpeedKmh,
                    onSpeedChange = {
                        currentSpeedKmh = it
                        onSpeedChanged(it / 3.6f)
                    },
                    selectedSpeedMode = selectedSpeedMode,
                    onSpeedModeChange = { selectedSpeedMode = it },
                    onStartRoute = {
                        onSpeedChanged(currentSpeedKmh / 3.6f)
                        onStartRoute(waypoints, routeMode, targetDistanceKm?.let { it * 1000.0 }, isGcj02Route)
                    },
                    onStopRoute = onStopRoute,
                    onPauseRoute = onPauseRoute,
                    onResumeRoute = onResumeRoute,
                    savedRoutes = savedRoutes,
                    presetRoutes = PRESET_ROUTES,
                    onOpenSaveDialog = { showSaveDialog = true },
                    onOpenGpxDialog = { showGpxDialog = true },
                    onOpenLoopDialog = { showLoopGeneratorDialog = true },
                    onOpenTrackDialog = {
                        originalWaypointsBeforeTrack = waypoints
                        showTrackDialog = true
                    },
                    onLaunchGpxFilePicker = { gpxFileLauncher.launch("*/*") },
                    onUpdateSavedRoutes = { savedRoutes = it }
                )
                2 -> EngineDefenseTab(
                    isRooted = isRooted,
                    onGrantMockViaRoot = onGrantMockViaRoot,
                    isIgnoringBattery = isIgnoringBattery,
                    onRequestIgnoreBattery = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    },
                    onOpenAuditDialog = { showAuditDialog = true },
                    isAntiJumpHighFreq = isAntiJumpHighFreq,
                    onSetAntiJumpHighFreq = {
                        isAntiJumpHighFreq = it
                        onSetAntiJumpHighFreq(it)
                    },
                    onOpenAntiJumpGuide = { showAntiJumpGuideDialog = true },
                    onOpenLocationSettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(context, "请手动前往手机【设置->位置信息】", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enableJitter = enableJitter,
                    onSetJitterEnabled = {
                        enableJitter = it
                        onSetJitterEnabled(it)
                    },
                    selectedJitterProfile = selectedJitterProfile,
                    onSetJitterProfile = {
                        selectedJitterProfile = it
                        onSetJitterProfile(it)
                    },
                    enablePhysiologicalGait = enablePhysiologicalGait,
                    onSetPhysiologicalGait = {
                        enablePhysiologicalGait = it
                        onSetPhysiologicalGait(it)
                    },
                    enableRandomStops = enableRandomStops,
                    onSetRandomStops = {
                        enableRandomStops = it
                        onSetRandomStops(it)
                    },
                    enableFatigueCurve = enableFatigueCurve,
                    onSetFatigueCurve = {
                        enableFatigueCurve = it
                        onSetFatigueCurve(it)
                    },
                    enableTerrainElevation = enableTerrainElevation,
                    onSetTerrainElevation = {
                        enableTerrainElevation = it
                        onSetTerrainElevation(it)
                    },
                    onOpenRadioSpoofDialog = { showRadioSpoofDialog = true },
                    showNmeaTerminal = showNmeaTerminal,
                    onToggleNmeaTerminal = { showNmeaTerminal = !showNmeaTerminal },
                    liveRmc = liveRmc,
                    liveGga = liveGga,
                    liveGsv = liveGsv,
                    liveSats = liveSats,
                    liveCn0 = liveCn0,
                    onCheckUpdate = { triggerUpdateCheck(true) },
                    isCheckingUpdate = isCheckingUpdate
                )
            }
        }
        }
    }

    // ==================== 统一弹窗系统 ====================
    if (showAuditDialog) {
        AuditDialog(
            enableJitter = enableJitter,
            enableRandomStops = enableRandomStops,
            enableFatigueCurve = enableFatigueCurve,
            onDismiss = { showAuditDialog = false }
        )
    }

    if (showRadioSpoofDialog) {
        RadioSpoofDialog(
            onDismiss = { showRadioSpoofDialog = false }
        )
    }

    if (showAntiJumpGuideDialog) {
        AntiJumpGuideDialog(
            onDismiss = { showAntiJumpGuideDialog = false }
        )
    }

    if (showAddFavoriteDialog) {
        AddFavoriteDialog(
            initialLat = favoriteAddLat,
            initialLon = favoriteAddLon,
            onDismiss = { showAddFavoriteDialog = false },
            onAdded = { favoritePlaces = FavoritePlacesHelper.loadFavorites(context) }
        )
    }

    if (showSaveDialog) {
        SaveRouteDialog(
            waypoints = waypoints,
            onDismiss = { showSaveDialog = false },
            onSaved = { savedRoutes = RouteStorageHelper.loadSavedRoutes(context) }
        )
    }

    if (showGpxDialog) {
        GpxImportDialog(
            presetRoutes = PRESET_ROUTES,
            isGcj02Route = isGcj02Route,
            onDismiss = { showGpxDialog = false },
            onImported = { pts ->
                waypoints = pts
                mapController.setRouteWaypoints(pts)
                mapController.fitRoute()
            }
        )
    }

    if (showLoopGeneratorDialog) {
        val center = mapController.currentCenter
            ?: waypoints.firstOrNull()
            ?: CoordinateUtils.LatLng(39.908823, 116.397470)
        LoopGeneratorDialog(
            centerLat = center.latitude,
            centerLon = center.longitude,
            onDismiss = { showLoopGeneratorDialog = false },
            onGenerated = { generated ->
                waypoints = generated
                mapController.setRouteWaypoints(generated)
                mapController.fitRoute()
            }
        )
    }

    if (showTrackDialog) {
        val initialCenter = mapController.currentCenter
            ?: waypoints.firstOrNull()
            ?: CoordinateUtils.LatLng(39.908823, 116.397470)
        Track400mDialog(
            initialCenterLat = initialCenter.latitude,
            initialCenterLon = initialCenter.longitude,
            onDismiss = {
                originalWaypointsBeforeTrack?.let { orig ->
                    waypoints = orig
                    mapController.setRouteWaypoints(orig)
                }
                showTrackDialog = false
            },
            onPreview = { previewPoints ->
                waypoints = previewPoints
                mapController.setRouteWaypoints(previewPoints)
            },
            onConfirm = { finalPoints, targetKm ->
                waypoints = finalPoints
                mapController.setRouteWaypoints(finalPoints)
                mapController.fitRoute()
                routeMode = RouteSimulator.RouteMode.RING_LOOP
                targetDistanceKm = targetKm
                originalWaypointsBeforeTrack = null
                showTrackDialog = false
            }
        )
    }

    if (showUpdateDialog && updateInfoToPrompt != null) {
        UpdateDialog(
            updateInfo = updateInfoToPrompt!!,
            onDismiss = { showUpdateDialog = false },
            onRestartApp = {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(launchIntent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        )
    }
}
