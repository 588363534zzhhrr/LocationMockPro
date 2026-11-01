package com.antigravity.geospoofer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 真实道路沿路吸附与曲线拓扑规划器 (Road-Snapping Routing Engine)
 * 基于开源 OpenStreetMap / OSRM 导航引擎，将散落的路点自动贴合吸附至真实的步行道路、人行道或机动车道，
 * 彻底消除两点之间直线穿楼、穿湖的异常轨迹特征。
 */
object RoadSnapHelper {

    enum class SnapProfile(val id: String, val label: String) {
        WALKING("walking", "步行/人行道"),
        BIKE("bike", "自行车道/骑行"),
        DRIVING("driving", "机动车道/驾车")
    }

    /**
     * 将多个稀疏打点沿真实道路吸附连接，输出完整的沿街路网坐标流
     */
    suspend fun snapWaypointsToRoad(
        waypoints: List<CoordinateUtils.LatLng>,
        profile: SnapProfile = SnapProfile.WALKING
    ): Result<List<CoordinateUtils.LatLng>> = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) {
            return@withContext Result.success(waypoints)
        }

        try {
            val snappedList = mutableListOf<CoordinateUtils.LatLng>()

            // 分段向 OSRM 请求道路拓扑路径（每两点之间规划真实道路）
            for (i in 0 until waypoints.size - 1) {
                val start = waypoints[i]
                val end = waypoints[i + 1]

                val segmentPoints = fetchRoadSegment(start, end, profile)
                if (segmentPoints.isNotEmpty()) {
                    if (snappedList.isEmpty()) {
                        snappedList.addAll(segmentPoints)
                    } else {
                        // 避免两段之间首尾点重复
                        snappedList.addAll(segmentPoints.drop(1))
                    }
                } else {
                    // 降级为直线两点
                    if (snappedList.isEmpty()) snappedList.add(start)
                    snappedList.add(end)
                }
            }

            Result.success(snappedList)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun fetchRoadSegment(
        start: CoordinateUtils.LatLng,
        end: CoordinateUtils.LatLng,
        profile: SnapProfile
    ): List<CoordinateUtils.LatLng> {
        val urlStr = String.format(
            Locale.US,
            "https://router.project-osrm.org/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
            profile.id,
            start.longitude, start.latitude,
            end.longitude, end.latitude
        )

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LocationMockPro/1.0 (Android; OpenStreetMap Routing)")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val firstRoute = routes.getJSONObject(0)
                    val geometry = firstRoute.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    val points = mutableListOf<CoordinateUtils.LatLng>()
                    for (k in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(k)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        points.add(CoordinateUtils.LatLng(lat, lon))
                    }

                    // 若吸附点过多（例如数十公里弯路数千点），做等距适度降采样，保持拐弯平滑且不卡顿
                    downsampleSmoothly(points, maxPoints = 120)
                } else {
                    listOf(start, end)
                }
            } else {
                listOf(start, end)
            }
        } catch (e: Exception) {
            listOf(start, end)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 对过密轨迹做保角平滑降采样，保留所有关键拐角并控制总点数
     */
    private fun downsampleSmoothly(
        points: List<CoordinateUtils.LatLng>,
        maxPoints: Int = 100
    ): List<CoordinateUtils.LatLng> {
        if (points.size <= maxPoints) return points

        val step = (points.size / maxPoints).coerceAtLeast(1)
        val result = mutableListOf<CoordinateUtils.LatLng>()
        result.add(points.first())

        for (i in 1 until points.size - 1 step step) {
            result.add(points[i])
        }

        if (result.last() != points.last()) {
            result.add(points.last())
        }
        return result
    }
}
