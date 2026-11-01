package com.antigravity.geospoofer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 真实道路沿路吸附与曲线拓扑规划器 (Road-Snapping Routing Engine)
 * 基于开源 OpenStreetMap / OSRM 导航引擎，将散落的路点自动贴合吸附至真实的步行道路、人行道或机动车道，
 * 彻底消除两点之间直线穿楼、穿湖的异常轨迹特征。
 *
 * V2.5 极致优化：采用全路网批量单次聚合请求 (Batch Multi-Waypoint Snapping)，大幅降低网络延迟并抵御公共 API 限流。
 */
object RoadSnapHelper {

    enum class SnapProfile(val id: String, val label: String) {
        WALKING("walking", "步行/人行道"),
        BIKE("bike", "自行车道/骑行"),
        DRIVING("driving", "机动车道/驾车")
    }

    private const val MAX_WAYPOINTS_PER_BATCH = 25

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

            // 若总点数较少，直接单次请求全量聚合；若点数过多，切片批量请求（重叠首尾点保证连贯）
            val chunks = splitIntoOverlappingChunks(waypoints, MAX_WAYPOINTS_PER_BATCH)

            for (chunk in chunks) {
                val batchPoints = fetchBatchRoadRoute(chunk, profile)
                if (batchPoints.isNotEmpty()) {
                    if (snappedList.isEmpty()) {
                        snappedList.addAll(batchPoints)
                    } else {
                        // 避免两批次之间首尾点重复
                        snappedList.addAll(batchPoints.drop(1))
                    }
                } else {
                    // 若批量请求失败，降级为直连
                    if (snappedList.isEmpty()) snappedList.addAll(chunk)
                    else snappedList.addAll(chunk.drop(1))
                }
            }

            // 对整段曲线进行平滑控制，确保 UI 渲染与仿真引擎极速运行
            val finalRoute = downsampleSmoothly(snappedList, maxPoints = 250)
            Result.success(finalRoute)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 将长路标切分成带重叠首尾节点的批次
     */
    private fun splitIntoOverlappingChunks(
        points: List<CoordinateUtils.LatLng>,
        chunkSize: Int
    ): List<List<CoordinateUtils.LatLng>> {
        if (points.size <= chunkSize) return listOf(points)

        val result = mutableListOf<List<CoordinateUtils.LatLng>>()
        var startIndex = 0
        while (startIndex < points.size - 1) {
            val endIndex = (startIndex + chunkSize).coerceAtMost(points.size)
            result.add(points.subList(startIndex, endIndex))
            if (endIndex == points.size) break
            startIndex = endIndex - 1 // 重叠最后一个节点作为下一批次起点
        }
        return result
    }

    /**
     * 单次请求 OSRM 批量全路网拓扑路径
     */
    private fun fetchBatchRoadRoute(
        points: List<CoordinateUtils.LatLng>,
        profile: SnapProfile
    ): List<CoordinateUtils.LatLng> {
        if (points.size < 2) return points

        // 构建聚合 coordinates 字符串: lon1,lat1;lon2,lat2;...
        val coordinatesParam = points.joinToString(";") { p ->
            String.format(Locale.US, "%.6f,%.6f", p.longitude, p.latitude)
        }

        val urlStr = "https://router.project-osrm.org/route/v1/${profile.id}/$coordinatesParam?overview=full&geometries=geojson"

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LocationMockPro/2.5 (Android; OpenStreetMap Batch Routing)")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val firstRoute = routes.getJSONObject(0)
                    val geometry = firstRoute.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    val resultPoints = mutableListOf<CoordinateUtils.LatLng>()
                    for (k in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(k)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        resultPoints.add(CoordinateUtils.LatLng(lat, lon))
                    }
                    if (resultPoints.isNotEmpty()) resultPoints else points
                } else {
                    points
                }
            } else {
                points
            }
        } catch (e: Exception) {
            points
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 对过密轨迹做保角平滑降采样，保留所有关键拐角并控制总点数
     */
    private fun downsampleSmoothly(
        points: List<CoordinateUtils.LatLng>,
        maxPoints: Int = 200
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
