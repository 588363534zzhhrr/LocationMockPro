package com.antigravity.geospoofer.core

import kotlin.math.*

/**
 * 坐标系转换工具类
 * 支持 WGS-84（GPS国际标准）与 GCJ-02（火星坐标系，国内高德/腾讯地图使用）的高精度互转
 * 以及距离与方位角测算
 */
object CoordinateUtils {

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val PI = Math.PI

    data class LatLng(val latitude: Double, val longitude: Double)

    /**
     * 判断坐标是否在中国境内
     */
    fun outOfChina(lat: Double, lon: Double): Boolean {
        if (lon < 72.004 || lon > 137.8347) return true
        if (lat < 0.8293 || lat > 55.8271) return true
        return false
    }

    /**
     * WGS-84 转换为 GCJ-02
     */
    fun wgs84ToGcj02(wgsLat: Double, wgsLon: Double): LatLng {
        if (outOfChina(wgsLat, wgsLon)) {
            return LatLng(wgsLat, wgsLon)
        }
        var dLat = transformLat(wgsLon - 105.0, wgsLat - 35.0)
        var dLon = transformLon(wgsLon - 105.0, wgsLat - 35.0)
        val radLat = wgsLat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return LatLng(wgsLat + dLat, wgsLon + dLon)
    }

    /**
     * GCJ-02 转换为 WGS-84
     */
    fun gcj02ToWgs84(gcjLat: Double, gcjLon: Double): LatLng {
        if (outOfChina(gcjLat, gcjLon)) {
            return LatLng(gcjLat, gcjLon)
        }
        val gcj = wgs84ToGcj02(gcjLat, gcjLon)
        val dLat = gcj.latitude - gcjLat
        val dLon = gcj.longitude - gcjLon
        return LatLng(gcjLat - dLat, gcjLon - dLon)
    }

    private const val X_PI = PI * 3000.0 / 180.0

    /**
     * 百度坐标系 (BD-09) 转换为 火星坐标系 (GCJ-02)
     */
    fun bd09ToGcj02(bdLat: Double, bdLon: Double): LatLng {
        val x = bdLon - 0.0065
        val y = bdLat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        val gcjLon = z * cos(theta)
        val gcjLat = z * sin(theta)
        return LatLng(gcjLat, gcjLon)
    }

    /**
     * 火星坐标系 (GCJ-02) 转换为 百度坐标系 (BD-09)
     */
    fun gcj02ToBd09(gcjLat: Double, gcjLon: Double): LatLng {
        val z = sqrt(gcjLon * gcjLon + gcjLat * gcjLat) + 0.00002 * sin(gcjLat * X_PI)
        val theta = atan2(gcjLat, gcjLon) + 0.000003 * cos(gcjLon * X_PI)
        val bdLon = z * cos(theta) + 0.0065
        val bdLat = z * sin(theta) + 0.006
        return LatLng(bdLat, bdLon)
    }

    /**
     * 百度坐标系 (BD-09) 转换为 GPS国际标准 (WGS-84)
     */
    fun bd09ToWgs84(bdLat: Double, bdLon: Double): LatLng {
        val gcj = bd09ToGcj02(bdLat, bdLon)
        return gcj02ToWgs84(gcj.latitude, gcj.longitude)
    }

    /**
     * GPS国际标准 (WGS-84) 转换为 百度坐标系 (BD-09)
     */
    fun wgs84ToBd09(wgsLat: Double, wgsLon: Double): LatLng {
        val gcj = wgs84ToGcj02(wgsLat, wgsLon)
        return gcj02ToBd09(gcj.latitude, gcj.longitude)
    }

    /**
     * 基于球面哈弗辛公式（Haversine）计算两点之间的球面距离（单位：米）
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // 地球平均半径（米）
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2.0).pow(2.0) +
                cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return r * c
    }

    /**
     * 计算从点 1 到点 2 的真北航向角（Bearing，0~360 度）
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val initialBearing = Math.toDegrees(atan2(y, x))

        return ((initialBearing + 360.0) % 360.0).toFloat()
    }

    /**
     * 根据起始坐标、前进方向角（度）和移动距离（米）推算新的坐标点
     */
    fun movePoint(startLat: Double, startLon: Double, distanceMeters: Double, bearingDegrees: Float): LatLng {
        val r = 6371000.0
        val delta = distanceMeters / r
        val theta = Math.toRadians(bearingDegrees.toDouble())

        val phi1 = Math.toRadians(startLat)
        val lambda1 = Math.toRadians(startLon)

        val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
        val phi2 = asin(sinPhi2)
        val y = sin(theta) * sin(delta) * cos(phi1)
        val x = cos(delta) - sin(phi1) * sin(phi2)
        val lambda2 = lambda1 + atan2(y, x)

        return LatLng(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }

    /**
     * 智能生成真实自然的闭环环形路线（如校园跑、公园跑、操场跑）
     * @param centerLat 中心点纬度
     * @param centerLon 中心点经度
     * @param targetDistanceMeters 期望的总闭环里程（米，例如 2000 代表 2.0 km）
     * @param numPoints 拟合控制点数（通常 8~14 个点）
     * @param irregularity 扰动粗糙度（0.0 ~ 0.35），越大形状越有机自然，0 代表标准正圆
     */
    fun generateOrganicLoopRoute(
        centerLat: Double,
        centerLon: Double,
        targetDistanceMeters: Double,
        numPoints: Int = 10,
        irregularity: Double = 0.20
    ): List<LatLng> {
        val radius = targetDistanceMeters / (2 * PI)
        val random = java.util.Random()
        val baseAngleStep = (2 * PI) / numPoints

        val points = mutableListOf<LatLng>()
        for (i in 0 until numPoints) {
            val angle = i * baseAngleStep
            val radiusFactor = 1.0 + (random.nextGaussian() * irregularity).coerceIn(-0.35, 0.35)
            val currentR = radius * radiusFactor
            val bearingDeg = Math.toDegrees(angle).toFloat()
            val pt = movePoint(centerLat, centerLon, currentR, bearingDeg)
            points.add(pt)
        }
        // 闭合回路：将第一个点作为终点
        points.add(points.first())
        return points
    }

    /**
     * 生成标准 400 米国际田联标准田径场跑道闭环路线（包含两条直道与两段半圆弯道，完美适配校园跑、体测与操场巡航）
     * @param centerLat 操场中心纬度
     * @param centerLon 操场中心经度
     * @param orientationDeg 跑道长轴航向角（顺时针度数，0 为南北向，90 为东西向）
     * @param lane 道次（1~8，第1道周长标准 400.0 米，外道依次增加）
     * @param isCounterClockwise 是否逆时针奔跑（IAAF国际田联标准为逆时针左转，默认 true）
     * @param pointsPerCurve 半圆弯道采样点数（默认 10 点，确保圆弧平滑）
     */
    fun generateTrack400m(
        centerLat: Double,
        centerLon: Double,
        orientationDeg: Float = 0f,
        lane: Int = 1,
        isCounterClockwise: Boolean = true,
        pointsPerCurve: Int = 10
    ): List<LatLng> {
        val halfL = 42.195   // 直道半长：IAAF标准直道全长 84.39 米
        // IAAF标准：第1道内突沿半径 36.50m，第1道计算跑线半径 36.80m（+0.30m）
        // 第 2~8 道（分道宽 1.22m），计算跑线距分道线外 0.20m
        val clampedLane = lane.coerceIn(1, 8)
        val radius = if (clampedLane == 1) {
            36.80
        } else {
            36.50 + (clampedLane - 1) * 1.22 + 0.20
        }

        val localCoords = mutableListOf<Pair<Double, Double>>() // (lx: 东向米, ly: 北向米)

        // IAAF 标准逆时针路线（向左转弯）：
        // 1. 东侧直道：从南向北奔跑
        val straightSteps = 4
        for (i in 0 until straightSteps) {
            val y = -halfL + (2.0 * halfL * i / straightSteps)
            localCoords.add(Pair(radius, y))
        }

        // 2. 北侧半圆弯道：从东向西逆时针转弯 (角度 0 -> PI)
        for (i in 0 until pointsPerCurve) {
            val theta = (i.toDouble() / pointsPerCurve) * PI
            val x = radius * cos(theta)
            val y = halfL + radius * sin(theta)
            localCoords.add(Pair(x, y))
        }

        // 3. 西侧直道：从北向南奔跑
        for (i in 0 until straightSteps) {
            val y = halfL - (2.0 * halfL * i / straightSteps)
            localCoords.add(Pair(-radius, y))
        }

        // 4. 南侧半圆弯道：从西向东逆时针转弯 (角度 PI -> 2*PI)
        for (i in 0 until pointsPerCurve) {
            val theta = PI + (i.toDouble() / pointsPerCurve) * PI
            val x = radius * cos(theta)
            val y = -halfL + radius * sin(theta)
            localCoords.add(Pair(x, y))
        }

        // 如果用户需要顺时针，则将局部坐标反向
        val orderedCoords = if (isCounterClockwise) localCoords else localCoords.reversed()

        // 将局部米级坐标基于航向角 orientationDeg 进行顺时针旋转，并计算经纬度
        val normalizedOrientation = ((orientationDeg % 360f) + 360f) % 360f
        val points = mutableListOf<LatLng>()

        for ((lx, ly) in orderedCoords) {
            val dist = hypot(lx, ly)
            if (dist < 0.1) {
                points.add(LatLng(centerLat, centerLon))
            } else {
                // localBearing: 局部真北航向（0°为北，90°为东，180°为南，270°为西）
                val localBearing = Math.toDegrees(atan2(lx, ly))
                val finalBearing = ((localBearing + normalizedOrientation) % 360.0 + 360.0) % 360.0
                points.add(movePoint(centerLat, centerLon, dist, finalBearing.toFloat()))
            }
        }

        // 保证闭合回路：终点精确连接起点
        if (points.isNotEmpty()) {
            points.add(points.first())
        }
        return points
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
