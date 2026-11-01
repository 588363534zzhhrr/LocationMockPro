package com.antigravity.geospoofer.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

/**
 * 跨进程定位、步数遥测与环境无线电指纹状态 Provider
 * 为 LSPosed Hook 模块提供毫秒级 IPC 通信桥梁，支持在目标应用进程内直接同步获取伪装坐标、传感器步数、企业 Wi-Fi 与基站信息
 */
class LocationStateProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return try {
            instance = this
            appContext = context?.applicationContext
            loadPersistedConfig()
            true
        } catch (_: Throwable) {
            true
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return try {
            val result = Bundle()
            if (method == "getState") {
                synchronized(stateLock) {
                    result.putBoolean(KEY_IS_MOCKING, isMocking)
                    result.putDouble(KEY_LATITUDE, latitude)
                    result.putDouble(KEY_LONGITUDE, longitude)
                    result.putDouble(KEY_ALTITUDE, altitude)
                    result.putFloat(KEY_SPEED, speed)
                    result.putFloat(KEY_BEARING, bearing)
                    result.putLong(KEY_TOTAL_STEPS, totalSteps)
                    result.putInt(KEY_CADENCE, cadenceSpm)
                    result.putLong(KEY_TIMESTAMP, timestamp)

                    // 企业 Wi-Fi 伪装
                    result.putBoolean(KEY_WIFI_MOCK_ENABLED, wifiMockEnabled)
                    result.putString(KEY_WIFI_SSID, wifiSsid)
                    result.putString(KEY_WIFI_BSSID, wifiBssid)
                    result.putInt(KEY_WIFI_RSSI, wifiRssi)

                    // 基站 Cell ID 伪装
                    result.putBoolean(KEY_CELL_MOCK_ENABLED, cellMockEnabled)
                    result.putInt(KEY_CELL_MCC, cellMcc)
                    result.putInt(KEY_CELL_MNC, cellMnc)
                    result.putInt(KEY_CELL_LAC, cellLac)
                    result.putInt(KEY_CELL_CID, cellCid)
                }
            }
            result
        } catch (_: Throwable) {
            Bundle()
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return try {
            val columns = arrayOf(
                KEY_IS_MOCKING, KEY_LATITUDE, KEY_LONGITUDE, KEY_ALTITUDE,
                KEY_SPEED, KEY_BEARING, KEY_TOTAL_STEPS, KEY_CADENCE, KEY_TIMESTAMP,
                KEY_WIFI_MOCK_ENABLED, KEY_WIFI_SSID, KEY_WIFI_BSSID, KEY_WIFI_RSSI,
                KEY_CELL_MOCK_ENABLED, KEY_CELL_MCC, KEY_CELL_MNC, KEY_CELL_LAC, KEY_CELL_CID
            )
            val cursor = MatrixCursor(columns)
            synchronized(stateLock) {
                cursor.addRow(
                    arrayOf(
                        if (isMocking) 1 else 0, latitude, longitude, altitude,
                        speed, bearing, totalSteps, cadenceSpm, timestamp,
                        if (wifiMockEnabled) 1 else 0, wifiSsid, wifiBssid, wifiRssi,
                        if (cellMockEnabled) 1 else 0, cellMcc, cellMnc, cellLac, cellCid
                    )
                )
            }
            cursor
        } catch (_: Throwable) {
            null
        }
    }
    override fun getType(uri: Uri): String? = "vnd.android.cursor.dir/vnd.com.antigravity.geospoofer.state"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.antigravity.geospoofer.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/state")

        const val KEY_IS_MOCKING = "is_mocking"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ALTITUDE = "altitude"
        const val KEY_SPEED = "speed"
        const val KEY_BEARING = "bearing"
        const val KEY_TOTAL_STEPS = "total_steps"
        const val KEY_CADENCE = "cadence_spm"
        const val KEY_TIMESTAMP = "timestamp"

        const val KEY_WIFI_MOCK_ENABLED = "wifi_mock_enabled"
        const val KEY_WIFI_SSID = "wifi_ssid"
        const val KEY_WIFI_BSSID = "wifi_bssid"
        const val KEY_WIFI_RSSI = "wifi_rssi"

        const val KEY_CELL_MOCK_ENABLED = "cell_mock_enabled"
        const val KEY_CELL_MCC = "cell_mcc"
        const val KEY_CELL_MNC = "cell_mnc"
        const val KEY_CELL_LAC = "cell_lac"
        const val KEY_CELL_CID = "cell_cid"

        private const val PREFS_NAME = "location_mock_pro_stealth_prefs"

        private val stateLock = Any()
        private var instance: LocationStateProvider? = null
        private var appContext: Context? = null

        @Volatile var isMocking: Boolean = false
        @Volatile var latitude: Double = 39.908823
        @Volatile var longitude: Double = 116.397470
        @Volatile var altitude: Double = 35.0
        @Volatile var speed: Float = 0f
        @Volatile var bearing: Float = 0f
        @Volatile var totalSteps: Long = 0L
        @Volatile var cadenceSpm: Int = 0
        @Volatile var timestamp: Long = System.currentTimeMillis()

        // Wi-Fi 伪装状态
        @Volatile var wifiMockEnabled: Boolean = false
        @Volatile var wifiSsid: String = "Company-Office-5G"
        @Volatile var wifiBssid: String = "00:1a:2b:3c:4d:5e"
        @Volatile var wifiRssi: Int = -52

        // 基站伪装状态 (中国移动典型参数)
        @Volatile var cellMockEnabled: Boolean = false
        @Volatile var cellMcc: Int = 460
        @Volatile var cellMnc: Int = 0
        @Volatile var cellLac: Int = 41021
        @Volatile var cellCid: Int = 1892341

        private fun loadPersistedConfig() {
            val ctx = appContext ?: return
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            wifiMockEnabled = prefs.getBoolean(KEY_WIFI_MOCK_ENABLED, false)
            wifiSsid = prefs.getString(KEY_WIFI_SSID, "Company-Office-5G") ?: "Company-Office-5G"
            wifiBssid = prefs.getString(KEY_WIFI_BSSID, "00:1a:2b:3c:4d:5e") ?: "00:1a:2b:3c:4d:5e"
            wifiRssi = prefs.getInt(KEY_WIFI_RSSI, -52)

            cellMockEnabled = prefs.getBoolean(KEY_CELL_MOCK_ENABLED, false)
            cellMcc = prefs.getInt(KEY_CELL_MCC, 460)
            cellMnc = prefs.getInt(KEY_CELL_MNC, 0)
            cellLac = prefs.getInt(KEY_CELL_LAC, 41021)
            cellCid = prefs.getInt(KEY_CELL_CID, 1892341)
        }

        /**
         * 更新当前模拟定位核心状态（由 Service 与 Engine 实时调用）
         */
        fun updateLocation(
            lat: Double,
            lon: Double,
            alt: Double,
            spd: Float,
            brg: Float,
            active: Boolean = true
        ) {
            synchronized(stateLock) {
                latitude = lat
                longitude = lon
                altitude = alt
                speed = spd
                bearing = brg
                isMocking = active
                timestamp = System.currentTimeMillis()
            }
        }

        /**
         * 更新计步器生理步数（由 RouteSimulator 实时联动）
         */
        fun updateSteps(steps: Long, cadence: Int) {
            synchronized(stateLock) {
                totalSteps = steps
                cadenceSpm = cadence
            }
        }

        /**
         * 更新并持久化企业 Wi-Fi 伪装配置
         */
        fun updateWifiSpoof(enabled: Boolean, ssid: String, bssid: String, rssi: Int = -52) {
            synchronized(stateLock) {
                wifiMockEnabled = enabled
                wifiSsid = ssid
                wifiBssid = bssid
                wifiRssi = rssi
            }
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.apply {
                putBoolean(KEY_WIFI_MOCK_ENABLED, enabled)
                putString(KEY_WIFI_SSID, ssid)
                putString(KEY_WIFI_BSSID, bssid)
                putInt(KEY_WIFI_RSSI, rssi)
                apply()
            }
        }

        /**
         * 更新并持久化企业/公网基站 Cell ID 伪装配置
         */
        fun updateCellSpoof(enabled: Boolean, mcc: Int, mnc: Int, lac: Int, cid: Int) {
            synchronized(stateLock) {
                cellMockEnabled = enabled
                cellMcc = mcc
                cellMnc = mnc
                cellLac = lac
                cellCid = cid
            }
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.apply {
                putBoolean(KEY_CELL_MOCK_ENABLED, enabled)
                putInt(KEY_CELL_MCC, mcc)
                putInt(KEY_CELL_MNC, mnc)
                putInt(KEY_CELL_LAC, lac)
                putInt(KEY_CELL_CID, cid)
                apply()
            }
        }

        /**
         * 供目标进程 Hook 代码调用的安全数据读取工具方法
         */
        fun fetchRemoteState(context: Context): Bundle? {
            return try {
                context.contentResolver.call(
                    Uri.parse("content://$AUTHORITY"),
                    "getState",
                    null,
                    null
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
