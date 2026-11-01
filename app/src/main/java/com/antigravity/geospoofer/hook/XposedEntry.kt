package com.antigravity.geospoofer.hook

import android.app.AppOpsManager
import android.content.Context
import android.database.Cursor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellLocation
import android.telephony.CellSignalStrengthLte
import android.telephony.TelephonyManager
import android.telephony.gsm.GsmCellLocation
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * LocationMockPro 顶级 LSPosed / Xposed 免杀 Hook 核心引擎 (Phase 3.0 终审加固版)
 *
 * 核心功能加固矩阵：
 * 1. 彻底抹除 isFromMockProvider 与 isMock 虚拟定位标志位
 * 2. 隐藏系统设置 mock_location 标志与 AppOps OP_MOCK_LOCATION 权限标记
 * 3. 硬件计步器增量叠加算法（基准开机值叠加仿真步数，杜绝清零与断崖跳跃异常）
 * 4. LocationManager 接口纳秒级单调时钟与 3.5m~8.0m 动态精度波动
 * 5. ActiveDispatcher 调度器严格基于指定 Looper/Executor 进行主线程/线程池安全投递，并在 removeUpdates 闭环清理
 * 6. ContentProvider IPC 双模容错（call + query 自动降级，绝不阻塞弄崩宿主 App）
 * 7. 企业打卡 Wi-Fi BSSID/SSID 扫描与连接指纹注入
 * 8. 完整合规 CellInfoLte（包含合法 MCC=460、MNC、TAC、CI 及非空 -70~-85dBm 信号强度）
 */
class XposedEntry : IXposedHookLoadPackage {

    private val hookedListeners = ConcurrentHashMap<Int, Boolean>()
    private val hookedLocationListeners = ConcurrentHashMap<Int, Boolean>()

    // 计步器开机硬件真实步数基准值（记录首次硬件回调，默认 12580f 优雅垫底）
    @Volatile private var baseHardwareSteps: Float = 12580f
    @Volatile private var hasCapturedRealHardwareStep: Boolean = false

    // 活跃 Listener 调度封装器与调度器线程
    private val activeDispatchers = ConcurrentHashMap<Int, ListenerDispatcher>()
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

    private val defaultState = RemoteState(
        isMocking = false,
        latitude = 39.908823,
        longitude = 116.397470,
        altitude = 35.0,
        speed = 0f,
        bearing = 0f,
        totalSteps = 0L,
        cadenceSpm = 0,
        wifiMockEnabled = false,
        wifiSsid = "Company-Office-5G",
        wifiBssid = "00:1a:2b:3c:4d:5e",
        wifiRssi = -52,
        cellMockEnabled = false,
        cellMcc = 460,
        cellMnc = 0,
        cellLac = 41021,
        cellCid = 1892341
    )

    // LSPosed 原生 XSharedPreferences 实例（用于 Android 11+ 包隔离下的终极只读 IPC 兜底）
    @Volatile private var xSharedPrefs: XSharedPreferences? = null

    private fun getXSharedPrefs(): XSharedPreferences? {
        if (xSharedPrefs == null) {
            synchronized(this) {
                if (xSharedPrefs == null) {
                    try {
                        val prefs = XSharedPreferences("com.antigravity.geospoofer", "location_mock_pro_stealth_prefs")
                        prefs.makeWorldReadable()
                        xSharedPrefs = prefs
                    } catch (_: Throwable) {}
                }
            }
        }
        return xSharedPrefs
    }

    /**
     * Listener 线程安全调度包装器：
     * 1. 采用 WeakReference 持有宿主 LocationListener，提供弱引用双保险，严防第三方 App 忘记注销引发宿主 Activity 内存泄漏
     * 2. 在分发闭包内部动态二次判空 weakRef.get()，避免投递到 Handler/Executor 消息队列时因强引用闭包阻碍宿主 GC 回收
     * 3. 严格按照宿主注册时传入的 Looper 或 Executor 原生上下文进行投递，严禁后台线程裸调引发 CalledFromWrongThreadException
     */
    private class ListenerDispatcher(
        listener: LocationListener,
        looper: Looper?,
        executorObj: Any?
    ) {
        val weakListener = WeakReference(listener)
        val targetHandler: Handler? = if (looper != null) Handler(looper) else null
        val targetExecutor = executorObj as? Executor

        fun isAlive(): Boolean = weakListener.get() != null

        fun dispatch(location: Location, defaultMainHandler: Handler): Boolean {
            if (weakListener.get() == null) return false
            val weakRef = weakListener

            if (targetExecutor != null) {
                try {
                    targetExecutor.execute {
                        val activeListener = weakRef.get() ?: return@execute
                        try { activeListener.onLocationChanged(location) } catch (_: Throwable) {}
                    }
                    return true
                } catch (_: Throwable) {
                    return false
                }
            } else if (targetHandler != null) {
                try {
                    targetHandler.post {
                        val activeListener = weakRef.get() ?: return@post
                        try { activeListener.onLocationChanged(location) } catch (_: Throwable) {}
                    }
                    return true
                } catch (_: Throwable) {
                    return false
                }
            } else {
                try {
                    defaultMainHandler.post {
                        val activeListener = weakRef.get() ?: return@post
                        try { activeListener.onLocationChanged(location) } catch (_: Throwable) {}
                    }
                    return true
                } catch (_: Throwable) {
                    return false
                }
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 排除宿主应用自身以及核心框架
        if (lpparam.packageName == "com.antigravity.geospoofer") return

        try {
            // ================= 1. 抹除 Android 虚拟定位一切底层标志位 =================
            hookMockFlags(lpparam)

            // ================= 2. 隐藏系统设置与 AppOps 开发者模拟配置 =================
            hookSettingsAndAppOps(lpparam)

            // ================= 3. 硬件步数与运动传感器 Hook 注入 (增量叠加算法) =================
            hookSensors(lpparam)

            // ================= 4. LocationManager 全维接口劫持与动态流主动推送 =================
            hookLocationManager(lpparam)

            // ================= 5. 企业打卡 Wi-Fi BSSID/SSID 环境无线电指纹伪装 =================
            hookWifi(lpparam)

            // ================= 6. 移动蜂窝基站 CellInfoLte 合规构造与伪装 =================
            hookTelephony(lpparam)

            // ================= 7. Android 11+ 包可见性 (Package Visibility) 终极穿透 =================
            hookPackageVisibility(lpparam)

            XposedBridge.log("[LocationMockPro-Hook] Phase 3.0 加固与 Release 级防护已成功注入: ${lpparam.packageName}")
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
     * 硬件传感器 Hook：基于真实开机步数 baseHardwareSteps 进行增量平滑叠加
     */
    private fun hookSensors(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val registerMethods = SensorManager::class.java.declaredMethods.filter {
                it.name == "registerListener" && it.parameterTypes.isNotEmpty() &&
                        SensorEventListener::class.java.isAssignableFrom(it.parameterTypes[0])
            }

            for (method in registerMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args[0] as? SensorEventListener ?: return
                        val sensor = param.args.getOrNull(1) as? Sensor

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
                        // 记录首个硬件有效步数作为基准值
                        if (!hasCapturedRealHardwareStep && event.values.isNotEmpty() && event.values[0] > 0f) {
                            baseHardwareSteps = event.values[0]
                            hasCapturedRealHardwareStep = true
                        }
                        // 注入基准值 + 模拟位移累加步数（严禁覆写为从 0 开始）
                        if (event.values.isNotEmpty()) {
                            event.values[0] = baseHardwareSteps + state.totalSteps.toFloat()
                        }
                    } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                        if (event.values.isNotEmpty()) {
                            event.values[0] = 1.0f
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

        // C. Hook requestLocationUpdates 全部重载：捕获 Listener/Looper/Executor 并启动闭环线程安全分发
        try {
            val requestMethods = LocationManager::class.java.declaredMethods.filter {
                it.name == "requestLocationUpdates"
            }

            for (method in requestMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        var targetListener: LocationListener? = null
                        var targetLooper: Looper? = null
                        var targetExecutor: Any? = null

                        for (arg in param.args) {
                            if (arg is LocationListener) {
                                targetListener = arg
                            } else if (arg is Looper) {
                                targetLooper = arg
                            } else if (arg != null && arg.javaClass.name.contains("Executor")) {
                                targetExecutor = arg
                            }
                        }

                        if (targetListener != null) {
                            val dispatcher = ListenerDispatcher(targetListener, targetLooper, targetExecutor)
                            activeDispatchers[System.identityHashCode(targetListener)] = dispatcher
                            hookLocationListenerCallbacks(targetListener)
                            ensureActiveDispatcher()
                        }
                    }
                })
            }

            // Hook removeUpdates 及时注销监听，彻底消除内存泄漏
            val removeMethods = LocationManager::class.java.declaredMethods.filter {
                it.name == "removeUpdates"
            }
            for (method in removeMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args.firstOrNull() as? LocationListener ?: return
                        activeDispatchers.remove(System.identityHashCode(listener))
                    }
                })
            }

            // D. Hook registerGnssStatusCallback 模拟急速冷启动与 3D 卫星定位锁定
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val gnssMethods = LocationManager::class.java.declaredMethods.filter {
                    it.name == "registerGnssStatusCallback"
                }
                for (method in gnssMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val callback = param.args.firstOrNull { it is android.location.GnssStatus.Callback } as? android.location.GnssStatus.Callback ?: return
                            mainHandler.postDelayed({
                                try {
                                    val state = getSimState()
                                    if (state != null && state.isMocking) {
                                        callback.onStarted()
                                        callback.onFirstFix(800)
                                    }
                                } catch (_: Throwable) {}
                            }, 400L)
                        }
                    })
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * 针对目标应用的 LocationListener 回调进行实时位置劫持与单调时钟补齐
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
     * 启动进程内主动定时推送调度器（严格基于目标线程上下文投递，拒绝裸调造成 WrongThreadException）
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
                            // 定期清理已被 GC 回收的无效引用（弱引用双保险）
                            val iterator = activeDispatchers.entries.iterator()
                            while (iterator.hasNext()) {
                                val entry = iterator.next()
                                if (!entry.value.isAlive()) {
                                    iterator.remove()
                                }
                            }

                            val activeEntries = activeDispatchers.entries.toList()
                            if (activeEntries.isNotEmpty()) {
                                val spoofedLoc = buildSpoofedLocation(LocationManager.GPS_PROVIDER, state)
                                for (entry in activeEntries) {
                                    val success = entry.value.dispatch(spoofedLoc, mainHandler)
                                    if (!success || !entry.value.isAlive()) {
                                        activeDispatchers.remove(entry.key)
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

                        try {
                            val fakeAp = ScanResult().apply {
                                SSID = state.wifiSsid
                                BSSID = state.wifiBssid
                                capabilities = "[WPA2-PSK-CCMP][ESS]"
                                level = state.wifiRssi
                                frequency = 5180
                                timestamp = SystemClock.elapsedRealtime() * 1000
                            }
                            originalList.add(0, fakeAp)
                            param.result = originalList
                        } catch (_: Throwable) {}
                    }
                }
            )

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
     * 移动蜂窝基站 CellInfoLte 合规构造与伪装
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

        // B. 拦截 TelephonyManager.getAllCellInfo：完整构造 CellInfoLte（MCC=460、MNC、TAC、CI 及非空 -70~-85dBm 信号）
        try {
            XposedHelpers.findAndHookMethod(
                TelephonyManager::class.java,
                "getAllCellInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = getSimState() ?: return
                        if (!state.cellMockEnabled) return

                        val fakeLte = buildFakeCellInfoLte(state)
                        if (fakeLte != null) {
                            param.result = listOf(fakeLte)
                        } else {
                            param.result = emptyList<CellInfo>()
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    /**
     * 动态构造符合 AOSP 规范的合法 CellInfoLte，防止目标 App 读取信号强度时产生空指针闪退
     */
    private fun buildFakeCellInfoLte(state: RemoteState): CellInfo? {
        return try {
            val cellInfo = XposedHelpers.newInstance(CellInfoLte::class.java) as CellInfoLte

            // 1. 设置注册标记为 Primary 驻网小区
            try {
                XposedHelpers.setBooleanField(cellInfo, "mRegistered", true)
            } catch (_: Throwable) {
                try {
                    val method = CellInfo::class.java.getDeclaredMethod("setRegistered", Boolean::class.javaPrimitiveType)
                    method.isAccessible = true
                    method.invoke(cellInfo, true)
                } catch (_: Throwable) {}
            }

            // 2. 构造或反射填充 CellIdentityLte
            val cellIdentity = try {
                XposedHelpers.newInstance(CellIdentityLte::class.java) as CellIdentityLte
            } catch (_: Throwable) {
                null
            }

            if (cellIdentity != null) {
                val mccStr = state.cellMcc.toString()
                val mncStr = String.format("%02d", state.cellMnc)

                try { XposedHelpers.setObjectField(cellIdentity, "mMccStr", mccStr) } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(cellIdentity, "mMncStr", mncStr) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellIdentity, "mMcc", state.cellMcc) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellIdentity, "mMnc", state.cellMnc) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellIdentity, "mCi", state.cellCid) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellIdentity, "mTac", state.cellLac) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellIdentity, "mPci", 168) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellIdentity, "mEarfcn", 1825) } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(cellIdentity, "mAlphaLong", if (state.cellMnc == 0) "China Mobile" else "China Telecom") } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(cellIdentity, "mAlphaShort", if (state.cellMnc == 0) "CMCC" else "CT") } catch (_: Throwable) {}

                try {
                    XposedHelpers.setObjectField(cellInfo, "mCellIdentityLte", cellIdentity)
                } catch (_: Throwable) {}
            }

            // 3. 构造并注入合法的 CellSignalStrengthLte (Dbm 控制在 -70 ~ -85 dBm 极优区间)
            val cellSignal = try {
                XposedHelpers.newInstance(CellSignalStrengthLte::class.java) as CellSignalStrengthLte
            } catch (_: Throwable) {
                null
            }

            if (cellSignal != null) {
                val randomDbm = -75 - (System.currentTimeMillis() % 10).toInt() // -75 ~ -84 dBm 真实轻微波动
                try { XposedHelpers.setIntField(cellSignal, "mDbm", randomDbm) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellSignal, "mRssi", randomDbm) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellSignal, "mRsrp", randomDbm - 8) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellSignal, "mRsrq", -9) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellSignal, "mRssnr", 18) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellSignal, "mSignalStrength", 26) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(cellSignal, "mLevel", 4) } catch (_: Throwable) {}

                try {
                    XposedHelpers.setObjectField(cellInfo, "mCellSignalStrengthLte", cellSignal)
                } catch (_: Throwable) {}
            }

            cellInfo
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 7. Android 11+ 包可见性 (Package Visibility) 终极穿透 Hook
     * 确保宿主应用在 targetSdkVersion >= 30 且未在 AndroidManifest 中声明 queries 时，
     * 依然可以无障碍解析并访问 LocationStateProvider 与模块包信息
     */
    private fun hookPackageVisibility(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        try {
            val appPkgMgrClass = Class.forName("android.app.ApplicationPackageManager")

            // A. 放行 canPackageQuery 跨包查询鉴权
            val canQueryMethods = appPkgMgrClass.declaredMethods.filter { it.name == "canPackageQuery" }
            for (method in canQueryMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        for (arg in param.args) {
                            if (arg == "com.antigravity.geospoofer") {
                                param.result = true
                                return
                            }
                        }
                    }
                })
            }

            // B. 拦截 resolveContentProvider，防止宿主查询 com.antigravity.geospoofer.provider 返回 null
            val resolveMethods = appPkgMgrClass.declaredMethods.filter { it.name == "resolveContentProvider" }
            for (method in resolveMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val auth = param.args.firstOrNull() as? String
                        if (auth == "com.antigravity.geospoofer.provider" && param.result == null) {
                            try {
                                val pi = android.content.pm.ProviderInfo().apply {
                                    authority = "com.antigravity.geospoofer.provider"
                                    packageName = "com.antigravity.geospoofer"
                                    name = "com.antigravity.geospoofer.provider.LocationStateProvider"
                                    exported = true
                                    grantUriPermissions = true
                                }
                                param.result = pi
                            } catch (_: Throwable) {}
                        }
                    }
                })
            }
        } catch (_: Throwable) {}
    }

    /**
     * 纳秒级单调时钟与精度补齐：严禁恒定精度，生成 3.5m ~ 8.0m 动态波动值
     */
    private fun buildSpoofedLocation(provider: String, state: RemoteState): Location {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val nowMillis = System.currentTimeMillis()
        val dynamicAccuracy = (3.5f + (Math.sin(nowMillis / 1200.0) * 2.0 + ((nowNanos % 250).toFloat() / 100.0f)).toFloat()).coerceIn(3.5f, 8.0f)

        return Location(provider).apply {
            latitude = state.latitude
            longitude = state.longitude
            altitude = state.altitude
            speed = state.speed
            bearing = state.bearing
            accuracy = dynamicAccuracy
            time = nowMillis
            elapsedRealtimeNanos = nowNanos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 1.0f
                speedAccuracyMetersPerSecond = 0.2f
                verticalAccuracyMeters = 1.5f
            }
        }
    }

    /**
     * 通过 ContentProvider 跨进程高速获取模拟状态（具备 call + query 双模降级兜底，绝不弄崩宿主 App）
     */
    private fun getSimState(): RemoteState? {
        val now = System.currentTimeMillis()
        if (cachedState != null && now - lastFetchTime < 1000L) {
            return cachedState
        }

        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = activityThreadClass.getMethod("currentApplication")
            val context = currentAppMethod.invoke(null) as? Context ?: return cachedState ?: defaultState

            // 优先尝试 call() 极速 IPC
            val bundle = try {
                context.contentResolver.call(
                    Uri.parse("content://com.antigravity.geospoofer.provider"),
                    "getState",
                    null,
                    null
                )
            } catch (_: Throwable) {
                null
            }

            if (bundle != null) {
                val state = RemoteState(
                    isMocking = bundle.getBoolean("is_mocking", false),
                    latitude = bundle.getDouble("latitude", 39.908823),
                    longitude = bundle.getDouble("longitude", 116.397470),
                    altitude = bundle.getDouble("altitude", 35.0),
                    speed = bundle.getFloat("speed", 0f),
                    bearing = bundle.getFloat("bearing", 0f),
                    totalSteps = bundle.getLong("total_steps", 0L),
                    cadenceSpm = bundle.getInt("cadence_spm", 0),
                    wifiMockEnabled = bundle.getBoolean("wifi_mock_enabled", false),
                    wifiSsid = bundle.getString("wifi_ssid", "Company-Office-5G") ?: "Company-Office-5G",
                    wifiBssid = bundle.getString("wifi_bssid", "00:1a:2b:3c:4d:5e") ?: "00:1a:2b:3c:4d:5e",
                    wifiRssi = bundle.getInt("wifi_rssi", -52),
                    cellMockEnabled = bundle.getBoolean("cell_mock_enabled", false),
                    cellMcc = bundle.getInt("cell_mcc", 460),
                    cellMnc = bundle.getInt("cell_mnc", 0),
                    cellLac = bundle.getInt("cell_lac", 41021),
                    cellCid = bundle.getInt("cell_cid", 1892341)
                )
                cachedState = state
                lastFetchTime = now
                return state
            }

            // 备用尝试 query() 降级
            var queryCursor: Cursor? = null
            try {
                queryCursor = context.contentResolver.query(
                    Uri.parse("content://com.antigravity.geospoofer.provider/state"),
                    null,
                    null,
                    null,
                    null
                )
                if (queryCursor != null && queryCursor.moveToFirst()) {
                    val state = RemoteState(
                        isMocking = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("is_mocking")) == 1,
                        latitude = queryCursor.getDouble(queryCursor.getColumnIndexOrThrow("latitude")),
                        longitude = queryCursor.getDouble(queryCursor.getColumnIndexOrThrow("longitude")),
                        altitude = queryCursor.getDouble(queryCursor.getColumnIndexOrThrow("altitude")),
                        speed = queryCursor.getFloat(queryCursor.getColumnIndexOrThrow("speed")),
                        bearing = queryCursor.getFloat(queryCursor.getColumnIndexOrThrow("bearing")),
                        totalSteps = queryCursor.getLong(queryCursor.getColumnIndexOrThrow("total_steps")),
                        cadenceSpm = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("cadence_spm")),
                        wifiMockEnabled = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("wifi_mock_enabled")) == 1,
                        wifiSsid = queryCursor.getString(queryCursor.getColumnIndexOrThrow("wifi_ssid")) ?: "Company-Office-5G",
                        wifiBssid = queryCursor.getString(queryCursor.getColumnIndexOrThrow("wifi_bssid")) ?: "00:1a:2b:3c:4d:5e",
                        wifiRssi = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("wifi_rssi")),
                        cellMockEnabled = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("cell_mock_enabled")) == 1,
                        cellMcc = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("cell_mcc")),
                        cellMnc = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("cell_mnc")),
                        cellLac = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("cell_lac")),
                        cellCid = queryCursor.getInt(queryCursor.getColumnIndexOrThrow("cell_cid"))
                    )
                    cachedState = state
                    lastFetchTime = now
                    return state
                }
            } catch (_: Throwable) {
            } finally {
                queryCursor?.close()
            }

            // 终极只读 IPC 通道预案：尝试 LSPosed 原生 XSharedPreferences 读取（穿透 Android 11+ Package Visibility 隔离）
            val prefs = getXSharedPrefs()
            if (prefs != null) {
                try {
                    prefs.reload()
                    if (prefs.contains("is_mocking") || prefs.contains("latitude_f") || prefs.contains("latitude_str")) {
                        val latStr = prefs.getString("latitude_str", null)
                        val lonStr = prefs.getString("longitude_str", null)
                        val altStr = prefs.getString("altitude_str", null)

                        val lat = latStr?.toDoubleOrNull() ?: prefs.getFloat("latitude_f", 39.908823f).toDouble()
                        val lon = lonStr?.toDoubleOrNull() ?: prefs.getFloat("longitude_f", 116.397470f).toDouble()
                        val alt = altStr?.toDoubleOrNull() ?: prefs.getFloat("altitude_f", 35.0f).toDouble()

                        val state = RemoteState(
                            isMocking = prefs.getBoolean("is_mocking", false),
                            latitude = lat,
                            longitude = lon,
                            altitude = alt,
                            speed = prefs.getFloat("speed", 0f),
                            bearing = prefs.getFloat("bearing", 0f),
                            totalSteps = prefs.getLong("total_steps", 0L),
                            cadenceSpm = prefs.getInt("cadence_spm", 0),
                            wifiMockEnabled = prefs.getBoolean("wifi_mock_enabled", false),
                            wifiSsid = prefs.getString("wifi_ssid", "Company-Office-5G") ?: "Company-Office-5G",
                            wifiBssid = prefs.getString("wifi_bssid", "00:1a:2b:3c:4d:5e") ?: "00:1a:2b:3c:4d:5e",
                            wifiRssi = prefs.getInt("wifi_rssi", -52),
                            cellMockEnabled = prefs.getBoolean("cell_mock_enabled", false),
                            cellMcc = prefs.getInt("cell_mcc", 460),
                            cellMnc = prefs.getInt("cell_mnc", 0),
                            cellLac = prefs.getInt("cell_lac", 41021),
                            cellCid = prefs.getInt("cell_cid", 1892341)
                        )
                        cachedState = state
                        lastFetchTime = now
                        return state
                    }
                } catch (_: Throwable) {}
            }

            cachedState ?: defaultState
        } catch (_: Throwable) {
            cachedState ?: defaultState
        }
    }
}
