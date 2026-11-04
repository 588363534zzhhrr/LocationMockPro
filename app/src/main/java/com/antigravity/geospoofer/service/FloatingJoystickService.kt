package com.antigravity.geospoofer.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.*

/**
 * 全局灵动微型悬浮控制岛服务 (Floating HUD & Smart Joystick Pro)
 * 可以在任意第三方 App（游戏、打卡、微信等）浮层显示：
 * 1. 灵动极简药丸模式（点击展开，极简贴边不遮挡）与 完整控制台模式无缝切换
 * 2. 实时显示巡航时速、里程、步频、海拔起伏与爬升
 * 3. 支持无级微调摇杆（360度模拟）+ 四向独立米级微移按钮（⬆️ ⬇️ ⬅️ ➡️ 精准挪动 2 米）
 * 4. 目标达成驻留指示与动态调速
 */
class FloatingJoystickService : Service() {

    private lateinit var windowManager: WindowManager
    private var windowParams: WindowManager.LayoutParams? = null
    private var rootContainer: FrameLayout? = null
    private var miniHudView: LinearLayout? = null
    private var fullHudView: LinearLayout? = null

    private var mockService: MockLocationService? = null
    private var isBound = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var joystickLoopJob: Job? = null
    private var hudUpdateJob: Job? = null

    private var currentAngle = 0f
    private var currentIntensity = 0f
    private var isCruiseLocked = false

    // 控件引用
    private var miniStatusTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var telemetryTextView: TextView? = null
    private var pauseBtn: TextView? = null
    private var joystickContainer: FrameLayout? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MockLocationService.LocalBinder
            mockService = binder?.getService()
            isBound = true
            listenSimulationUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mockService = null
            isBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bindService(
            Intent(this, MockLocationService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        initFloatingHud()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun initFloatingHud() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 220
        }

        val params = windowParams!!

        rootContainer = FrameLayout(this)

        // ================= A. 极简灵动药丸 (Mini HUD Capsule) =================
        miniHudView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 10, 20, 10)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(0xEE1E1E1E.toInt())
                setStroke(2, 0x664CAF50.toInt())
            }
            visibility = View.GONE // 默认展开控制台
        }

        val miniIcon = TextView(this).apply {
            text = "🕹️"
            textSize = 14f
            setPadding(0, 0, 10, 0)
        }
        miniStatusTextView = TextView(this).apply {
            text = "LocationMockPro"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        val miniExpand = TextView(this).apply {
            text = " ↗️"
            setTextColor(0xFF4CAF50.toInt())
            textSize = 11f
        }

        miniHudView?.addView(miniIcon)
        miniHudView?.addView(miniStatusTextView)
        miniHudView?.addView(miniExpand)

        miniHudView?.setOnClickListener {
            // 点击折叠药丸展开为完整控制面板
            miniHudView?.visibility = View.GONE
            fullHudView?.visibility = View.VISIBLE
            windowManager.updateViewLayout(rootContainer, params)
        }

        // ================= B. 完整控制台 (Full HUD Deck) =================
        fullHudView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 14, 20, 14)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0xEE1A1A1A.toInt())
                setStroke(2, 0x44FFFFFF.toInt())
            }
        }

        // 1. 顶部操作栏
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        statusTextView = TextView(this).apply {
            text = "🚀 准备就绪"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 0, 12, 0)
        }

        pauseBtn = TextView(this).apply {
            text = "⏸️"
            textSize = 14f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                val sim = mockService?.routeSimulator ?: return@setOnClickListener
                if (sim.simulationState.value.isPaused) {
                    mockService?.resumeRoute()
                    text = "⏸️"
                } else {
                    mockService?.pauseRoute()
                    text = "▶️"
                }
            }
        }

        val speedDownBtn = TextView(this).apply {
            text = "➖"
            textSize = 13f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                val sim = mockService?.routeSimulator ?: return@setOnClickListener
                val newSpeed = (sim.customSpeedMps - 0.6f).coerceAtLeast(0.5f)
                mockService?.updateSpeed(newSpeed)
            }
        }

        val speedUpBtn = TextView(this).apply {
            text = "➕"
            textSize = 13f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                val sim = mockService?.routeSimulator ?: return@setOnClickListener
                val newSpeed = (sim.customSpeedMps + 0.6f).coerceAtMost(30f)
                mockService?.updateSpeed(newSpeed)
            }
        }

        val gears = listOf(
            Pair("🚶", 1.4f),
            Pair("🏃", 3.2f),
            Pair("🚴", 5.5f),
            Pair("🚗", 16.6f)
        )
        var gearIndex = 0

        val gearBtn = TextView(this).apply {
            text = gears[gearIndex].first
            textSize = 14f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                gearIndex = (gearIndex + 1) % gears.size
                text = gears[gearIndex].first
                mockService?.updateSpeed(gears[gearIndex].second)
            }
        }

        // 🔒 巡航锁定（自动恒速向前持续巡航，降低手部按压疲劳）
        val cruiseBtn = TextView(this).apply {
            text = "🔒"
            textSize = 14f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                isCruiseLocked = !isCruiseLocked
                if (isCruiseLocked) {
                    text = "🔓"
                    setTextColor(0xFF4CAF50.toInt())
                    if (currentIntensity <= 0f) {
                        currentIntensity = 1.0f
                    }
                    startJoystickLoop()
                } else {
                    text = "🔒"
                    setTextColor(Color.WHITE)
                    currentIntensity = 0f
                    stopJoystickLoop()
                }
            }
        }

        // 折叠为极小药丸按钮
        val collapseBtn = TextView(this).apply {
            text = "🔽"
            textSize = 13f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                fullHudView?.visibility = View.GONE
                miniHudView?.visibility = View.VISIBLE
                windowManager.updateViewLayout(rootContainer, params)
            }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF888888.toInt())
            textSize = 14f
            setPadding(10, 4, 4, 4)
            setOnClickListener {
                stopSelf()
            }
        }

        headerRow.addView(statusTextView)
        headerRow.addView(pauseBtn)
        headerRow.addView(speedDownBtn)
        headerRow.addView(speedUpBtn)
        headerRow.addView(gearBtn)
        headerRow.addView(cruiseBtn)
        headerRow.addView(collapseBtn)
        headerRow.addView(closeBtn)
        fullHudView?.addView(headerRow)

        // 2. 遥测状态副标题（海拔与爬升）
        telemetryTextView = TextView(this).apply {
            text = "⛰️ 待命海拔: 45.0m | 摇杆就绪"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 10f
            setPadding(2, 4, 2, 8)
        }
        fullHudView?.addView(telemetryTextView)

        // 3. 四向独立米级微调控制台 (Cardinal Micro-Nudges: ⬆️ ⬇️ ⬅️ ➡️)
        val nudgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4
                bottomMargin = 6
            }
        }

        val makeNudgeBtn = { label: String, bearing: Float ->
            TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(12, 6, 12, 6)
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0x44333333)
                    setStroke(1, 0x66FFFFFF.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener {
                    mockService?.nudgeLocation(bearing, 2.0)
                }
            }
        }

        nudgeRow.addView(makeNudgeBtn("⬆️ 北微调", 0f))
        nudgeRow.addView(makeNudgeBtn("⬇️ 南", 180f))
        nudgeRow.addView(makeNudgeBtn("⬅️ 西", 270f))
        nudgeRow.addView(makeNudgeBtn("➡️ 东", 90f))
        fullHudView?.addView(nudgeRow)

        // 4. 下方 360 度动态操纵杆
        joystickContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(220, 220).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 8
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x44000000)
                setStroke(2, 0x55FFFFFF.toInt())
            }
        }

        val stickView = TextView(this).apply {
            text = "●"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(80, 80, Gravity.CENTER).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xDD1976D2.toInt())
                    setStroke(2, Color.WHITE)
                }
            }
        }

        joystickContainer?.addView(stickView)
        fullHudView?.addView(joystickContainer)

        val maxRadius = 65.0

        joystickContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val centerX = (joystickContainer?.width?.takeIf { it > 0 }?.toFloat() ?: 220f) / 2f
                    val centerY = (joystickContainer?.height?.takeIf { it > 0 }?.toFloat() ?: 220f) / 2f
                    val dx = event.x - centerX
                    val dy = event.y - centerY
                    val distance = sqrt((dx * dx + dy * dy).toDouble())

                    val clampedDist = distance.coerceAtMost(maxRadius)
                    val rad = atan2(dy.toDouble(), dx.toDouble())

                    var deg = Math.toDegrees(rad).toFloat() + 90f
                    if (deg < 0) deg += 360f

                    currentAngle = deg
                    currentIntensity = (clampedDist / maxRadius).toFloat()

                    stickView.translationX = (cos(rad) * clampedDist).toFloat()
                    stickView.translationY = (sin(rad) * clampedDist).toFloat()

                    startJoystickLoop()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isCruiseLocked) {
                        stickView.translationX = 0f
                        stickView.translationY = 0f
                        currentIntensity = 0f
                        stopJoystickLoop()
                    }
                    true
                }
                else -> false
            }
        }

        // 拖拽手势绑定于状态文本与副标题，避免拦截按钮点击
        bindDragListener(statusTextView!!, params)
        bindDragListener(telemetryTextView!!, params)

        // 极简胶囊药丸拖拽与展开点击响应
        var mInitialX = 0
        var mInitialY = 0
        var mTouchX = 0f
        var mTouchY = 0f
        var mIsClick = false

        miniHudView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mInitialX = params.x
                    mInitialY = params.y
                    mTouchX = event.rawX
                    mTouchY = event.rawY
                    mIsClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - mTouchX).toInt()
                    val dy = (event.rawY - mTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        mIsClick = false
                    }
                    params.x = mInitialX + dx
                    params.y = mInitialY + dy
                    windowManager.updateViewLayout(rootContainer, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (mIsClick) {
                        miniHudView?.visibility = View.GONE
                        fullHudView?.visibility = View.VISIBLE
                        windowManager.updateViewLayout(rootContainer, params)
                    }
                    true
                }
                else -> false
            }
        }

        rootContainer?.addView(miniHudView)
        rootContainer?.addView(fullHudView)
        windowManager.addView(rootContainer, params)
    }

    private fun bindDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootContainer, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun listenSimulationUpdates() {
        hudUpdateJob?.cancel()
        hudUpdateJob = serviceScope.launch {
            mockService?.routeSimulator?.simulationState?.collectLatest { state ->
                if (state.isGoalReached) {
                    val goalText = "🏆 达成目标: ${state.goalSummary}"
                    statusTextView?.text = goalText
                    miniStatusTextView?.text = "🏆 目标已达成驻留中"
                    telemetryTextView?.text = "⛰️ 海拔: ${"%.1f".format(state.currentElevationMeters)}m | 爬升: +${"%.1f".format(state.elevationGainMeters)}m"
                    pauseBtn?.text = "▶️"
                } else if (state.isSimulating) {
                    val speedKmh = state.currentSpeedMps * 3.6f
                    val distKm = state.distanceCoveredMeters / 1000.0
                    val totalKm = state.totalDistanceMeters / 1000.0
                    val pct = (state.progressFraction * 100).toInt()
                    val pausedText = if (state.isPaused) " [暂停]" else ""
                    val shuttleText = if (state.isShuttleReverse) " [返程]" else ""

                    val summary = "${"%.1f".format(speedKmh)}km/h · ${"%.1f".format(distKm)}/${"%.1f".format(totalKm)}km (${pct}%)$pausedText$shuttleText"
                    statusTextView?.text = summary
                    miniStatusTextView?.text = "📍 ${"%.1f".format(speedKmh)}km/h · ${"%.1f".format(distKm)}km"
                    pauseBtn?.text = if (state.isPaused) "▶️" else "⏸️"

                    telemetryTextView?.text = "⛰️ 海拔: ${"%.1f".format(state.currentElevationMeters)}m | 爬升: +${"%.1f".format(state.elevationGainMeters)}m | 🏃 ${state.cadenceSpm} SPM"
                } else {
                    statusTextView?.text = "● 待命中 (摇杆与微调可用)"
                    miniStatusTextView?.text = "● 悬浮摇杆待命中"
                    telemetryTextView?.text = "点击微调键或摇杆可随时挪动位置"
                }
            }
        }
    }

    private fun startJoystickLoop() {
        if (joystickLoopJob?.isActive == true) return
        joystickLoopJob = serviceScope.launch {
            while (isActive && currentIntensity > 0.05f) {
                mockService?.onJoystickMove(currentAngle, currentIntensity)
                delay(200)
            }
        }
    }

    private fun stopJoystickLoop() {
        joystickLoopJob?.cancel()
        joystickLoopJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (rootContainer != null) {
            try {
                windowManager.removeView(rootContainer)
            } catch (_: Exception) {}
            rootContainer = null
        }
        if (isBound) {
            try {
                unbindService(connection)
            } catch (_: Exception) {}
            isBound = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
