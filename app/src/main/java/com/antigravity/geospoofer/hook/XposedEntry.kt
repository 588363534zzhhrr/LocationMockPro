package com.antigravity.geospoofer.hook

import android.app.AppOpsManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.CellLocation
import android.telephony.TelephonyManager
import android.telephony.gsm.GsmCellLocation
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * LocationMockPro 顶级 LSPosed / Xposed 免杀 Hook 核心引擎 (V2.5 进阶版)
 *
 * 核心功能矩阵：
 * 1. 彻底抹除 isFromMockProvider 与 isMock 虚拟定位标志位
 * 2. 隐藏系统设置 mock_location 标志与 AppOps OP_MOCK_LOCATION 权限标记
 * 3. 硬件计步器与加速度计传感器 Hook（打通仿真步频与步数联动，防止运动作弊判定）
 * 4. LocationManager 单次请求直接劫持 (getLastKnownLocation / getLastLocation / getCurrentLocation)
 * 5. 无感 LocationListener 动态流劫持与主动推送调度器 (无需原生 TestProvider 即可直推)
 * 6. 企业打卡 Wi-Fi BSSID/SSID 扫描与连接指纹注入 (绕过钉钉/企微 Wi-Fi 考勤检测)
 * 7. 基站 Cell ID / LAC 无线电信号虚拟化 (杜绝蜂窝基站异地暴露)
 */
class XposedEntry : IXposedHookLoadPackage {

    private val hookedListeners = ConcurrentHashMap<Int, Boolean>()
    private val hookedLocationListeners = ConcurrentHashMap<Int, Boolean>()

    // 活跃 LocationListener 弱引用集合与主动注入调度器
    private val activeLocationListeners = Collections.newSetFromMap(WeakHashMap<LocationListener, Boolean>())
    @Volatile private var isDispatcherRunning = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // 进程内缓存模拟状态（避免高频 IPC 跨进程开销，1 秒缓存过期刷新）
    private var cachedState: RemoteState? = null
    private var lastFetchTime = 0L

    data class RemoteState(
        val isMocking: Boolean,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val speed: Float,
        val bearing: Float,
        val totalSteps: Long,
        val cadenceSpm: Int,
        // Wi-Fi 伪装
        val wifiMockEnabled: Boolean,
        val wifiSsid: String,
        val wifiBssid: String,
        val wifiRssi: Int,
        // 基站伪装
        val cellMockEnabled: Boolean,
        val cellMcc: Int,
        val cellMnc: Int,
        val cellLac: Int,
        val cellCid: Int
    )

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 排除宿主应用自身以及核心框架
        if (lpparam.packageName == "com.antigravity.geospoofer") return

        try {
            // ================= 1. 抹除 Android 虚拟定位一切底层标志位 =================
            hookMockFlags(lpparam)

            // ================= 2. 隐藏系统设置与 AppOps 开发者模拟配置 =================
            hookSettingsAndAppOps(lpparam)

            // ================= 3. 硬件步数与运动传感器 Hook 注入 (针对运动类 App) =================
            hookSensors(lpparam)

            // ================= 4. LocationManager 全维接口劫持与动态流主动推送 =================
            hookLocationManager(lpparam)

            // ================= 5. 企业打卡 Wi-Fi BSSID/SSID 环境无线电指纹伪装 =================
            hookWifi(lpparam)

            // ================= 6. 移动蜂窝基站 LAC / Cell ID 伪装与防护 =================
            hookTelephony(lpparam)

            XposedBridge.log("[LocationMockPro-Hook] 成功全维度注入目标应用: ${lpparam.packageName}")
        } catch (t: Throwable) {
            XposedBridge.log("[LocationMockPro-Hook] 注入目标应用 ${lpparam.packageName} 发生异常: ${t.message}")
        }
    }

    /**
     * 抹除 isFromMockProvider() / isMock() 以及 Bundle 中的 mockLocation 标志
     */
    private fun hookMockFlags(lpparam: XC_LoadPackage.LoadPackageParam) {
        // A. 拦截 Location.isFromMockProvider() -> 永真返回 false
        try {
            XposedHelpers.findAndHookMethod(
                Location::class.java,
                "isFromMockProvider",
                XC_MethodReplacement.returnConstant(false)
            )
        } catch (_: Throwable) {}

        // B. 拦截 Android 12+ (API 31) Location.isMock() -> 永真返回 false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                XposedHelpers.findAndHookMethod(
                    Location::class.java,
                    "isMock",
                    XC_MethodReplacement.returnConstant(false)
                )
            } catch (_: Throwable) {}
        }

        // C. 拦截 Location.getExtras()，剔除 mock 判定键值
        try {
            XposedHelpers.findAndHookMethod(
                Location::class.java,
                "getExtras",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val bundle = param.result as? Bundle ?: return
                        bundle.remove("mockLocation")
                        bundle.remove("isMock")
                        bundle.putBoolean("mockLocation", false)
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    /**
     * 隐藏系统开发者选项 mock_location 标志位与 AppOps 权限判定
     */
    private fun hookSettingsAndAppOps(lpparam: XC_LoadPackage.LoadPackageParam) {
        // A. Settings.Secure.getString / getInt
        try {
            XposedHelpers.findAndHookMethod(
                Settings.Secure::class.java,
                "getString",
                android.content.ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "mock_location" || key == "allow_mock_location") {
                            param.result = "0"
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                Settings.Secure::class.java,
                "getInt",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "mock_location" || key == "allow_mock_location") {
                            param.result = 0
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // B. AppOpsManager.checkOp / checkOpNoThrow (OP_MOCK_LOCATION = 58)
        val opMethods = listOf("checkOp", "checkOpNoThrow", "noteOp", "noteOpNoThrow", "noteProxyOp", "noteProxyOpNoThrow")
        for (m in opMethods) {
            try {
                XposedHelpers.findAndHookMethod(
                    AppOpsManager::class.java,
                    m,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val op = param.args[0] as? Int
                            if (op == 58) { // OP_MOCK_LOCATION
                                param.result = AppOpsManager.MODE_IGNORED
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
    }

    /**
     * 硬件传感器 Hook：将 LocationMockPro 算法生成的步数同步注入目标 App
     */
    private fun hookSensors(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook SensorManager.registerListener
            val registerMethods = SensorManager::class.java.declaredMethods.filter {
                it.name == "registerListener" && it.parameterTypes.isNotEmpty() &&
                        SensorEventListener::class.java.isAssignableFrom(it.parameterTypes[0])
            }

            for (method in registerMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args[0] as? SensorEventListener ?: return
                        val sensor = param.args.getOrNull(1) as? Sensor

                        // 针对计步器 (TYPE_STEP_COUNTER = 19) 或步数探测器 (TYPE_STEP_DETECTOR = 18)
                        if (sensor != null && (sensor.type == Sensor.TYPE_STEP_COUNTER || sensor.type == Sensor.TYPE_STEP_DETECTOR)) {
                            hookSensorEventListener(listener)
                        }
                    }
                })
            }
        } catch (_: Throwable) {}
    }

    /**
     * Hook 目标应用实现的 SensorEventListener.onSensorChanged
     */
    private fun hookSensorEventListener(listener: SensorEventListener) {
        val hash = System.identityHashCode(listener)
        if (hookedListeners.putIfAbsent(hash, true) != null) return

        try {
            val listenerClass = listener.javaClass
            val onSensorChangedMethod = listenerClass.getMethod("onSensorChanged", SensorEvent::class.java)

            XposedBridge.hookMethod(onSensorChangedMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args[0] as? SensorEvent ?: return
                    val state = getSimState() ?: return
                    if (!state.isMocking) return

                    if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                        // 目标 App 读取硬件总步数：动态叠加 LocationMockPro 模拟步数
                        if (state.totalSteps > 0) {
                            event.values[0] = (event.values[0] + state.totalSteps.toFloat())
                        }
                    }
                }
            })
        } catch (_: Throwable) {}
    }

    /**
     * 劫持 LocationManager 核心接口，包含单次请求与动态流主动推送调度器
     */
    private fun hookLocationManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        // A. getLastKnownLocation
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager::class.java,
                "getLastKnownLocation",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (!state.isMocking) return

                        val provider = param.args[0] as? String ?: LocationManager.GPS_PROVIDER
                        val mockLoc = buildSpoofedLocation(provider, state)
                        param.result = mockLoc
                    }
                }
            )
        } catch (_: Throwable) {}

        // B. Android 12+ getLastLocation()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                XposedHelpers.findAndHookMethod(
                    LocationManager::class.java,
                    "getLastLocation",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val state = getSimState() ?: return
                            if (!state.isMocking) return

                            val mockLoc = buildSpoofedLocation(LocationManager.FUSED_PROVIDER, state)
                            param.result = mockLoc
                        }
                    }
                )
            } catch (_: Throwable) {}
        }

        // C. Hook requestLocationUpdates 全部重载：捕获 LocationListener 并启动无感主动分发
        try {
            val requestMethods = LocationManager::class.java.declaredMethods.filter {
                it.name == "requestLocationUpdates"
            }

            for (method in requestMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        for (arg in param.args) {
                            if (arg is LocationListener) {
                                synchronized(activeLocationListeners) {
                                    activeLocationListeners.add(arg)
                                }
                                hookLocationListenerCallbacks(arg)
                                ensureActiveDispatcher()
                                break
                            }
                        }
                    }
                })
            }

            // Hook removeUpdates 及时注销监听
            val removeMethods = LocationManager::class.java.declaredMethods.filter {
                it.name == "removeUpdates"
            }
            for (method in removeMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args.firstOrNull() as? LocationListener ?: return
                        synchronized(activeLocationListeners) {
                            activeLocationListeners.remove(listener)
                        }
                    }
                })
            }
        } catch (_: Throwable) {}
    }

    /**
     * 针对目标应用的 LocationListener 回调进行实时位置劫持
     */
    private fun hookLocationListenerCallbacks(listener: LocationListener) {
        val hash = System.identityHashCode(listener)
        if (hookedLocationListeners.putIfAbsent(hash, true) != null) return

        try {
            val listenerClass = listener.javaClass

            // Hook onLocationChanged(Location)
            try {
                val method = listenerClass.getMethod("onLocationChanged", Location::class.java)
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (!state.isMocking) return
                        val originalLoc = param.args[0] as? Location
                        val provider = originalLoc?.provider ?: LocationManager.GPS_PROVIDER
                        param.args[0] = buildSpoofedLocation(provider, state)
                    }
                })
            } catch (_: NoSuchMethodException) {}

            // Hook API 30+ onLocationChanged(List<Location>)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val listMethod = listenerClass.getMethod("onLocationChanged", List::class.java)
                    XposedBridge.hookMethod(listMethod, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val state = getSimState() ?: return
                            if (!state.isMocking) return
                            val spoofedLoc = buildSpoofedLocation(LocationManager.GPS_PROVIDER, state)
                            param.args[0] = listOf(spoofedLoc)
                        }
                    })
                } catch (_: NoSuchMethodException) {}
            }
        } catch (_: Throwable) {}
    }

    /**
     * 启动进程内主动定时推送调度器（即使无 TestProvider，依然保证每秒向目标应用分发逼真坐标）
     */
    private fun ensureActiveDispatcher() {
        if (isDispatcherRunning) return
        synchronized(this) {
            if (isDispatcherRunning) return
            isDispatcherRunning = true

            val dispatcherThread = Thread({
                while (true) {
                    try {
                        Thread.sleep(1000L)
                        val state = getSimState()
                        if (state != null && state.isMocking) {
                            val listeners = synchronized(activeLocationListeners) {
                                activeLocationListeners.toList()
                            }
                            if (listeners.isNotEmpty()) {
                                val spoofedLoc = buildSpoofedLocation(LocationManager.GPS_PROVIDER, state)
                                mainHandler.post {
                                    for (listener in listeners) {
                                        try {
                                            listener.onLocationChanged(spoofedLoc)
                                        } catch (_: Throwable) {}
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }, "LocationMockPro-ActiveDispatcher")
            dispatcherThread.isDaemon = true
            dispatcherThread.start()
        }
    }

    /**
     * 企业打卡 Wi-Fi BSSID/SSID 扫描与连接指纹注入
     */
    private fun hookWifi(lpparam: XC_LoadPackage.LoadPackageParam) {
        // A. WifiInfo 劫持 (SSID, BSSID, RSSI)
        try {
            XposedHelpers.findAndHookMethod(
                WifiInfo::class.java,
                "getSSID",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (state.wifiMockEnabled && state.wifiSsid.isNotBlank()) {
                            param.result = "\"${state.wifiSsid}\""
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                WifiInfo::class.java,
                "getBSSID",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (state.wifiMockEnabled && state.wifiBssid.isNotBlank()) {
                            param.result = state.wifiBssid
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                WifiInfo::class.java,
                "getRssi",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (state.wifiMockEnabled) {
                            param.result = state.wifiRssi
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // B. WifiManager.getScanResults 注入目标考勤 AP 节点
        try {
            XposedHelpers.findAndHookMethod(
                WifiManager::class.java,
                "getScanResults",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (!state.wifiMockEnabled || state.wifiBssid.isBlank()) return

                        val originalList = (param.result as? List<ScanResult>)?.toMutableList() ?: mutableListOf()

                        // 构造目标仿真 Wi-Fi 接入点
                        try {
                            val fakeAp = ScanResult().apply {
                                SSID = state.wifiSsid
                                BSSID = state.wifiBssid
                                capabilities = "[WPA2-PSK-CCMP][ESS]"
                                level = state.wifiRssi
                                frequency = 5180
                                timestamp = SystemClock.elapsedRealtime() * 1000
                            }
                            // 置顶插入在首位
                            originalList.add(0, fakeAp)
                            param.result = originalList
                        } catch (_: Throwable) {}
                    }
                }
            )

            // 保持 Wi-Fi 开启状态常真
            XposedHelpers.findAndHookMethod(
                WifiManager::class.java,
                "isWifiEnabled",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (state.wifiMockEnabled) {
                            param.result = true
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    /**
     * 移动蜂窝基站 LAC / Cell ID 伪装与防护
     */
    private fun hookTelephony(lpparam: XC_LoadPackage.LoadPackageParam) {
        // A. 拦截 TelephonyManager.getCellLocation
        try {
            XposedHelpers.findAndHookMethod(
                TelephonyManager::class.java,
                "getCellLocation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (!state.cellMockEnabled) return

                        val fakeCell = GsmCellLocation().apply {
                            setLacAndCid(state.cellLac, state.cellCid)
                        }
                        param.result = fakeCell
                    }
                }
            )
        } catch (_: Throwable) {}

        // B. 拦截 TelephonyManager.getAllCellInfo (防止真实异地基站列表暴露)
        try {
            XposedHelpers.findAndHookMethod(
                TelephonyManager::class.java,
                "getAllCellInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (!state.cellMockEnabled) return

                        // 当处于异地模拟且未配置完整物理蜂窝拓扑时，返回空列表杜绝真实基站比对泄露
                        param.result = emptyList<CellInfo>()
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun buildSpoofedLocation(provider: String, state: RemoteState): Location {
        return Location(provider).apply {
            latitude = state.latitude
            longitude = state.longitude
            altitude = state.altitude
            speed = state.speed
            bearing = state.bearing
            accuracy = 3.5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 1.0f
                speedAccuracyMetersPerSecond = 0.2f
                verticalAccuracyMeters = 1.5f
            }
        }
    }

    /**
     * 通过 ContentProvider 跨进程高速获取 LocationMockPro 当前坐标、步数与无线电指纹
     */
    private fun getSimState(): RemoteState? {
        val now = System.currentTimeMillis()
        if (cachedState != null && now - lastFetchTime < 1000L) {
            return cachedState
        }

        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = activityThreadClass.getMethod("currentApplication")
            val context = currentAppMethod.invoke(null) as? Context ?: return cachedState

            val bundle = context.contentResolver.call(
                android.net.Uri.parse("content://com.antigravity.geospoofer.provider"),
                "getState",
                null,
                null
            ) ?: return cachedState

            val state = RemoteState(
                isMocking = bundle.getBoolean("is_mocking", false),
                latitude = bundle.getDouble("latitude", 39.908823),
                longitude = bundle.getDouble("longitude", 116.397470),
                altitude = bundle.getDouble("altitude", 35.0),
                speed = bundle.getFloat("speed", 0f),
                bearing = bundle.getFloat("bearing", 0f),
                totalSteps = bundle.getLong("total_steps", 0L),
                cadenceSpm = bundle.getInt("cadence_spm", 0),
                // Wi-Fi 伪装
                wifiMockEnabled = bundle.getBoolean("wifi_mock_enabled", false),
                wifiSsid = bundle.getString("wifi_ssid", "Company-Office-5G") ?: "Company-Office-5G",
                wifiBssid = bundle.getString("wifi_bssid", "00:1a:2b:3c:4d:5e") ?: "00:1a:2b:3c:4d:5e",
                wifiRssi = bundle.getInt("wifi_rssi", -52),
                // 基站伪装
                cellMockEnabled = bundle.getBoolean("cell_mock_enabled", false),
                cellMcc = bundle.getInt("cell_mcc", 460),
                cellMnc = bundle.getInt("cell_mnc", 0),
                cellLac = bundle.getInt("cell_lac", 41021),
                cellCid = bundle.getInt("cell_cid", 1892341)
            )
            cachedState = state
            lastFetchTime = now
            state
        } catch (_: Throwable) {
            cachedState
        }
    }
}
