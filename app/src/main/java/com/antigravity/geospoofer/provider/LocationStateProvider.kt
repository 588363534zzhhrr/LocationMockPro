package com.antigravity.geospoofer.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * 跨进程定位与步数遥测状态 Provider
 * 为 LSPosed Hook 模块提供毫秒级 IPC 通信桥梁，支持在目标应用进程内直接同步获取伪装坐标与传感器步数
 */
class LocationStateProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        instance = this
        appContext = context?.applicationContext
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
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
            }
        }
        return result
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
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
