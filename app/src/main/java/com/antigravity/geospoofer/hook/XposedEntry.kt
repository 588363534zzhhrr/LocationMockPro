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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
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
    private val activeStepListeners = ConcurrentHashMap<Int, StepListenerInfo>()
    private val activeGnssCallbacks = ConcurrentHashMap<Int, WeakReference<android.location.GnssStatus.Callback>>()
    private val activeNmeaListeners = ConcurrentHashMap<Int, NmeaListenerInfo>()
    @Volatile private var isDispatcherRunning = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private class StepListenerInfo(
        val weakListener: WeakReference<SensorEventListener>,
        val sensor: Sensor,
        val targetHandler: Handler?
    )

    private class NmeaListenerInfo(
        val rawListener: WeakReference<Any>,
        val targetHandler: Handler?,
        val targetExecutor: Executor?,
        val isModern: Boolean
    ) {
        fun isAlive(): Boolean = rawListener.get() != null

        fun dispatch(nmea: String, timestamp: Long, defaultMainHandler: Handler) {
            val listener = rawListener.get() ?: return
            val action = Runnable {
                val active = rawListener.get() ?: return@Runnable
                try {
                    if (isModern) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && active is android.location.OnNmeaMessageListener) {
                            active.onNmeaMessage(nmea, timestamp)
                        } else {
                            val method = active.javaClass.getMethod("onNmeaMessage", String::class.java, Long::class.javaPrimitiveType)
                            method.invoke(active, nmea, timestamp)
                        }
                    } else {
                        if (active is android.location.GpsStatus.NmeaListener) {
                            active.onNmeaReceived(timestamp, nmea)
                        } else {
                            val method = active.javaClass.getMethod("onNmeaReceived", Long::class.javaPrimitiveType, String::class.java)
                            method.invoke(active, timestamp, nmea)
                        }
                    }
                } catch (_: Throwable) {}
            }

            if (targetExecutor != null) {
                try { targetExecutor.execute(action) } catch (_: Throwable) {}
            } else if (targetHandler != null) {
                try { targetHandler.post(action) } catch (_: Throwable) {}
            } else {
                try { defaultMainHandler.post(action) } catch (_: Throwable) {}
            }
        }
    }

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
        // A. Settings.Secure & Settings.Global getString / getInt (覆盖 2 参数与 3 参数重载)
        val settingsClasses = listOf(Settings.Secure::class.java, Settings.Global::class.java)
        for (clazz in settingsClasses) {
            try {
                // getString(ContentResolver, String)
                XposedHelpers.findAndHookMethod(
                    clazz,
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

                // getInt(ContentResolver, String) - 双参数原生标准重载
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "getInt",
                    android.content.ContentResolver::class.java,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val key = param.args[1] as? String
                            if (key == "mock_location" || key == "allow_mock_location") {
                                param.result = 0
                            }
                        }
                    }
                )

                // getInt(ContentResolver, String, Int) - 三参数带默认值重载
                XposedHelpers.findAndHookMethod(
                    clazz,
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
        }

        // B. AppOpsManager.checkOp / checkOpNoThrow / noteOp (覆盖 Int 与 String 两种操作符体系)
        val opMethods = listOf(
            "checkOp", "checkOpNoThrow", "noteOp", "noteOpNoThrow",
            "noteProxyOp", "noteProxyOpNoThrow", "unsafeCheckOp",
            "unsafeCheckOpNoThrow", "unsafeCheckOpRaw", "unsafeCheckOpRawNoThrow"
        )
        for (m in opMethods) {
            // 1. 拦截 Int 参数体系 (OP_MOCK_LOCATION = 58)
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

            // 2. 拦截现代 Android (API 23~35) String 参数体系 (OPSTR_MOCK_LOCATION = "android:mock_location")
            try {
                XposedHelpers.findAndHookMethod(
                    AppOpsManager::class.java,
                    m,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val op = param.args[0] as? String
                            if (op == "android:mock_location" || op == "mock_location" || op == "OPSTR_MOCK_LOCATION") {
                                param.result = AppOpsManager.MODE_IGNORED
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
    }

    /**
     * 硬件传感器 Hook：基于真实开机步数 baseHardwareSteps 进行增量平滑叠加，支持无传感器设备/模拟器主动虚拟仿真
     */
    private fun hookSensors(lpparam: XC_LoadPackage.LoadPackageParam) {
        // A. 拦截 getDefaultSensor：在无物理计步器的模拟器或低端设备中伪装成博世高精度计步传感器
        try {
            XposedHelpers.findAndHookMethod(
                SensorManager::class.java,
                "getDefaultSensor",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val type = param.args[0] as? Int ?: return
                        if (type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR) {
                            if (param.result == null) {
                                param.result = createFakeStepSensor(type)
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // B. 拦截 registerListener：捕获目标应用注册的计步器回调并开启主动仿真推送
        try {
            val registerMethods = SensorManager::class.java.declaredMethods.filter {
                it.name == "registerListener" && it.parameterTypes.isNotEmpty() &&
                        SensorEventListener::class.java.isAssignableFrom(it.parameterTypes[0])
            }

            for (method in registerMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args[0] as? SensorEventListener ?: return
                        val sensor = param.args.getOrNull(1) as? Sensor ?: return
                        var targetHandler: Handler? = null
                        for (arg in param.args) {
                            if (arg is Handler) targetHandler = arg
                        }

                        if (sensor.type == Sensor.TYPE_STEP_COUNTER || sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                            hookSensorEventListener(listener)
                            activeStepListeners[System.identityHashCode(listener)] = StepListenerInfo(
                                WeakReference(listener),
                                sensor,
                                targetHandler
                            )
                            ensureActiveDispatcher()
                        }
                    }
                })
            }

            // 拦截 unregisterListener 及时注销
            val unregisterMethods = SensorManager::class.java.declaredMethods.filter {
                it.name == "unregisterListener" && it.parameterTypes.isNotEmpty() &&
                        SensorEventListener::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            for (method in unregisterMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args[0] as? SensorEventListener ?: return
                        activeStepListeners.remove(System.identityHashCode(listener))
                    }
                })
            }
        } catch (_: Throwable) {}

        // C. 拦截 PackageManager.hasSystemFeature：在无物理计步传感器或模拟器环境下伪装支持 STEP_COUNTER 与 STEP_DETECTOR
        try {
            val appPkgMgrClass = Class.forName("android.app.ApplicationPackageManager")
            XposedHelpers.findAndHookMethod(
                appPkgMgrClass,
                "hasSystemFeature",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val feature = param.args[0] as? String
                        if (feature == "android.hardware.sensor.stepcounter" ||
                            feature == "android.hardware.sensor.stepdetector") {
                            param.result = true
                        }
                    }
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                XposedHelpers.findAndHookMethod(
                    appPkgMgrClass,
                    "hasSystemFeature",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val feature = param.args[0] as? String
                            if (feature == "android.hardware.sensor.stepcounter" ||
                                feature == "android.hardware.sensor.stepdetector") {
                                param.result = true
                            }
                        }
                    }
                )
            }
        } catch (_: Throwable) {}

        // D. 拦截 SensorManager.getSensorList：当系统未检测到物理步数传感器时注入仿真传感器实例
        try {
            XposedHelpers.findAndHookMethod(
                SensorManager::class.java,
                "getSensorList",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val type = param.args[0] as? Int ?: return
                        if (type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR || type == Sensor.TYPE_ALL) {
                            val list = (param.result as? List<Sensor>)?.toMutableList() ?: mutableListOf()
                            val hasCounter = list.any { it.type == Sensor.TYPE_STEP_COUNTER }
                            val hasDetector = list.any { it.type == Sensor.TYPE_STEP_DETECTOR }
                            if ((type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_ALL) && !hasCounter) {
                                createFakeStepSensor(Sensor.TYPE_STEP_COUNTER)?.let { list.add(it) }
                            }
                            if ((type == Sensor.TYPE_STEP_DETECTOR || type == Sensor.TYPE_ALL) && !hasDetector) {
                                createFakeStepSensor(Sensor.TYPE_STEP_DETECTOR)?.let { list.add(it) }
                            }
                            param.result = list
                        }
                    }
                }
            )
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

        // C. Android 11+ (API 30+) getCurrentLocation 拦截注入（解决钉钉/企微/飞书/高德等打卡首选单次精准定位穿透风险）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val currentLocMethods = LocationManager::class.java.declaredMethods.filter {
                    it.name == "getCurrentLocation"
                }
                for (method in currentLocMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val state = getSimState() ?: return
                            if (!state.isMocking) return

                            var executor: Executor? = null
                            var consumer: Any? = null
                            var providerName = LocationManager.GPS_PROVIDER

                            for (arg in param.args) {
                                if (arg is String) {
                                    providerName = arg
                                } else if (arg is Executor) {
                                    executor = arg
                                } else if (arg != null && (arg.javaClass.name.contains("Consumer") || arg is java.util.function.Consumer<*>)) {
                                    consumer = arg
                                }
                            }

                            if (consumer != null) {
                                val spoofedLoc = buildSpoofedLocation(providerName, state)
                                val targetExec = executor ?: Executor { r -> mainHandler.post(r) }
                                try {
                                    targetExec.execute {
                                        try {
                                            val acceptMethod = consumer.javaClass.methods.firstOrNull {
                                                it.name == "accept" && it.parameterTypes.size == 1
                                            }
                                            acceptMethod?.invoke(consumer, spoofedLoc)
                                        } catch (_: Throwable) {}
                                    }
                                } catch (_: Throwable) {
                                    try {
                                        XposedHelpers.callMethod(consumer, "accept", spoofedLoc)
                                    } catch (_: Throwable) {}
                                }
                                param.result = null // 阻断系统原方法执行，杜绝真实基站与物理 GPS 穿透
                            }
                        }
                    })
                }
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
                            activeGnssCallbacks[System.identityHashCode(callback)] = WeakReference(callback)
                            mainHandler.postDelayed({
                                try {
                                    val state = getSimState()
                                    if (state != null && state.isMocking) {
                                        callback.onStarted()
                                        callback.onFirstFix(800)
                                        val status = buildSyntheticGnssStatus()
                                        if (status != null) {
                                            callback.onSatelliteStatusChanged(status)
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }, 300L)
                        }
                    })
                }

                // Hook unregisterGnssStatusCallback
                try {
                    val unregisterMethod = LocationManager::class.java.getMethod("unregisterGnssStatusCallback", android.location.GnssStatus.Callback::class.java)
                    XposedBridge.hookMethod(unregisterMethod, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val callback = param.args[0] as? android.location.GnssStatus.Callback ?: return
                            activeGnssCallbacks.remove(System.identityHashCode(callback))
                        }
                    })
                } catch (_: Throwable) {}
            }

            // E. Hook addGpsStatusListener (针对旧版地图 SDK)
            try {
                val method = LocationManager::class.java.getMethod("addGpsStatusListener", android.location.GpsStatus.Listener::class.java)
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args[0] as? android.location.GpsStatus.Listener ?: return
                        mainHandler.postDelayed({
                            try {
                                val state = getSimState()
                                if (state != null && state.isMocking) {
                                    listener.onGpsStatusChanged(android.location.GpsStatus.GPS_EVENT_STARTED)
                                    listener.onGpsStatusChanged(android.location.GpsStatus.GPS_EVENT_FIRST_FIX)
                                    listener.onGpsStatusChanged(android.location.GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                                }
                            } catch (_: Throwable) {}
                        }, 400L)
                    }
                })
            } catch (_: Throwable) {}

            // F. Hook addNmeaListener / removeNmeaListener (捕获高德/百度/Keep等监听 NMEA 原始卫星报文)
            try {
                val addNmeaMethods = LocationManager::class.java.declaredMethods.filter { it.name == "addNmeaListener" }
                for (method in addNmeaMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            var targetListener: Any? = null
                            var targetHandler: Handler? = null
                            var targetExecutor: Executor? = null
                            var isModern = false

                            for (arg in param.args) {
                                if (arg == null) continue
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && arg is android.location.OnNmeaMessageListener) {
                                    targetListener = arg
                                    isModern = true
                                } else if (arg is android.location.GpsStatus.NmeaListener) {
                                    targetListener = arg
                                    isModern = false
                                } else if (arg is Handler) {
                                    targetHandler = arg
                                } else if (arg is Executor) {
                                    targetExecutor = arg
                                } else if (arg.javaClass.name.contains("NmeaMessageListener")) {
                                    targetListener = arg
                                    isModern = true
                                } else if (arg.javaClass.name.contains("NmeaListener")) {
                                    targetListener = arg
                                    isModern = false
                                }
                            }

                            if (targetListener != null) {
                                val info = NmeaListenerInfo(
                                    WeakReference(targetListener),
                                    targetHandler,
                                    targetExecutor,
                                    isModern
                                )
                                activeNmeaListeners[System.identityHashCode(targetListener)] = info
                                ensureActiveDispatcher()
                            }
                        }
                    })
                }

                val removeNmeaMethods = LocationManager::class.java.declaredMethods.filter { it.name == "removeNmeaListener" }
                for (method in removeNmeaMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val listener = param.args.firstOrNull { it != null && (it.javaClass.name.contains("Nmea") || it is android.location.GpsStatus.NmeaListener) } ?: return
                            activeNmeaListeners.remove(System.identityHashCode(listener))
                        }
                    })
                }
            } catch (_: Throwable) {}

            // G. Hook isProviderEnabled (确保在模拟开启时 gps/network/fused 恒定可用)
            try {
                XposedHelpers.findAndHookMethod(
                    LocationManager::class.java,
                    "isProviderEnabled",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val state = getSimState() ?: return
                            if (state.isMocking) {
                                val p = param.args[0] as? String
                                if (p == LocationManager.GPS_PROVIDER ||
                                    p == LocationManager.NETWORK_PROVIDER ||
                                    p == LocationManager.PASSIVE_PROVIDER ||
                                    p == "fused") {
                                    param.result = true
                                }
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}

            // H. Hook getProviders & getAllProviders
            try {
                XposedHelpers.findAndHookMethod(
                    LocationManager::class.java,
                    "getProviders",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        @Suppress("UNCHECKED_CAST")
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val state = getSimState() ?: return
                            if (state.isMocking) {
                                val list = (param.result as? List<String>)?.toMutableList() ?: mutableListOf()
                                if (!list.contains(LocationManager.GPS_PROVIDER)) list.add(LocationManager.GPS_PROVIDER)
                                if (!list.contains(LocationManager.NETWORK_PROVIDER)) list.add(LocationManager.NETWORK_PROVIDER)
                                if (!list.contains(LocationManager.PASSIVE_PROVIDER)) list.add(LocationManager.PASSIVE_PROVIDER)
                                if (!list.contains("fused")) list.add("fused")
                                param.result = list
                            }
                        }
                    }
                )

                XposedHelpers.findAndHookMethod(
                    LocationManager::class.java,
                    "getAllProviders",
                    object : XC_MethodHook() {
                        @Suppress("UNCHECKED_CAST")
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val state = getSimState() ?: return
                            if (state.isMocking) {
                                val list = (param.result as? List<String>)?.toMutableList() ?: mutableListOf()
                                if (!list.contains(LocationManager.GPS_PROVIDER)) list.add(LocationManager.GPS_PROVIDER)
                                if (!list.contains(LocationManager.NETWORK_PROVIDER)) list.add(LocationManager.NETWORK_PROVIDER)
                                if (!list.contains(LocationManager.PASSIVE_PROVIDER)) list.add(LocationManager.PASSIVE_PROVIDER)
                                if (!list.contains("fused")) list.add("fused")
                                param.result = list
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}

            // I. Hook getBestProvider
            try {
                XposedHelpers.findAndHookMethod(
                    LocationManager::class.java,
                    "getBestProvider",
                    android.location.Criteria::class.java,
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val state = getSimState() ?: return
                            if (state.isMocking) {
                                param.result = LocationManager.GPS_PROVIDER
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}
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

                            // 计步器主动仿真事件分发（为无物理计步器的模拟器与静止真机主动推送步数事件）
                            if (activeStepListeners.isNotEmpty() && state.totalSteps > 0) {
                                val activeStepEntries = activeStepListeners.entries.toList()
                                for (entry in activeStepEntries) {
                                    val info = entry.value
                                    val listener = info.weakListener.get()
                                    if (listener == null) {
                                        activeStepListeners.remove(entry.key)
                                        continue
                                    }
                                    val stepVal = if (info.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                                        baseHardwareSteps + state.totalSteps.toFloat()
                                    } else {
                                        1.0f
                                    }
                                    val event = createFakeSensorEvent(info.sensor, stepVal)
                                    if (event != null) {
                                        val h = info.targetHandler ?: mainHandler
                                        h.post {
                                            try { listener.onSensorChanged(event) } catch (_: Throwable) {}
                                        }
                                    }
                                }
                            }

                            // GNSS 卫星状态主动仿真事件推流（持续向高德/百度等地图 SDK 提供 16 颗有效卫星与强信号）
                            if (activeGnssCallbacks.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                val status = buildSyntheticGnssStatus()
                                if (status != null) {
                                    val iterator = activeGnssCallbacks.entries.iterator()
                                    while (iterator.hasNext()) {
                                        val entry = iterator.next()
                                        val cb = entry.value.get()
                                        if (cb == null) {
                                            iterator.remove()
                                        } else {
                                            mainHandler.post {
                                                try { cb.onSatelliteStatusChanged(status) } catch (_: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            }

                            // NMEA 0183 原始卫星报文主动仿真流推流 ($GPRMC & $GPGGA)
                            if (activeNmeaListeners.isNotEmpty()) {
                                val nowMillis = System.currentTimeMillis()
                                val sentences = buildNmeaSentences(state, nowMillis)
                                val nmeaEntries = activeNmeaListeners.entries.toList()
                                for (entry in nmeaEntries) {
                                    val info = entry.value
                                    if (!info.isAlive()) {
                                        activeNmeaListeners.remove(entry.key)
                                        continue
                                    }
                                    for (sentence in sentences) {
                                        info.dispatch(sentence, nowMillis, mainHandler)
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
     * 动态构造符合 AOSP 规范的合法虚拟硬件计步器传感器（供模拟器环境免杀注入）
     */
    private fun createFakeStepSensor(type: Int): Sensor? {
        return try {
            val sensor = XposedHelpers.newInstance(Sensor::class.java) as Sensor
            try { XposedHelpers.setIntField(sensor, "mType", type) } catch (_: Throwable) {}
            try { XposedHelpers.setObjectField(sensor, "mName", if (type == Sensor.TYPE_STEP_COUNTER) "BOSCH Step Counter Sensor" else "BOSCH Step Detector Sensor") } catch (_: Throwable) {}
            try { XposedHelpers.setObjectField(sensor, "mVendor", "Bosch Sensortec") } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(sensor, "mVersion", 1) } catch (_: Throwable) {}
            try { XposedHelpers.setFloatField(sensor, "mMaxRange", 100000.0f) } catch (_: Throwable) {}
            try { XposedHelpers.setFloatField(sensor, "mResolution", 1.0f) } catch (_: Throwable) {}
            try { XposedHelpers.setFloatField(sensor, "mPower", 0.05f) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(sensor, "mMinDelay", 0) } catch (_: Throwable) {}
            sensor
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 构造用于主动回调投递的虚拟 SensorEvent 事件实例
     */
    private fun createFakeSensorEvent(sensor: Sensor, stepValue: Float): SensorEvent? {
        return try {
            val event = try {
                XposedHelpers.newInstance(SensorEvent::class.java, 4) as SensorEvent
            } catch (_: Throwable) {
                try {
                    XposedHelpers.newInstance(SensorEvent::class.java, 1) as SensorEvent
                } catch (_: Throwable) {
                    XposedHelpers.newInstance(SensorEvent::class.java) as SensorEvent
                }
            }
            try { XposedHelpers.setObjectField(event, "sensor", sensor) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(event, "accuracy", SensorManager.SENSOR_STATUS_ACCURACY_HIGH) } catch (_: Throwable) {}
            try { XposedHelpers.setLongField(event, "timestamp", SystemClock.elapsedRealtimeNanos()) } catch (_: Throwable) {}
            val values = event.values
            if (values != null && values.isNotEmpty()) {
                values[0] = stepValue
            }
            event
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 构造用于主动回调投递的高保真 3D 锁定 GnssStatus 实例 (支持 12 颗 GPS + 4 颗北斗卫星)
     */
    private fun buildSyntheticGnssStatus(): android.location.GnssStatus? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val builder = android.location.GnssStatus.Builder()
                // 12 GPS 卫星 (1~12)
                for (i in 1..12) {
                    builder.addSatellite(
                        android.location.GnssStatus.CONSTELLATION_GPS,
                        i,
                        33.5f + (i % 5) * 1.8f, // 33.5 ~ 40.7 dB-Hz 强卫星载噪比
                        (35.0f + (i * 3) % 40).toFloat(),   // elevation
                        ((i * 28.0f) % 360f).toFloat(),     // azimuth
                        true,                   // hasEphemeris
                        true,                   // hasAlmanac
                        true,                   // usedInFix (高德定位判定 GPS_ACCURACY_GOOD 的关键依据)
                        true,                   // hasCarrierFrequency
                        1575420000.0f,          // carrierFrequencyHz (L1)
                        true,                   // hasBasebandCn0DbHz
                        30.5f                   // basebandCn0DbHz
                    )
                }
                // 4 北斗卫星 (1~4)
                for (i in 1..4) {
                    builder.addSatellite(
                        android.location.GnssStatus.CONSTELLATION_BEIDOU,
                        i,
                        35.0f + (i % 3) * 2.0f,
                        (55.0f + (i * 4) % 25).toFloat(),
                        ((i * 75.0f) % 360f).toFloat(),
                        true,
                        true,
                        true,
                        true,
                        1561098000.0f,          // carrierFrequencyHz (B1)
                        true,
                        32.0f
                    )
                }
                return builder.build()
            } catch (_: Throwable) {}
        }
        return null
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
     * 纳秒级单调时钟与精度补齐：严禁恒定精度，生成 3.5m ~ 8.0m 动态波动值，并附带完备卫星星历 Extras
     */
    private fun buildSpoofedLocation(provider: String, state: RemoteState): Location {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val nowMillis = System.currentTimeMillis()
        val dynamicAccuracy = (3.5f + (Math.sin(nowMillis / 1200.0) * 2.0 + ((nowNanos % 250).toFloat() / 100.0f)).toFloat()).coerceIn(3.5f, 8.0f)

        val extras = Bundle().apply {
            putInt("satellites", 16)
            putInt("maxSatellites", 28)
            putFloat("meanCn0", 33.5f)
            putInt("numSatellitesInView", 16)
            putBoolean("mockLocation", false)
            putBoolean("isMock", false)
        }

        return Location(provider).apply {
            latitude = state.latitude
            longitude = state.longitude
            altitude = state.altitude
            speed = state.speed
            bearing = state.bearing
            accuracy = dynamicAccuracy
            time = nowMillis
            elapsedRealtimeNanos = nowNanos
            this.extras = extras
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 1.0f
                speedAccuracyMetersPerSecond = 0.2f
                verticalAccuracyMeters = 1.5f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    mslAltitudeMeters = state.altitude
                    mslAltitudeAccuracyMeters = 1.0f
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * 合成标准 NMEA 0183 卫星报文 ($GPRMC 与 $GPGGA)
     */
    private fun buildNmeaSentences(state: RemoteState, timestamp: Long): List<String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestamp
        }
        val utcTime = String.format(
            Locale.US,
            "%02d%02d%02d.%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND),
            calendar.get(Calendar.MILLISECOND) / 10
        )
        val utcDate = String.format(
            Locale.US,
            "%02d%02d%02d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR) % 100
        )

        val latDeg = Math.abs(state.latitude).toInt()
        val latMin = (Math.abs(state.latitude) - latDeg) * 60.0
        val latHem = if (state.latitude >= 0) "N" else "S"
        val latStr = String.format(Locale.US, "%02d%07.4f,%s", latDeg, latMin, latHem)

        val lonDeg = Math.abs(state.longitude).toInt()
        val lonMin = (Math.abs(state.longitude) - lonDeg) * 60.0
        val lonHem = if (state.longitude >= 0) "E" else "W"
        val lonStr = String.format(Locale.US, "%03d%07.4f,%s", lonDeg, lonMin, lonHem)

        val speedKnots = state.speed * 1.943844f
        val speedStr = String.format(Locale.US, "%05.1f", speedKnots)
        val bearingStr = String.format(Locale.US, "%05.1f", state.bearing)
        val altStr = String.format(Locale.US, "%.1f", state.altitude)

        val rmcPayload = "GPRMC,$utcTime,A,$latStr,$lonStr,$speedStr,$bearingStr,$utcDate,,,A"
        val rmc = formatNmeaSentence(rmcPayload)

        val ggaPayload = "GPGGA,$utcTime,$latStr,$lonStr,1,16,0.8,$altStr,M,0.0,M,,"
        val gga = formatNmeaSentence(ggaPayload)

        return listOf(rmc, gga)
    }

    private fun formatNmeaSentence(payload: String): String {
        var checksum = 0
        for (char in payload) {
            checksum = checksum xor char.code
        }
        return "\$$payload*${String.format(Locale.US, "%02X", checksum)}"
    }

    /**
     * 通过 ContentProvider 跨进程高速获取模拟状态（具备 call + query 双模降级兜底，绝不弄崩宿主 App）
     */
    private fun getSimState(): RemoteState? {
        val now = System.currentTimeMillis()
        if (cachedState != null && now - lastFetchTime < 250L) {
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
