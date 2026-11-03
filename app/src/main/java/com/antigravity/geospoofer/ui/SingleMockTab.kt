package com.antigravity.geospoofer.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.core.CoordinateUtils
import com.antigravity.geospoofer.core.FavoritePlace
import com.antigravity.geospoofer.core.FavoritePlacesHelper
import com.antigravity.geospoofer.ui.theme.*

@Composable
fun SingleMockTab(
    mapController: MapController,
    isMapExpanded: Boolean,
    onToggleMapExpand: () -> Unit,
    isMocking: Boolean,
    onStartMock: (Double, Double, Boolean) -> Unit,
    onStopMock: () -> Unit,
    favoritePlaces: List<FavoritePlace>,
    onUpdateFavoritePlaces: (List<FavoritePlace>) -> Unit,
    onOpenAddFavoriteDialog: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var latText by remember { mutableStateOf("39.908823") }
    var lonText by remember { mutableStateOf("116.397470") }
    var coordSystemSingle by remember { mutableStateOf("GCJ02") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 🗺️ 可视化交互地图卡片
        InteractiveMapCard(
            controller = mapController,
            mode = "single",
            height = if (isMapExpanded) 480.dp else 300.dp,
            onSinglePointSelected = { lat, lon ->
                latText = "%.6f".format(lat)
                lonText = "%.6f".format(lon)
            },
            onToggleExpand = onToggleMapExpand
        )

        // ⭐ 常用打卡收藏卡片
        GlassCard(
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = TechAmberLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "常用打卡地点收藏",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                TextButton(
                    onClick = {
                        val lat = latText.toDoubleOrNull() ?: 39.908823
                        val lon = lonText.toDoubleOrNull() ?: 116.397470
                        onOpenAddFavoriteDialog(lat, lon)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("收藏当前点", fontSize = 11.sp, color = TechBlueLight)
                }
            }

            if (favoritePlaces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(favoritePlaces) { place ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkInputBg,
                            border = BorderStroke(1.dp, DarkDivider),
                            modifier = Modifier.clickable {
                                latText = "%.6f".format(place.latitude)
                                lonText = "%.6f".format(place.longitude)
                                coordSystemSingle = "GCJ02"
                                mapController.setSinglePoint(place.latitude, place.longitude)
                                mapController.flyToLocation(place.latitude, place.longitude)
                                if (isMocking) {
                                    onStartMock(place.latitude, place.longitude, true)
                                }
                                Toast.makeText(context, "已定位至: ${place.name}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = TechBlue.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        place.tag,
                                        fontSize = 10.sp,
                                        color = TechBlueLight,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    place.name,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = {
                                        val updated = FavoritePlacesHelper.deleteFavorite(context, place.id)
                                        onUpdateFavoritePlaces(updated)
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 📍 目标坐标与系统校准卡片
        GlassCard(
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = "目标坐标设置",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        it.toDoubleOrNull()?.let { lat ->
                            lonText.toDoubleOrNull()?.let { lon ->
                                mapController.setSinglePoint(lat, lon)
                            }
                        }
                    },
                    label = { Text("纬度 Latitude", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkInputBg,
                        unfocusedContainerColor = DarkInputBg,
                        focusedBorderColor = TechBlue,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedLabelColor = TechBlueLight,
                        unfocusedLabelColor = TextSecondary
                    )
                )

                OutlinedTextField(
                    value = lonText,
                    onValueChange = {
                        lonText = it
                        latText.toDoubleOrNull()?.let { lat ->
                            it.toDoubleOrNull()?.let { lon ->
                                mapController.setSinglePoint(lat, lon)
                            }
                        }
                    },
                    label = { Text("经度 Longitude", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkInputBg,
                        unfocusedContainerColor = DarkInputBg,
                        focusedBorderColor = TechBlue,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedLabelColor = TechBlueLight,
                        unfocusedLabelColor = TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "坐标系标准 (自动适配偏转算法):",
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val systems = listOf(
                    Triple("GCJ02", "高德/腾讯 (GCJ-02)", "主流国产地图标定"),
                    Triple("WGS84", "国际 GPS (WGS-84)", "原生硬件无偏转"),
                    Triple("BD09", "百度地图 (BD-09)", "百度二阶偏转")
                )
                systems.forEach { (code, name, _) ->
                    val isSelected = coordSystemSingle == code
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) TechBlue.copy(alpha = 0.2f) else DarkInputBg,
                        border = BorderStroke(1.dp, if (isSelected) TechBlue else DarkCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { coordSystemSingle = code }
                    ) {
                        Text(
                            text = name,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TechBlueLight else TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "快捷城市预设:",
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            val cities = listOf(
                Pair("北京", Pair(39.908823, 116.397470)),
                Pair("上海", Pair(31.239688, 121.499763)),
                Pair("深圳", Pair(22.540503, 113.934528)),
                Pair("广州", Pair(23.129110, 113.264385)),
                Pair("杭州", Pair(30.259200, 120.149500))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cities.forEach { (cityName, coords) ->
                    val isCitySelected = latText == "%.6f".format(coords.first) && lonText == "%.6f".format(coords.second)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCitySelected) TechCyan.copy(alpha = 0.2f) else DarkInputBg,
                        border = BorderStroke(1.dp, if (isCitySelected) TechCyan else DarkCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                latText = "%.6f".format(coords.first)
                                lonText = "%.6f".format(coords.second)
                                mapController.setSinglePoint(coords.first, coords.second)
                                mapController.flyToLocation(coords.first, coords.second)
                            }
                    ) {
                        Text(
                            text = cityName,
                            fontSize = 11.sp,
                            fontWeight = if (isCitySelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCitySelected) TechCyanLight else TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 🚀 主执行操作按钮
        PrimaryActionButton(
            text = if (isMocking) "停止定位模拟" else "开始定位模拟",
            icon = if (isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
            isActive = isMocking,
            onClick = {
                if (isMocking) {
                    onStopMock()
                } else {
                    val lat = latText.toDoubleOrNull() ?: 39.908823
                    val lon = lonText.toDoubleOrNull() ?: 116.397470
                    val finalCoords = when (coordSystemSingle) {
                        "BD09" -> CoordinateUtils.bd09ToWgs84(lat, lon)
                        "GCJ02" -> CoordinateUtils.gcj02ToWgs84(lat, lon)
                        else -> CoordinateUtils.LatLng(lat, lon)
                    }
                    onStartMock(finalCoords.latitude, finalCoords.longitude, false)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
