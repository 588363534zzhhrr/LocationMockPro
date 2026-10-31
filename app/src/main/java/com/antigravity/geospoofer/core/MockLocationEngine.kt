package com.antigravity.geospoofer.core

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import java.util.*
import kotlin.math.*

/**
 * 专业级模拟定位注入引擎
 * 封装 Android 底层测试提供者机制，并提供多环境自然物理信号微抖动、全套卫星星历 Bundle、标准 NMEA-0183 语句仿真
 */
class MockLocationEngine(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val random = Random()

    @Volatile
    var isRunning: Boolean = false
        private set

    var currentLatitude: Double = 39.908823 // 默认北京天安门附近
        private set
    var currentLongitude: Double = 116.397470
        private set
    var currentSpeedMps: Float = 0f
    var currentBearing: Float = 0f
    var currentAltitude: Double = 35.0

    // 自然物理信号微抖动开关与环境预设
    var enableNaturalJitter: Boolean = true
    var currentJitterProfile: JitterProfile = JitterProfile.OPEN_SKY

    // 最新生成的真实 NMEA-0183 语句（供底层监测与反判虚防御）
    var lastNmeaRmc: String = ""
        private set
    var lastNmeaGga: String = ""
        private set
    var lastSatelliteCount: Int = 16
        private set
    var lastMeanCn0: Float = 33.5f
        private set

    /**
     * 拟真信号扰动环境预设
     */
    enum class JitterProfile(
        val label: String,
        val sigmaMeters: Double,
        val baseSatellites: Int,
        val baseAccuracyMeters: Float
    ) {
        OPEN_SKY("开阔平原/操场 (高精)", 0.25, 17, 2.5f),
        URBAN_CANYON("城市街道/大厦 (微扰)", 1.20, 13, 5.0f),
        INDOOR_DRIFT("弱信号/室内 (自然漂移)", 2.80, 8, 12.0f),
        NONE("纯净定点 (无微抖动)", 0.0, 15, 3.0f)
    }

    /**
     * 启动并注册测试提供者
     */
    @Synchronized
    fun start(): Boolean {
        if (isRunning) return true

        return try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                registerProvider(provider)
            }
            isRunning = true
            true
        } catch (e: SecurityException) {
            e.printStackTrace()
            isRunning = false
            false
        }
    }

    /**
     * 注销并恢复系统提供者
     */
    @Synchronized
    fun stop() {
        if (!isRunning) return

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
        isRunning = false
    }

    private fun registerProvider(provider: String) {
        try {
            locationManager.removeTestProvider(provider)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .setHasAltitudeSupport(true)
                .setHasBearingSupport(true)
                .setHasSpeedSupport(true)
                .build()
            locationManager.addTestProvider(provider, properties)
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                provider,
                false, false, false, false,
                true, true, true,
                1, 1
            )
        }
        locationManager.setTestProviderEnabled(provider, true)
    }

    /**
     * 向系统注入新坐标
     */
    fun updateLocation(lat: Double, lon: Double, speed: Float = currentSpeedMps, bearing: Float = currentBearing) {
        currentLatitude = lat
        currentLongitude = lon
        currentSpeedMps = speed
        currentBearing = bearing

        if (!isRunning) return

        var injectedLat = lat
        var injectedLon = lon

        // 根据选定的物理环境预设注入逼真的高斯随机扰动
        val profile = if (enableNaturalJitter) currentJitterProfile else JitterProfile.NONE
        if (profile.sigmaMeters > 0.0) {
            // 纬度每度约 111320 米，经度每度约 111320 * cos(lat) 米
            val latRad = Math.toRadians(lat)
            val sigmaLatDeg = profile.sigmaMeters / 111320.0
            val sigmaLonDeg = profile.sigmaMeters / (111320.0 * cos(latRad).coerceAtLeast(0.1))

            injectedLat += random.nextGaussian() * sigmaLatDeg
            injectedLon += random.nextGaussian() * sigmaLonDeg
        }

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val nowMillis = System.currentTimeMillis()

        // 真实卫星可见数与载噪比（防止检测系统通过 0 卫星或空 extras 判定为虚拟定位）
        val satelliteCount = (profile.baseSatellites + random.nextInt(4)).coerceAtLeast(6)
        val meanCn0 = 31.5f + random.nextFloat() * 4.0f
        lastSatelliteCount = satelliteCount
        lastMeanCn0 = meanCn0
        val extras = Bundle().apply {
            putInt("satellites", satelliteCount)
            putInt("maxSatellites", 28)
            putFloat("meanCn0", meanCn0)
            putInt("numSatellitesInView", satelliteCount)
        }

        // 生成真实有效的 NMEA-0183 协议语句
        lastNmeaRmc = NmeaUtils.buildGprmc(injectedLat, injectedLon, speed, bearing, nowMillis)
        lastNmeaGga = NmeaUtils.buildGpgga(injectedLat, injectedLon, currentAltitude, satelliteCount, nowMillis)

        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val loc = Location(provider).apply {
                latitude = injectedLat
                longitude = injectedLon
                altitude = currentAltitude + (random.nextDouble() * 0.6 - 0.3)
                this.speed = speed
                this.bearing = bearing
                accuracy = profile.baseAccuracyMeters + (random.nextFloat() * 1.2f)
                time = nowMillis
                elapsedRealtimeNanos = nowNanos
                this.extras = extras

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 1.0f
                    speedAccuracyMetersPerSecond = 0.2f
                    verticalAccuracyMeters = 1.5f
                }
            }

            try {
                locationManager.setTestProviderLocation(provider, loc)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/**
 * 国际通用 NMEA-0183 导航数据格式化器
 */
object NmeaUtils {

    private fun toNmeaCoord(decimal: Double, isLat: Boolean): Pair<String, String> {
        val positive = decimal >= 0
        val absVal = abs(decimal)
        val degrees = absVal.toInt()
        val minutes = (absVal - degrees) * 60.0
        val dir = if (isLat) {
            if (positive) "N" else "S"
        } else {
            if (positive) "E" else "W"
        }
        val formatted = if (isLat) {
            String.format(Locale.US, "%02d%07.4f", degrees, minutes)
        } else {
            String.format(Locale.US, "%03d%07.4f", degrees, minutes)
        }
        return Pair(formatted, dir)
    }

    private fun computeChecksum(body: String): String {
        var checksum = 0
        for (ch in body) {
            checksum = checksum xor ch.code
        }
        return String.format(Locale.US, "%02X", checksum)
    }

    fun buildGprmc(lat: Double, lon: Double, speedMps: Float, bearingDeg: Float, timestampMillis: Long): String {
        val (latStr, latDir) = toNmeaCoord(lat, true)
        val (lonStr, lonDir) = toNmeaCoord(lon, false)
        val speedKnots = speedMps * 1.943844f
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampMillis
        }
        val timeStr = String.format(
            Locale.US, "%02d%02d%02d.00",
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND)
        )
        val dateStr = String.format(
            Locale.US, "%02d%02d%02d",
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR) % 100
        )

        val body = "GPRMC,$timeStr,A,$latStr,$latDir,$lonStr,$lonDir,${String.format(Locale.US, "%.2f", speedKnots)},${String.format(Locale.US, "%.2f", bearingDeg)},$dateStr,,,A"
        return "\$$body*${computeChecksum(body)}"
    }

    fun buildGpgga(lat: Double, lon: Double, alt: Double, satellites: Int, timestampMillis: Long): String {
        val (latStr, latDir) = toNmeaCoord(lat, true)
        val (lonStr, lonDir) = toNmeaCoord(lon, false)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampMillis
        }
        val timeStr = String.format(
            Locale.US, "%02d%02d%02d.00",
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND)
        )

        val body = "GPGGA,$timeStr,$latStr,$latDir,$lonStr,$lonDir,1,${String.format(Locale.US, "%02d", satellites)},1.0,${String.format(Locale.US, "%.1f", alt)},M,0.0,M,,"
        return "\$$body*${computeChecksum(body)}"
    }
}
