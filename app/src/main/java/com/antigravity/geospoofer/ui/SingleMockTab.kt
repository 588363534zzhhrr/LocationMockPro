package com.antigravity.geospoofer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
    latText: String,
    onLatTextChange: (String) -> Unit,
    lonText: String,
    onLonTextChange: (String) -> Unit,
    coordSystemSingle: String,
    onCoordSystemChange: (String) -> Unit,
    isMocking: Boolean,
    onStartMock: (Double, Double, Boolean) -> Unit,
    onStopMock: () -> Unit,
    favoritePlaces: List<FavoritePlace>,
    onUpdateFavoritePlaces: (List<FavoritePlace>) -> Unit,
    onOpenAddFavoriteDialog: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 页面初始化或切换 Tab 时，自动同步当前单点标记
    LaunchedEffect(mapController.isReady) {
        if (mapController.isReady) {
            val lat = latText.toDoubleOrNull() ?: 39.908823
            val lon = lonText.toDoubleOrNull() ?: 116.397470
            mapController.setSinglePoint(lat, lon)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==================== 1. 常用打卡地点收藏舱 ====================
        GlassCard(
            accentGlowColor = TechAmber,
            contentPadding = PaddingValues(16.dp)
        ) {
            TechSectionHeader(
                title = "常用打卡坐标库",
                icon = Icons.Default.Bookmarks,
                iconTint = TechAmberLight,
                badgeText = if (favoritePlaces.isNotEmpty()) "${favoritePlaces.size} 处" else null,
                badgeColor = TechAmberLight,
                actionSlot = {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TechAmber.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, TechAmberLight.copy(alpha = 0.42f)),
                        modifier = Modifier.clickable {
                            val lat = latText.toDoubleOrNull() ?: 39.908823
                            val lon = lonText.toDoubleOrNull() ?: 116.397470
                            onOpenAddFavoriteDialog(lat, lon)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = TechAmberLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "收藏当前点",
                                fontSize = 11.sp,
                                color = TechAmberLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )

            if (favoritePlaces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(favoritePlaces) { place ->
                        val isCurrentPlace = latText == "%.6f".format(place.latitude) &&
                            lonText == "%.6f".format(place.longitude)
                        val cardShape = RoundedCornerShape(14.dp)

                        Box(
                            modifier = Modifier
                                .clip(cardShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isCurrentPlace) {
                                            listOf(
                                                TechAmber.copy(alpha = 0.24f),
                                                DarkInputBg.copy(alpha = 0.95f)
                                            )
                                        } else {
                                            listOf(
                                                DarkCardSubtle.copy(alpha = 0.7f),
                                                DarkInputBg.copy(alpha = 0.95f)
                                            )
                                        }
                                    )
                                )
                                .border(
                                    width = if (isCurrentPlace) 1.2.dp else 0.8.dp,
                                    color = if (isCurrentPlace) TechAmberLight.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f),
                                    shape = cardShape
                                )
                                .clickable {
                                    onLatTextChange("%.6f".format(place.latitude))
                                    onLonTextChange("%.6f".format(place.longitude))
                                    onCoordSystemChange("GCJ02")
                                    mapController.setSinglePoint(place.latitude, place.longitude)
                                    mapController.flyToLocation(place.latitude, place.longitude)
                                    if (isMocking) {
                                        onStartMock(place.latitude, place.longitude, true)
                                    }
                                    Toast.makeText(context, "已定位至: ${place.name}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = TechAmber.copy(alpha = 0.20f),
                                            shape = RoundedCornerShape(5.dp),
                                            border = BorderStroke(0.5.dp, TechAmberLight.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = place.tag,
                                                fontSize = 9.5.sp,
                                                color = TechAmberLight,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            )
                                        }
                                        Text(
                                            text = place.name,
                                            fontSize = 12.5.sp,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${"%.4f".format(place.latitude)}, ${"%.4f".format(place.longitude)}",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val updated = FavoritePlacesHelper.deleteFavorite(context, place.id)
                                        onUpdateFavoritePlaces(updated)
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== 2. 目标坐标与空间校准舱 ====================
        GlassCard(
            accentGlowColor = TechCyan,
            contentPadding = PaddingValues(16.dp)
        ) {
            TechSectionHeader(
                title = "精确经纬坐标与校准",
                icon = Icons.Default.MyLocation,
                iconTint = TechCyanLight,
                actionSlot = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkInputBg,
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Coordinates", "$latText, $lonText"))
                            Toast.makeText(context, "已复制坐标: $latText, $lonText", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TechCyanLight, modifier = Modifier.size(12.dp))
                            Text("复制坐标", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        onLatTextChange(it)
                        it.toDoubleOrNull()?.let { lat ->
                            lonText.toDoubleOrNull()?.let { lon ->
                                mapController.setSinglePoint(lat, lon)
                            }
                        }
                    },
                    label = { Text("纬度 Latitude (N)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkInputBg,
                        unfocusedContainerColor = DarkInputBg,
                        focusedBorderColor = TechCyanLight,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedLabelColor = TechCyanLight,
                        unfocusedLabelColor = TextSecondary
                    )
                )

                OutlinedTextField(
                    value = lonText,
                    onValueChange = {
                        onLonTextChange(it)
                        latText.toDoubleOrNull()?.let { lat ->
                            it.toDoubleOrNull()?.let { lon ->
                                mapController.setSinglePoint(lat, lon)
                            }
                        }
                    },
                    label = { Text("经度 Longitude (E)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkInputBg,
                        unfocusedContainerColor = DarkInputBg,
                        focusedBorderColor = TechCyanLight,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedLabelColor = TechCyanLight,
                        unfocusedLabelColor = TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "坐标系基准转换",
                fontSize = 11.5.sp,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp
            )
            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val systems = listOf(
                    Pair("GCJ02", "高德/腾讯 (GCJ-02)"),
                    Pair("WGS84", "国际 GPS (WGS-84)"),
                    Pair("BD09", "百度地图 (BD-09)")
                )
                systems.forEach { (code, name) ->
                    val isSelected = coordSystemSingle == code
                    TechSelectorChip(
                        text = name,
                        isSelected = isSelected,
                        onClick = { onCoordSystemChange(code) },
                        modifier = Modifier.weight(1f),
                        activeColor = TechCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "核心城市一键跃迁",
                fontSize = 11.5.sp,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp
            )
            Spacer(modifier = Modifier.height(7.dp))

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
                    TechSelectorChip(
                        text = cityName,
                        isSelected = isCitySelected,
                        onClick = {
                            onLatTextChange("%.6f".format(coords.first))
                            onLonTextChange("%.6f".format(coords.second))
                            mapController.setSinglePoint(coords.first, coords.second)
                            mapController.flyToLocation(coords.first, coords.second)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ==================== 3. 主执行操作按钮 ====================
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

        Spacer(modifier = Modifier.height(18.dp))
    }
}
