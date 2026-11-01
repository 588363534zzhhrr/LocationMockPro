package com.antigravity.geospoofer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.core.AntiDetectionAuditHelper
import com.antigravity.geospoofer.core.CoordinateUtils
import com.antigravity.geospoofer.core.FavoritePlace
import com.antigravity.geospoofer.core.FavoritePlacesHelper
import com.antigravity.geospoofer.core.GpxExportHelper
import com.antigravity.geospoofer.core.MockLocationEngine
import com.antigravity.geospoofer.core.RoadSnapHelper
import com.antigravity.geospoofer.core.RootHelper
import com.antigravity.geospoofer.core.RouteSimulator
import com.antigravity.geospoofer.core.RouteStorageHelper
import com.antigravity.geospoofer.core.SavedRoute
import com.antigravity.geospoofer.provider.LocationStateProvider
import com.antigravity.geospoofer.service.FloatingJoystickService
import com.antigravity.geospoofer.service.MockLocationService
import com.antigravity.geospoofer.ui.InteractiveMapCard
import com.antigravity.geospoofer.ui.rememberMapController
import com.antigravity.geospoofer.ui.theme.LocationMockProTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
                            val success = RootHelper.grantMockLocationPermissionViaRoot(packageName)
                            Toast.makeText(
                                this,
                                if (success) "Root 授权模拟位置成功！" else "Root 执行失败，请检查授权管理器",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onStartMock = { lat, lon, isGcj02 ->
                            val finalCoords = if (isGcj02) {
                                CoordinateUtils.gcj02ToWgs84(lat, lon)
                            } else {
                                CoordinateUtils.LatLng(lat, lon)
                            }

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
                        onStartRoute = { waypoints, mode, targetMeters, isGcj02 ->
                            if (waypoints.size < 2) {
                                Toast.makeText(this, "路线模拟至少需要 2 个路点", Toast.LENGTH_SHORT).show()
                                return@MainScreen
                            }
                            val finalWaypoints = if (isGcj02) {
                                waypoints.map { CoordinateUtils.gcj02ToWgs84(it.latitude, it.longitude) }
                            } else {
                                waypoints
                            }
                            val success = mockService?.startRoute(finalWaypoints, mode, targetMeters) ?: false
                            if (success) {
                                isMocking = true
                                Toast.makeText(this, "路线模拟仿真已启动", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "启动失败！请在【开发者选项】中勾选本应用为模拟位置应用，或点击上方Root授权",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onStopRoute = {
                            mockService?.stopRoute()
                            Toast.makeText(this, "路线模拟已停止", Toast.LENGTH_SHORT).show()
                        },
                        onPauseRoute = {
                            mockService?.pauseRoute()
                        },
                        onResumeRoute = {
                            mockService?.resumeRoute()
                        },
                        onToggleJoystick = { enable ->
                            if (enable) {
                                if (checkOverlayPermission()) {
                                    startService(Intent(this, FloatingJoystickService::class.java))
                                } else {
                                    requestOverlayPermission()
                                }
                            } else {
                                stopService(Intent(this, FloatingJoystickService::class.java))
                            }
                        },
                        onSpeedChanged = { speed ->
                            mockService?.updateSpeed(speed)
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

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}

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
            CoordinateUtils.LatLng(30.259200, 120.149500), // 断桥
            CoordinateUtils.LatLng(30.254100, 120.142000), // 白堤
            CoordinateUtils.LatLng(30.251200, 120.136800), // 平湖秋月
            CoordinateUtils.LatLng(30.246500, 120.131200), // 孤山
            CoordinateUtils.LatLng(30.238000, 120.128500)  // 苏堤
        )
    )
)

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
    onToggleJoystick: (Boolean) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onSetJitterEnabled: (Boolean) -> Unit,
    onSetJitterProfile: (MockLocationEngine.JitterProfile) -> Unit,
    onSetPhysiologicalGait: (Boolean) -> Unit,
    onSetRandomStops: (Boolean) -> Unit,
    onSetFatigueCurve: (Boolean) -> Unit,
    onSetTerrainElevation: (Boolean) -> Unit = {},
    engine: MockLocationEngine? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSnappingRoad by remember { mutableStateOf(false) }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 单点模拟, 1: 路线模拟

    // 地图与视图状态
    var isMapExpanded by remember { mutableStateOf(false) }
    val mapController = rememberMapController()

    // 常用打卡地点收藏夹
    var favoritePlaces by remember { mutableStateOf(FavoritePlacesHelper.loadFavorites(context)) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteNameInput by remember { mutableStateOf("") }
    var favoriteTagInput by remember { mutableStateOf("常去") }

    // 单点模拟状态
    var latText by remember { mutableStateOf("39.908823") }
    var lonText by remember { mutableStateOf("116.397470") }
    var isGcj02Single by remember { mutableStateOf(true) }
    var coordSystemSingle by remember { mutableStateOf("GCJ02") }
    var joystickEnabled by remember { mutableStateOf(false) }

    // 防检测体检、NMEA 终端与地形海拔起伏
    var enableTerrainElevation by remember { mutableStateOf(true) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var showRadioSpoofDialog by remember { mutableStateOf(false) }
    var showNmeaTerminal by remember { mutableStateOf(false) }

    // Wi-Fi 与基站伪装状态
    var wifiMockEnabled by remember { mutableStateOf(LocationStateProvider.wifiMockEnabled) }
    var wifiSsidInput by remember { mutableStateOf(LocationStateProvider.wifiSsid) }
    var wifiBssidInput by remember { mutableStateOf(LocationStateProvider.wifiBssid) }
    var wifiRssiInput by remember { mutableStateOf(LocationStateProvider.wifiRssi.toString()) }

    var cellMockEnabled by remember { mutableStateOf(LocationStateProvider.cellMockEnabled) }
    var cellMccInput by remember { mutableStateOf(LocationStateProvider.cellMcc.toString()) }
    var cellMncInput by remember { mutableStateOf(LocationStateProvider.cellMnc.toString()) }
    var cellLacInput by remember { mutableStateOf(LocationStateProvider.cellLac.toString()) }
    var cellCidInput by remember { mutableStateOf(LocationStateProvider.cellCid.toString()) }

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
    var newLatText by remember { mutableStateOf("") }
    var newLonText by remember { mutableStateOf("") }

    // 仿真参数与防检测
    var enableJitter by remember { mutableStateOf(true) }
    var selectedJitterProfile by remember { mutableStateOf(MockLocationEngine.JitterProfile.OPEN_SKY) }
    var enablePhysiologicalGait by remember { mutableStateOf(true) }
    var enableRandomStops by remember { mutableStateOf(false) }
    var enableFatigueCurve by remember { mutableStateOf(false) }
    var currentSpeedKmh by remember { mutableFloatStateOf(12f) }
    var selectedSpeedMode by remember { mutableStateOf(RouteSimulator.SpeedMode.WALK) }

    // 智能环形跑规划弹窗
    var showLoopGeneratorDialog by remember { mutableStateOf(false) }
    var loopDistanceKmText by remember { mutableStateOf("2.0") }

    // 路线持久化管理与 GPX 文件选择器
    var savedRoutes by remember { mutableStateOf(RouteStorageHelper.loadSavedRoutes(context)) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }
    var showGpxDialog by remember { mutableStateOf(false) }
    var gpxXmlInput by remember { mutableStateOf("") }

    val gpxFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (!content.isNullOrBlank()) {
                    val pts = RouteStorageHelper.importFromGpx(content)
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

    // 监听模拟运行状态，实时驱动地图车标与航向
    LaunchedEffect(simState.isSimulating, simState.currentLat, simState.currentLon, simState.currentBearing) {
        if (simState.isSimulating) {
            mapController.updateSimPosition(simState.currentLat, simState.currentLon, simState.currentBearing)
        } else {
            mapController.stopSimPosition()
        }
    }

    // 切换 Tab 时更新地图模式及初始化显示
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            mapController.setMode("single")
            val lat = latText.toDoubleOrNull() ?: 39.908823
            val lon = lonText.toDoubleOrNull() ?: 116.397470
            mapController.setSinglePoint(lat, lon)
        } else {
            mapController.setMode("route")
            mapController.setRouteWaypoints(waypoints)
            mapController.fitRoute()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部标题与运行状态指示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LocationMock Pro",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (isMocking) {
                Surface(
                    color = if (simState.isPaused) Color(0xFFF57C00) else Color(0xFF2E7D32),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (simState.isSimulating) {
                            if (simState.isPaused) "⏸️ 巡航已暂停" else "● 路线巡航中"
                        } else "● 单点模拟中",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 功能标签栏 TabRow
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("📍 定位模拟", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("🛣️ 路线模拟", fontWeight = FontWeight.SemiBold) }
            )
        }

        // 🗺️ 可视化交互地图卡片 (支持搜索/道路吸附/全览/全屏)
        InteractiveMapCard(
            controller = mapController,
            mode = if (selectedTab == 0) "single" else "route",
            height = if (isMapExpanded) 520.dp else 360.dp,
            onSinglePointSelected = { lat, lon ->
                latText = "%.6f".format(lat)
                lonText = "%.6f".format(lon)
            },
            onWaypointsUpdated = { updatedList ->
                waypoints = updatedList
            },
            onToggleExpand = {
                isMapExpanded = !isMapExpanded
            }
        )

        // Root 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRooted) Color(0xFF1E3A24) else Color(0xFF2C2C2C)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRooted) Icons.Default.Security else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isRooted) Color(0xFF4CAF50) else Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRooted) "Root: 已检测到 SU 权限" else "Root: 未激活（已自动适配免Root模式）",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isRooted) {
                    Button(
                        onClick = onGrantMockViaRoot,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Root 提权", fontSize = 11.sp)
                    }
                }
            }
        }

        // 🪟 全局灵动微型悬浮控制岛开关
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🪟 全局灵动微型悬浮控制岛", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("在微信、游戏或打卡软件上方悬浮监控里程时速，支持即时调速与方向摇杆", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = joystickEnabled,
                    onCheckedChange = {
                        joystickEnabled = it
                        onToggleJoystick(it)
                    }
                )
            }
        }

        // 🛡️ 防检测、保活与运动动力学增强设置卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. 忽略电池优化（防系统休眠杀后台）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚡ 后台无休眠常驻 (忽略电池优化)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (isIgnoringBattery) "已激活系统白名单，长时间后台巡航不被杀进程" else "建议开启，避免灭屏休眠时模拟服务被系统挂起",
                            fontSize = 11.sp,
                            color = if (isIgnoringBattery) Color(0xFF81C784) else Color.Gray
                        )
                    }
                    if (isIgnoringBattery) {
                        Surface(
                            color = Color(0xFF1B5E20),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("已保护", color = Color(0xFF81C784), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    } else {
                        Button(
                            onClick = {
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("加白名单", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF333333))

                // 2. 自然物理微抖动与信号环境预设
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🛡️ 自然 GPS 高斯微抖动 (防检测)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("自动注入真实卫星噪点与 NMEA 星历 Bundle，彻底避免绝对直线/静止被风控判定", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = enableJitter,
                        onCheckedChange = {
                            enableJitter = it
                            onSetJitterEnabled(it)
                        }
                    )
                }

                if (enableJitter) {
                    Text("信号扰动环境预设:", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MockLocationEngine.JitterProfile.values().filter { it != MockLocationEngine.JitterProfile.NONE }.forEach { prof ->
                            FilterChip(
                                selected = selectedJitterProfile == prof,
                                onClick = {
                                    selectedJitterProfile = prof
                                    onSetJitterProfile(prof)
                                },
                                label = { Text(prof.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF333333))

                // 3. 人类步态生理波动与转弯减速拟真
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🚴 人类步态生理波动与转弯减速拟真", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("模拟步伐呼吸周期微震荡，直角大弯前 12 米平滑减速入弯", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = enablePhysiologicalGait,
                        onCheckedChange = {
                            enablePhysiologicalGait = it
                            onSetPhysiologicalGait(it)
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF333333))

                // 4. 拟真等红绿灯与随机路口驻留
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🚦 拟真等红绿灯与路口驻留 (防风控)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("每 600~1000m 或路口随机停留 10~20 秒，杜绝全程不减速被 AI 判外挂", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = enableRandomStops,
                        onCheckedChange = {
                            enableRandomStops = it
                            onSetRandomStops(it)
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF333333))

                // 5. 真实体能消耗与配速衰减波形
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📈 真实体能消耗与配速衰减波形", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("随行进里程模拟真实体能衰减曲线，Keep/悦跑圈配速图表完美拟真", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = enableFatigueCurve,
                        onCheckedChange = {
                            enableFatigueCurve = it
                            onSetFatigueCurve(it)
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF333333))

                // 6. 真实地形海拔起伏与坡度拟真
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⛰️ 真实地形海拔起伏与坡度拟真", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("模拟自然道路微坡度升降与累计爬升，生成 Keep/Strava 爬坡高度数据", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = enableTerrainElevation,
                        onCheckedChange = {
                            enableTerrainElevation = it
                            onSetTerrainElevation(it)
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF333333))

                // 7. 防检测全维体检报告、Wi-Fi/基站伪装与 NMEA 卫星终端
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showAuditDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1B3A24))
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🛡️ 防检测体检", fontSize = 12.sp, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { showRadioSpoofDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1A2A3A))
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📶 Wi-Fi/基站伪装", fontSize = 12.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { showNmeaTerminal = !showNmeaTerminal },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showNmeaTerminal) "收起 NMEA 终端" else "🛰️ NMEA 终端与卫星数据流", fontSize = 12.sp)
                }

                if (showNmeaTerminal) {
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(color = Color(0xFF238636), shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                                    Text("NMEA-0183 硬件卫星数据流", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF58A6FF))
                                }
                                Text("🟢 卫星: ${liveSats} 颗 | SNR: ${"%.1f".format(liveCn0)} dB-Hz", fontSize = 11.sp, color = Color(0xFF7EE787))
                            }

                            Surface(
                                color = Color(0xFF000000),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = if (liveRmc.isNotBlank()) liveRmc else "\$GPRMC (待命中，启动模拟后将实时脉冲)",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF00FF66)
                                    )
                                    Text(
                                        text = if (liveGga.isNotBlank()) liveGga else "\$GPGGA (待命中，启动模拟后将实时脉冲)",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                    if (liveGsv.isNotBlank()) {
                                        Text(
                                            text = liveGsv,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== TAB 0: 单点位置模拟 ====================
        if (selectedTab == 0) {
            // ⭐ 常用打卡地点收藏卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐ 常用打卡地点收藏 (点击即秒级瞬移)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        TextButton(
                            onClick = {
                                val currentLat = latText.toDoubleOrNull() ?: 39.908823
                                val currentLon = lonText.toDoubleOrNull() ?: 116.397470
                                favoriteNameInput = "打卡点 (${"%.4f".format(currentLat)}, ${"%.4f".format(currentLon)})"
                                showAddFavoriteDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("+ 收藏当前选点", fontSize = 12.sp)
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(favoritePlaces) { place ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2C2C2C),
                                modifier = Modifier.clickable {
                                    latText = place.latitude.toString()
                                    lonText = place.longitude.toString()
                                    mapController.setSinglePoint(place.latitude, place.longitude)
                                    mapController.flyToLocation(place.latitude, place.longitude)
                                    if (isMocking) {
                                        onStartMock(place.latitude, place.longitude, isGcj02Single)
                                    }
                                    Toast.makeText(context, "已秒移至: ${place.name}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            place.tag,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(place.name, fontSize = 12.sp, color = Color.White)
                                    IconButton(
                                        onClick = {
                                            favoritePlaces = FavoritePlacesHelper.deleteFavorite(context, place.id)
                                        },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("单点目标坐标", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                    OutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            it.toDoubleOrNull()?.let { lat ->
                                lonText.toDoubleOrNull()?.let { lon ->
                                    mapController.setSinglePoint(lat, lon)
                                }
                            }
                        },
                        label = { Text("纬度 (Latitude)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = lonText,
                        onValueChange = {
                            lonText = it
                            latText.toDoubleOrNull()?.let { lat ->
                                it.toDoubleOrNull()?.let { lon ->
                                    mapController.setSinglePoint(lat, lon)
                                }
                            }
                        },
                        label = { Text("经度 (Longitude)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("输入坐标系标准 (自动适配校准):", fontSize = 12.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = coordSystemSingle == "GCJ02",
                            onClick = {
                                coordSystemSingle = "GCJ02"
                                isGcj02Single = true
                            },
                            label = { Text("高德/腾讯 (GCJ-02)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = coordSystemSingle == "WGS84",
                            onClick = {
                                coordSystemSingle = "WGS84"
                                isGcj02Single = false
                            },
                            label = { Text("GPS标准 (WGS-84)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = coordSystemSingle == "BD09",
                            onClick = {
                                coordSystemSingle = "BD09"
                                isGcj02Single = false
                            },
                            label = { Text("百度地图 (BD-09)", fontSize = 11.sp) }
                        )
                    }

                    Text("快捷地点:", fontSize = 12.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = latText == "39.908823",
                            onClick = {
                                latText = "39.908823"
                                lonText = "116.397470"
                                mapController.setSinglePoint(39.908823, 116.397470)
                            },
                            label = { Text("北京") }
                        )
                        FilterChip(
                            selected = latText == "31.239688",
                            onClick = {
                                latText = "31.239688"
                                lonText = "121.499763"
                                mapController.setSinglePoint(31.239688, 121.499763)
                            },
                            label = { Text("上海外滩") }
                        )
                        FilterChip(
                            selected = latText == "22.540503",
                            onClick = {
                                latText = "22.540503"
                                lonText = "113.934528"
                                mapController.setSinglePoint(22.540503, 113.934528)
                            },
                            label = { Text("深圳南山") }
                        )
                    }
                }
            }

            // 单点模拟控制大按钮
            Button(
                onClick = {
                    if (isMocking) {
                        onStopMock()
                    } else {
                        val lat = latText.toDoubleOrNull() ?: 39.908823
                        val lon = lonText.toDoubleOrNull() ?: 116.397470
                        val finalCoords = when (coordSystemSingle) {
                            "BD09" -> CoordinateUtils.bd09ToWgs84(lat, lon)
                            "GCJ02" -> CoordinateUtils.gcj02ToWgs84(lat, lon)
                            else -> CoordinateUtils.LatLng(lat, lon)
                        }
                        onStartMock(finalCoords.latitude, finalCoords.longitude, false)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMocking) "停止定位模拟" else "开始定位模拟",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ==================== TAB 1: 路线模拟 ====================
        if (selectedTab == 1) {
            // 实时运动状态仪表盘与动态无级变速控制
            if (simState.isSimulating) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF132F20))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (simState.isPaused) "⏸️ 巡航已暂停 (心跳保持)" else "🛣️ 实时运动仿真中",
                                fontWeight = FontWeight.Bold,
                                color = if (simState.isPaused) Color(0xFFFFB74D) else Color(0xFF81C784)
                            )
                            Text(
                                "第 ${simState.currentSegmentIndex} / ${simState.totalSegments} 段",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }

                        LinearProgressIndicator(
                            progress = { simState.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (simState.isPaused) Color(0xFFFFB74D) else Color(0xFF4CAF50),
                            trackColor = Color(0xFF1B5E20)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("已行进: ${"%.2f".format(simState.distanceCoveredMeters / 1000.0)} km", fontSize = 12.sp, color = Color.White)
                            Text("总里程: ${"%.2f".format(simState.totalDistanceMeters / 1000.0)} km", fontSize = 12.sp, color = Color.White)
                            Text("航向: ${"%.1f".format(simState.currentBearing)}°", fontSize = 12.sp, color = Color.White)
                        }

                        // 🏆 达成目标里程/时长提示卡片
                        if (simState.isGoalReached) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1B5E20), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("🏆", fontSize = 16.sp)
                                    Text(simState.goalSummary, color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("平缓驻留守护", color = Color(0xFFA5D6A7), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 🚦 拟真等红绿灯与路口驻留倒计时提示
                        if (simState.isTemporarilyStopped) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF4E2C00), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("🚦", fontSize = 14.sp)
                                    Text("拟真驻留: ${simState.stopReason}", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("倒计时 ${simState.stopCountdownSeconds}s", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 🏃 硬件计步器与步频联动指标
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1B3828), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🏃 步数: ${simState.totalSteps} 步", fontSize = 12.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                            Text("⚡ 步频: ${simState.cadenceSpm} SPM", fontSize = 12.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                            Text("🔥 消耗: ${simState.caloriesKcal} kcal", fontSize = 12.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                        }

                        // ⛰️ 地形海拔与累计爬升指标
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF142B20), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("⛰️ 海拔: ${"%.1f".format(simState.currentElevationMeters)} m", fontSize = 12.sp, color = Color(0xFF81D4FA), fontWeight = FontWeight.Medium)
                            Text("📈 累计爬升: +${"%.1f".format(simState.elevationGainMeters)} m", fontSize = 12.sp, color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "当前坐标: ${"%.6f".format(simState.currentLat)}, ${"%.6f".format(simState.currentLon)}",
                            fontSize = 12.sp,
                            color = Color(0xFFA5D6A7),
                            fontWeight = FontWeight.Medium
                        )

                        // 动态无级调速滑块
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("动态时速调节:", fontSize = 12.sp, color = Color.White)
                            Text("${currentSpeedKmh.toInt()} km/h (${"%.1f".format(currentSpeedKmh / 3.6f)} m/s)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                        }
                        Slider(
                            value = currentSpeedKmh,
                            onValueChange = {
                                currentSpeedKmh = it
                                onSpeedChanged(it / 3.6f)
                            },
                            valueRange = 1f..100f
                        )

                        // 暂停与停止按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (simState.isPaused) onResumeRoute() else onPauseRoute()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (simState.isPaused) Color(0xFF388E3C) else Color(0xFFF57C00)
                                )
                            ) {
                                Text(if (simState.isPaused) "▶️ 继续巡航" else "⏸️ 暂停模拟")
                            }
                            Button(
                                onClick = onStopRoute,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("⏹️ 停止路线")
                            }
                        }

                        // 悬浮窗与轨迹导出快捷操作
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    joystickEnabled = !joystickEnabled
                                    onToggleJoystick(joystickEnabled)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(if (joystickEnabled) "🪟 关闭悬浮窗" else "🪟 开启灵动悬浮窗", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (waypoints.isNotEmpty()) {
                                        val ok = GpxExportHelper.exportAndShareGpx(
                                            context,
                                            waypoints,
                                            "运动实时轨迹",
                                            simState.currentSpeedMps
                                        )
                                        if (!ok) {
                                            GpxExportHelper.copyGpxToClipboard(
                                                context,
                                                waypoints,
                                                "运动实时轨迹",
                                                simState.currentSpeedMps
                                            )
                                            Toast.makeText(context, "已复制 GPX XML 至剪贴板", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("📤 导出实时GPX", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 速度档位快捷调节
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("常用运动档位", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(selectedSpeedMode.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RouteSimulator.SpeedMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedSpeedMode == mode,
                                onClick = {
                                    selectedSpeedMode = mode
                                    currentSpeedKmh = mode.speedMps * 3.6f
                                    onSpeedChanged(mode.speedMps)
                                },
                                label = { Text(mode.name) }
                            )
                        }
                    }
                }
            }

            // 💾 路线保存、收藏夹与 GPX 导入导出卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("路线收藏与数据管理", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("${savedRoutes.size} 条已存", fontSize = 12.sp, color = Color.Gray)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                saveNameInput = "自定义路线 (${waypoints.size} 点)"
                                showSaveDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text("💾 收藏路线", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { gpxFileLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text("📂 选GPX", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { showGpxDialog = true },
                            modifier = Modifier.weight(0.9f),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text("📥 粘贴", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                if (waypoints.isNotEmpty()) {
                                    val ok = GpxExportHelper.exportAndShareGpx(
                                        context,
                                        waypoints,
                                        saveNameInput.ifBlank { "路线规划" },
                                        simState.currentSpeedMps
                                    )
                                    if (!ok) {
                                        GpxExportHelper.copyGpxToClipboard(
                                            context,
                                            waypoints,
                                            saveNameInput.ifBlank { "路线规划" },
                                            simState.currentSpeedMps
                                        )
                                        Toast.makeText(context, "已复制 GPX XML 文本至剪贴板", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "暂无路点可导出", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text("📤 导出GPX", fontSize = 11.sp)
                        }
                    }

                    // 已保存的自定义路线列表
                    if (savedRoutes.isNotEmpty()) {
                        savedRoutes.forEach { route ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF242424), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(route.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text(
                                        "${route.waypoints.size} 点 · ${"%.2f".format(route.totalDistanceMeters / 1000.0)} km",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            waypoints = route.waypoints
                                            mapController.setRouteWaypoints(route.waypoints)
                                            mapController.fitRoute()
                                            Toast.makeText(context, "已载入: ${route.name}", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("载入", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            val gpx = RouteStorageHelper.exportToGpx(route.name, route.waypoints)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("GPX", gpx))
                                            Toast.makeText(context, "GPX XML 已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "导出", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            RouteStorageHelper.deleteRoute(context, route.id)
                                            savedRoutes = RouteStorageHelper.loadSavedRoutes(context)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 预设经典路线选择卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("经典预设路线（点击即载入）", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    PRESET_ROUTES.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                waypoints = preset.waypoints
                                mapController.setRouteWaypoints(preset.waypoints)
                                mapController.fitRoute()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text(preset.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(preset.description, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // 路线巡航与自动化配置卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("路线巡航与目标自动化配置", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                    // 1. 巡航模式选择
                    Text("巡航模式选择:", fontSize = 13.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RouteSimulator.RouteMode.values().forEach { mode ->
                            val isSelected = routeMode == mode
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C2C2C),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { routeMode = mode }
                            ) {
                                Text(
                                    text = mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // 2. 目标里程守护 (自动刹车)
                    Text("目标里程守护 (到达后平缓刹车驻留):", fontSize = 13.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val targets = listOf(
                            Pair("🎯 无上限", null),
                            Pair("1.0 km", 1.0),
                            Pair("3.0 km", 3.0),
                            Pair("5.0 km", 5.0),
                            Pair("10.0 km", 10.0)
                        )
                        targets.forEach { (label, distKm) ->
                            val isSelected = targetDistanceKm == distKm
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF2E7D32) else Color(0xFF2C2C2C),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF4CAF50) else Color.Gray),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { targetDistanceKm = distKm }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isGcj02Route, onCheckedChange = { isGcj02Route = it })
                        Text("路点均为高德/腾讯火星坐标 (自动转 WGS-84)", fontSize = 13.sp)
                    }
                }
            }

            // 🔄 路点列表展示、高级编辑工具箱与管理
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("途径路点列表 (${waypoints.size} 点)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // 🛣️ 真实道路沿路吸附规划器
                            TextButton(
                                onClick = {
                                    if (waypoints.size >= 2) {
                                        isSnappingRoad = true
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在沿真实街道/人行道规划贴合路径...", Toast.LENGTH_SHORT).show()
                                            val result = RoadSnapHelper.snapWaypointsToRoad(waypoints, RoadSnapHelper.SnapProfile.WALKING)
                                            result.onSuccess { snapped ->
                                                waypoints = snapped
                                                mapController.setRouteWaypoints(snapped)
                                                mapController.fitRoute()
                                                Toast.makeText(context, "已成功沿真实道路吸附生成 ${snapped.size} 个平滑路点！", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, "道路吸附规划失败，保持原路线: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                            isSnappingRoad = false
                                        }
                                    } else {
                                        Toast.makeText(context, "至少需要 2 个路点才能进行道路吸附规划", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isSnappingRoad,
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(if (isSnappingRoad) "⏳ 吸附中" else "🛣️ 沿路贴合", fontSize = 12.sp)
                            }
                            // 🎲 智能环形跑规划器
                            TextButton(
                                onClick = { showLoopGeneratorDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("🎲 环形跑", fontSize = 12.sp)
                            }
                            // 🔄 原路折返
                            TextButton(
                                onClick = {
                                    if (waypoints.size >= 2) {
                                        val reversed = waypoints.dropLast(1).reversed()
                                        val roundTrip = waypoints + reversed
                                        waypoints = roundTrip
                                        mapController.setRouteWaypoints(roundTrip)
                                        mapController.fitRoute()
                                        Toast.makeText(context, "已生成原路折返闭环！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("🔄 折返", fontSize = 12.sp)
                            }
                            // 🔁 反转起点终点
                            TextButton(
                                onClick = {
                                    if (waypoints.size >= 2) {
                                        val reversed = waypoints.reversed()
                                        waypoints = reversed
                                        mapController.setRouteWaypoints(reversed)
                                        mapController.fitRoute()
                                        Toast.makeText(context, "已反转起点与终点！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("🔁 反转", fontSize = 12.sp)
                            }
                            // 清空
                            TextButton(
                                onClick = {
                                    waypoints = emptyList()
                                    mapController.setRouteWaypoints(emptyList())
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("清空", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    waypoints.forEachIndexed { index, point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF232323), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (index == 0) Color(0xFF4CAF50) else if (index == waypoints.size - 1) Color(0xFFE53935) else Color(0xFF1976D2),
                                    shape = CircleShape,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (index == 0) "起" else if (index == waypoints.size - 1) "终" else "$index",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${"%.6f".format(point.latitude)}, ${"%.6f".format(point.longitude)}",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 向上微调顺序
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val list = waypoints.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index - 1]
                                            list[index - 1] = temp
                                            waypoints = list
                                            mapController.setRouteWaypoints(list)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                    }
                                }
                                // 向下微调顺序
                                if (index < waypoints.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val list = waypoints.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index + 1]
                                            list[index + 1] = temp
                                            waypoints = list
                                            mapController.setRouteWaypoints(list)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val updated = waypoints.filterIndexed { i, _ -> i != index }
                                        waypoints = updated
                                        mapController.setRouteWaypoints(updated)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 添加自定义路点
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newLatText,
                            onValueChange = { newLatText = it },
                            label = { Text("纬度") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newLonText,
                            onValueChange = { newLonText = it },
                            label = { Text("经度") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val lat = newLatText.toDoubleOrNull()
                                val lon = newLonText.toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    val updated = waypoints + CoordinateUtils.LatLng(lat, lon)
                                    waypoints = updated
                                    mapController.setRouteWaypoints(updated)
                                    mapController.fitRoute()
                                    newLatText = ""
                                    newLonText = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("添加")
                        }
                    }
                }
            }

            // 路线模拟控制大按钮
            if (!simState.isSimulating) {
                Button(
                    onClick = {
                        onStartRoute(waypoints, routeMode, targetDistanceKm?.let { it * 1000.0 }, isGcj02Route)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "开始路线模拟仿真",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // 保存路线弹窗
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存当前规划路线") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前路线共包含 ${waypoints.size} 个途经路点。", fontSize = 13.sp)
                    OutlinedTextField(
                        value = saveNameInput,
                        onValueChange = { saveNameInput = it },
                        label = { Text("路线名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveNameInput.isNotBlank()) {
                            RouteStorageHelper.saveRoute(context, saveNameInput.trim(), waypoints)
                            savedRoutes = RouteStorageHelper.loadSavedRoutes(context)
                            showSaveDialog = false
                            Toast.makeText(context, "路线已保存到收藏夹", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // GPX 导入与示例弹窗
    if (showGpxDialog) {
        AlertDialog(
            onDismissRequest = { showGpxDialog = false },
            title = { Text("导入 GPX 轨迹文件") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("可直接粘贴外部运动轨迹 XML，或载入预设高精轨迹：", fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val sample = PRESET_ROUTES[0].waypoints
                                waypoints = sample
                                mapController.setRouteWaypoints(sample)
                                mapController.fitRoute()
                                showGpxDialog = false
                                Toast.makeText(context, "已载入奥森高精跑步轨迹", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("奥森 5km", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val sample = PRESET_ROUTES[3].waypoints
                                waypoints = sample
                                mapController.setRouteWaypoints(sample)
                                mapController.fitRoute()
                                showGpxDialog = false
                                Toast.makeText(context, "已载入西湖漫步轨迹", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("西湖 3.8km", fontSize = 11.sp)
                        }
                    }
                    OutlinedTextField(
                        value = gpxXmlInput,
                        onValueChange = { gpxXmlInput = it },
                        label = { Text("或在此粘贴 <gpx> 内容") },
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
                                waypoints = pts
                                mapController.setRouteWaypoints(pts)
                                mapController.fitRoute()
                                showGpxDialog = false
                                Toast.makeText(context, "成功解析并导入 ${pts.size} 个轨迹点！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "未能解析到有效 trkpt/wpt 坐标", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("解析导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpxDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 🎲 一键智能生成环形跑路线弹窗
    if (showLoopGeneratorDialog) {
        AlertDialog(
            onDismissRequest = { showLoopGeneratorDialog = false },
            title = { Text("🎲 一键智能生成环形跑路线") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("以当前位置或地图选点为圆心，自动规划闭合跑步/骑行环线，专为校园跑打卡及日常体测设计。", fontSize = 13.sp, color = Color.LightGray)

                    Text("快捷目标里程:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val km = loopDistanceKmText.toDoubleOrNull() ?: 2.0
                        val meters = km * 1000.0
                        val center = if (waypoints.isNotEmpty()) {
                            waypoints.first()
                        } else {
                            val cLat = latText.toDoubleOrNull() ?: 39.908823
                            val cLon = lonText.toDoubleOrNull() ?: 116.397470
                            CoordinateUtils.LatLng(cLat, cLon)
                        }

                        val generated = CoordinateUtils.generateOrganicLoopRoute(
                            centerLat = center.latitude,
                            centerLon = center.longitude,
                            targetDistanceMeters = meters,
                            numPoints = 12,
                            irregularity = 0.22
                        )
                        waypoints = generated
                        mapController.setRouteWaypoints(generated)
                        mapController.fitRoute()
                        showLoopGeneratorDialog = false
                        Toast.makeText(context, "已智能规划 ${"%.2f".format(km)} km 闭环拟真路线！", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("立即生成")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoopGeneratorDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ⭐ 收藏常用地点弹窗
    if (showAddFavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showAddFavoriteDialog = false },
            title = { Text("⭐ 收藏为常用地点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val currentLat = latText.toDoubleOrNull() ?: 39.908823
                    val currentLon = lonText.toDoubleOrNull() ?: 116.397470
                    Text("坐标: ${"%.6f".format(currentLat)}, ${"%.6f".format(currentLon)}", fontSize = 12.sp, color = Color.Gray)

                    OutlinedTextField(
                        value = favoriteNameInput,
                        onValueChange = { favoriteNameInput = it },
                        label = { Text("地点名称 (例如: 公司工位/学校田径场)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = favoriteTagInput,
                        onValueChange = { favoriteTagInput = it },
                        label = { Text("标签 (例如: 打卡/常去/家/跑道)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentLat = latText.toDoubleOrNull() ?: 39.908823
                        val currentLon = lonText.toDoubleOrNull() ?: 116.397470
                        if (favoriteNameInput.isNotBlank()) {
                            favoritePlaces = FavoritePlacesHelper.addFavorite(
                                context = context,
                                name = favoriteNameInput.trim(),
                                lat = currentLat,
                                lon = currentLon,
                                tag = favoriteTagInput.trim().ifEmpty { "常去" }
                            )
                            showAddFavoriteDialog = false
                            Toast.makeText(context, "已收藏地点: $favoriteNameInput", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("确认收藏")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFavoriteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 🛡️ 防检测安全体检报告弹窗
    if (showAuditDialog) {
        val auditReport = remember(enableJitter, enableRandomStops, enableFatigueCurve) {
            AntiDetectionAuditHelper.performAudit(
                context = context,
                isJitterActive = enableJitter,
                isStopsActive = enableRandomStops,
                isFatigueActive = enableFatigueCurve
            )
        }

        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛡️ 防检测安全体检报告", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                        color = Color(auditReport.gradeColorHex).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(auditReport.gradeColorHex))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(auditReport.gradeTitle, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text("基于 Android 底层 Mock 机制与生理特征 6 维审计", fontSize = 10.sp, color = Color.LightGray)
                            }
                            Text(
                                "${auditReport.safetyScore}分",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = Color(auditReport.gradeColorHex)
                            )
                        }
                    }

                    // 审计子项列表
                    auditReport.items.forEach { item ->
                        Surface(
                            color = Color(0xFF262626),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                                    Text(
                                        if (item.isPassed) "✅ 通过" else "⚠️ 警告",
                                        color = if (item.isPassed) Color(0xFF81C784) else Color(0xFFFFB74D),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("状态: ${item.statusText}", fontSize = 11.sp, color = Color(0xFFA5D6A7))
                                Text(item.suggestion, fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                    }

                    // LSPosed 免杀代码与指导说明
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF444444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💉 LSPosed 极客免杀 Hook 规则", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF64B5F6))
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("LSPosed Hook", AntiDetectionAuditHelper.getLsposedHookGuideSnippet())
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "已复制 LSPosed 免杀 Hook 源码到剪贴板！", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("复制源码", fontSize = 10.sp)
                                }
                            }
                            Text(
                                "对 Keep / 钉钉 等风控严苛的 App，可通过 LSPosed 模块直接拦截 Location.isFromMockProvider()，使其永久返回 false 达到 100% 完美隐形！",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAuditDialog = false }) {
                    Text("完成体检")
                }
            }
        )
    }

    // 📶 企业 Wi-Fi 与移动基站环境无线电指纹伪装弹窗
    if (showRadioSpoofDialog) {
        AlertDialog(
            onDismissRequest = { showRadioSpoofDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF64B5F6))
                    Text("📶 Wi-Fi 考勤与基站伪装", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "绕过钉钉、企业微信等大厂考勤风控系统对办公室内 Wi-Fi BSSID 与基站 LAC/CID 的硬件环境比对。",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    // 1. Wi-Fi 伪装卡片
                    Surface(
                        color = Color(0xFF1E2630),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF2C3E50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏢 启用 Wi-Fi 考勤伪装", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                Switch(
                                    checked = wifiMockEnabled,
                                    onCheckedChange = { wifiMockEnabled = it }
                                )
                            }

                            if (wifiMockEnabled) {
                                OutlinedTextField(
                                    value = wifiSsidInput,
                                    onValueChange = { wifiSsidInput = it },
                                    label = { Text("目标 Wi-Fi 名称 (SSID)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = wifiBssidInput,
                                    onValueChange = { wifiBssidInput = it },
                                    label = { Text("目标 MAC 地址 (BSSID)") },
                                    placeholder = { Text("如 00:1a:2b:3c:4d:5e") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = wifiRssiInput,
                                    onValueChange = { wifiRssiInput = it },
                                    label = { Text("信号强度 RSSI (dBm)") },
                                    placeholder = { Text("如 -48") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            wifiSsidInput = "Corporate-Office-5G"
                                            wifiBssidInput = "00:1a:2b:3c:4d:5e"
                                            wifiRssiInput = "-48"
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Text("预设: 企业5G", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            wifiSsidInput = "Tech-Park-FreeWiFi"
                                            wifiBssidInput = "74:05:a5:88:21:bb"
                                            wifiRssiInput = "-55"
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Text("预设: 园区打卡", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 2. 蜂窝基站伪装卡片
                    Surface(
                        color = Color(0xFF251E28),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF3E2C48)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🗼 启用蜂窝基站伪装", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                Switch(
                                    checked = cellMockEnabled,
                                    onCheckedChange = { cellMockEnabled = it }
                                )
                            }

                            if (cellMockEnabled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = cellMccInput,
                                        onValueChange = { cellMccInput = it },
                                        label = { Text("MCC") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = cellMncInput,
                                        onValueChange = { cellMncInput = it },
                                        label = { Text("MNC") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = cellLacInput,
                                        onValueChange = { cellLacInput = it },
                                        label = { Text("LAC 位置区") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = cellCidInput,
                                        onValueChange = { cellCidInput = it },
                                        label = { Text("CID 小区号") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            cellMccInput = "460"
                                            cellMncInput = "0"
                                            cellLacInput = "41021"
                                            cellCidInput = "1892341"
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
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
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
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

                        LocationStateProvider.updateWifiSpoof(
                            enabled = wifiMockEnabled,
                            ssid = wifiSsidInput.trim(),
                            bssid = wifiBssidInput.trim(),
                            rssi = rssiVal
                        )
                        LocationStateProvider.updateCellSpoof(
                            enabled = cellMockEnabled,
                            mcc = mccVal,
                            mnc = mncVal,
                            lac = lacVal,
                            cid = cidVal
                        )
                        Toast.makeText(context, "✅ Wi-Fi 与基站伪装配置已保存并实时生效！", Toast.LENGTH_SHORT).show()
                        showRadioSpoofDialog = false
                    }
                ) {
                    Text("保存并生效")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRadioSpoofDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
