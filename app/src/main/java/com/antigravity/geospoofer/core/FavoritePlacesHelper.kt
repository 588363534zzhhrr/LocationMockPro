package com.antigravity.geospoofer.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 常用打卡/定位目标地点收藏数据模型
 */
data class FavoritePlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val tag: String = "常去"
)

/**
 * 常用地点本地存储与持久化管理助手
 */
object FavoritePlacesHelper {
    private const val FILE_NAME = "favorite_places.json"

    private val DEFAULT_FAVORITES = listOf(
        FavoritePlace("1", "北京天安门广场", 39.908823, 116.397470, "地标"),
        FavoritePlace("2", "奥林匹克森林公园", 40.015200, 116.388800, "跑步"),
        FavoritePlace("3", "上海外滩陈毅广场", 31.239688, 121.499763, "漫步"),
        FavoritePlace("4", "深圳市民中心", 22.543100, 114.057900, "办公"),
        FavoritePlace("5", "杭州西湖断桥", 30.259200, 120.149500, "景区"),
        FavoritePlace("6", "成都天府广场", 30.657400, 104.066500, "商圈")
    )

    fun loadFavorites(context: Context): List<FavoritePlace> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            saveFavorites(context, DEFAULT_FAVORITES)
            return DEFAULT_FAVORITES
        }
        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<FavoritePlace>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    FavoritePlace(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        tag = obj.optString("tag", "常去")
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_FAVORITES else list
        } catch (e: Exception) {
            DEFAULT_FAVORITES
        }
    }

    fun saveFavorites(context: Context, list: List<FavoritePlace>) {
        val array = JSONArray()
        list.forEach { place ->
            val obj = JSONObject().apply {
                put("id", place.id)
                put("name", place.name)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
                put("tag", place.tag)
            }
            array.put(obj)
        }
        File(context.filesDir, FILE_NAME).writeText(array.toString(2))
    }

    fun addFavorite(context: Context, name: String, lat: Double, lon: Double, tag: String = "常去"): List<FavoritePlace> {
        val current = loadFavorites(context).toMutableList()
        current.add(0, FavoritePlace(System.currentTimeMillis().toString(), name, lat, lon, tag))
        saveFavorites(context, current)
        return current
    }

    fun deleteFavorite(context: Context, id: String): List<FavoritePlace> {
        val current = loadFavorites(context).filter { it.id != id }
        saveFavorites(context, current)
        return current
    }
}
