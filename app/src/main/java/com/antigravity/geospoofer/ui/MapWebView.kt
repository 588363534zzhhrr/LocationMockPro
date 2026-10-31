package com.antigravity.geospoofer.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antigravity.geospoofer.core.CoordinateUtils
import org.json.JSONArray
import org.json.JSONObject

class MapController {
    var webView: WebView? = null
    var isReady: Boolean = false
    private val pendingCommands = mutableListOf<String>()

    fun executeJs(js: String) {
        val wv = webView
        if (wv != null && isReady) {
            wv.post { wv.evaluateJavascript(js, null) }
        } else {
            pendingCommands.add(js)
        }
    }

    fun onReady() {
        isReady = true
        pendingCommands.forEach { js ->
            webView?.post { webView?.evaluateJavascript(js, null) }
        }
        pendingCommands.clear()
    }

    fun setMode(mode: String) {
        executeJs("window.setMode('$mode');")
    }

    fun setRouteWaypoints(points: List<CoordinateUtils.LatLng>) {
        val json = JSONArray()
        points.forEach { pt ->
            val obj = JSONObject()
            obj.put("lat", pt.latitude)
            obj.put("lon", pt.longitude)
            json.put(obj)
        }
        executeJs("window.setRouteWaypoints(${json.toString()});")
    }

    fun setSinglePoint(lat: Double, lon: Double) {
        executeJs("window.setSinglePoint($lat, $lon, false);")
    }

    fun updateSimPosition(lat: Double, lon: Double, bearing: Float) {
        executeJs("window.updateSimPosition($lat, $lon, $bearing);")
    }

    fun stopSimPosition() {
        executeJs("window.stopSimPosition();")
    }

    fun fitRoute() {
        executeJs("window.fitRoute();")
    }

    fun flyToLocation(lat: Double, lon: Double, zoom: Int = 15) {
        executeJs("window.flyToLocation($lat, $lon, $zoom);")
    }
}

@Composable
fun rememberMapController(): MapController {
    return remember { MapController() }
}

class AndroidMapBridge(
    private val onReady: () -> Unit,
    private val onAddWaypoint: (Double, Double) -> Unit,
    private val onWaypointsChange: (List<CoordinateUtils.LatLng>) -> Unit,
    private val onSinglePointChange: (Double, Double) -> Unit,
    private val onToggleExpand: () -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onWaypointAdded(lat: Double, lon: Double, count: Int) {
        mainHandler.post { onAddWaypoint(lat, lon) }
    }

    @JavascriptInterface
    fun onWaypointsUpdated(jsonStr: String) {
        mainHandler.post {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<CoordinateUtils.LatLng>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    list.add(CoordinateUtils.LatLng(item.getDouble("lat"), item.getDouble("lon")))
                }
                onWaypointsChange(list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun onSinglePointChanged(lat: Double, lon: Double) {
        mainHandler.post { onSinglePointChange(lat, lon) }
    }

    @JavascriptInterface
    fun onToggleExpand() {
        mainHandler.post { onToggleExpand() }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun InteractiveMapCard(
    controller: MapController,
    mode: String = "route", // "route" or "single"
    height: Dp = 360.dp,
    onWaypointAdded: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onWaypointsUpdated: (List<CoordinateUtils.LatLng>) -> Unit = {},
    onSinglePointSelected: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onToggleExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            builtInZoomControls = false
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                        }

                        // Prevent Compose verticalScroll from capturing map pan and pinch gestures
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                            false
                        }

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {}

                        val bridge = AndroidMapBridge(
                            onReady = {
                                controller.onReady()
                                controller.setMode(mode)
                            },
                            onAddWaypoint = onWaypointAdded,
                            onWaypointsChange = onWaypointsUpdated,
                            onSinglePointChange = onSinglePointSelected,
                            onToggleExpand = onToggleExpand
                        )
                        addJavascriptInterface(bridge, "AndroidBridge")

                        controller.webView = this
                        loadUrl("file:///android_asset/map.html")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    LaunchedEffect(mode) {
        controller.setMode(mode)
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.webView = null
            controller.isReady = false
        }
    }
}
