package com.antigravity.geospoofer.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SavedRoute(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val waypoints: List<CoordinateUtils.LatLng>,
    val totalDistanceMeters: Double,
    val createdAt: Long = System.currentTimeMillis()
)

object RouteStorageHelper {
    private const val FILE_NAME = "saved_routes.json"

    /**
     * 读取所有已保存的自定义路线
     */
    fun loadSavedRoutes(context: Context): List<SavedRoute> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SavedRoute>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.getString("name")
                val totalDist = obj.optDouble("totalDistanceMeters", 0.0)
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                val wpArray = obj.getJSONArray("waypoints")
                val waypoints = mutableListOf<CoordinateUtils.LatLng>()
                for (j in 0 until wpArray.length()) {
                    val wp = wpArray.getJSONObject(j)
                    waypoints.add(CoordinateUtils.LatLng(wp.getDouble("lat"), wp.getDouble("lon")))
                }

                list.add(SavedRoute(id, name, waypoints, totalDist, createdAt))
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 保存新路线
     */
    fun saveRoute(context: Context, name: String, waypoints: List<CoordinateUtils.LatLng>): SavedRoute {
        val currentRoutes = loadSavedRoutes(context).toMutableList()

        var totalDist = 0.0
        for (i in 0 until waypoints.size - 1) {
            totalDist += CoordinateUtils.calculateDistance(
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude
            )
        }

        val newRoute = SavedRoute(
            name = name,
            waypoints = waypoints,
            totalDistanceMeters = totalDist
        )
        currentRoutes.add(0, newRoute)
        persist(context, currentRoutes)
        return newRoute
    }

    /**
     * 删除指定路线
     */
    fun deleteRoute(context: Context, id: String) {
        val currentRoutes = loadSavedRoutes(context).filter { it.id != id }
        persist(context, currentRoutes)
    }

    private fun persist(context: Context, routes: List<SavedRoute>) {
        val array = JSONArray()
        routes.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("totalDistanceMeters", r.totalDistanceMeters)
            obj.put("createdAt", r.createdAt)

            val wpArray = JSONArray()
            r.waypoints.forEach { wp ->
                val wpObj = JSONObject()
                wpObj.put("lat", wp.latitude)
                wpObj.put("lon", wp.longitude)
                wpArray.put(wpObj)
            }
            obj.put("waypoints", wpArray)
            array.put(obj)
        }

        val file = File(context.filesDir, FILE_NAME)
        file.writeText(array.toString(2))
    }

    /**
     * 导出为国际通用 GPX 1.1 XML 格式
     */
    fun exportToGpx(name: String, waypoints: List<CoordinateUtils.LatLng>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val timeStr = sdf.format(Date())

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"LocationMockPro\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>").append(name).append("</name>\n")
        sb.append("    <time>").append(timeStr).append("</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(name).append("</name>\n")
        sb.append("    <trkseg>\n")

        waypoints.forEach { pt ->
            sb.append("      <trkpt lat=\"").append(pt.latitude).append("\" lon=\"").append(pt.longitude).append("\" />\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        return sb.toString()
    }

    /**
     * 解析 GPX / KML 格式字符串获取途经点列表（兼容 trkpt、wpt、rtept 及 KML 航线坐标）
     */
    fun importFromGpx(gpxXml: String): List<CoordinateUtils.LatLng> {
        val points = mutableListOf<CoordinateUtils.LatLng>()
        // 1. 匹配标准 GPX <trkpt lat="..." lon="...">、<wpt ...> 以及 <rtept ...>
        val pattern = """<(?:trkpt|wpt|rtept)\s+[^>]*lat=["']([-0-9.]+)["'][^>]*lon=["']([-0-9.]+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val patternAlt = """<(?:trkpt|wpt|rtept)\s+[^>]*lon=["']([-0-9.]+)["'][^>]*lat=["']([-0-9.]+)["']""".toRegex(RegexOption.IGNORE_CASE)

        val matches = pattern.findAll(gpxXml)
        for (m in matches) {
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) {
                points.add(CoordinateUtils.LatLng(lat, lon))
            }
        }

        if (points.isEmpty()) {
            val matchesAlt = patternAlt.findAll(gpxXml)
            for (m in matchesAlt) {
                val lon = m.groupValues[1].toDoubleOrNull()
                val lat = m.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) {
                    points.add(CoordinateUtils.LatLng(lat, lon))
                }
            }
        }

        // 2. 兼容 Google Earth / GIS KML 格式：<coordinates>lon,lat,alt ...</coordinates>
        if (points.isEmpty()) {
            val kmlPattern = """<coordinates>([\s\S]*?)</coordinates>""".toRegex(RegexOption.IGNORE_CASE)
            val kmlMatches = kmlPattern.findAll(gpxXml)
            for (km in kmlMatches) {
                val rawCoordText = km.groupValues[1].trim()
                val tuples = rawCoordText.split("\\s+".toRegex())
                for (tuple in tuples) {
                    val parts = tuple.split(",")
                    if (parts.size >= 2) {
                        val lon = parts[0].trim().toDoubleOrNull()
                        val lat = parts[1].trim().toDoubleOrNull()
                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            points.add(CoordinateUtils.LatLng(lat, lon))
                        }
                    }
                }
            }
        }

        return points
    }
}
