package com.antigravity.geospoofer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.ui.theme.*

/**
 * 苹果风格 Inset Grouped 卡片 (iOS Inset Grouped Card)
 * 采用深色模式系统灰色 SystemGray6，配备超微发丝线边框
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    containerColor: Color = AppleCard,
    borderColor: Color = AppleBorder,
    borderWidth: Dp = 0.5.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * 苹果风格模块分组标题 (iOS Group Header)
 * 极简、克制、富有层次
 */
@Composable
fun TechSectionHeader(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color = AppleBlue,
    badgeText: String? = null,
    badgeColor: Color = TextSecondary,
    actionSlot: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = (-0.2).sp
            )
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppleCardSubtle
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (actionSlot != null) {
            actionSlot()
        }
    }
}

/**
 * 苹果灵动岛风格状态胶囊 (Dynamic Island Pill)
 * 克制、纯净，以细微状态圆点提示运行状态
 */
@Composable
fun StatusPill(
    text: String,
    isActive: Boolean,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        isPaused -> AppleOrange
        isActive -> AppleGreen
        else -> TextTertiary
    }

    Surface(
        color = AppleCardSubtle,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, AppleBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.1).sp
            )
        }
    }
}

/**
 * 苹果健身/健康风格指标磁贴 (Apple Health / Fitness Tile)
 * 极简留白、高对比大数字排版
 */
@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color = AppleBlue,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = AppleInputBg,
        border = BorderStroke(0.5.dp, AppleBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label.uppercase(),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * 苹果原生风格设置切换行 (iOS Settings Row)
 */
@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color = AppleBlue,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Surface(
                    color = iconColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    letterSpacing = (-0.1).sp
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppleGreen,
                uncheckedThumbColor = Color(0xFFE5E5EA),
                uncheckedTrackColor = Color(0xFF39393D),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/**
 * 苹果原生分段选择控制器 (iOS Segmented Control)
 */
@Composable
fun ModernSegmentedTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    icons: List<ImageVector>? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        shape = RoundedCornerShape(10.dp),
        color = AppleCardSubtle,
        border = BorderStroke(0.5.dp, AppleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                val animBg by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF636366) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "tabBg"
                )
                val animText by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextSecondary,
                    animationSpec = tween(180),
                    label = "tabText"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = animBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (icons != null && index < icons.size) {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = null,
                                tint = animText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = animText,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 苹果风格主要操作按钮 (Apple System CTA Button)
 * 启动时使用纯粹的 iOS System Blue，停止时为极简 Red Destructive
 */
@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActive) AppleRed else AppleBlue

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                )
            }
        }
    }
}

/**
 * 苹果风格功能小卡片 (Apple Action Tile)
 * 灰阶统一底色，拒绝花哨杂色
 */
@Composable
fun ToolboxTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    accentColor: Color = TextPrimary
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppleInputBg,
        border = BorderStroke(0.5.dp, AppleBorder),
        modifier = modifier.clickable(enabled = !isLoading, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AppleBlue,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.1).sp
            )
        }
    }
}

/**
 * 苹果胶囊选择器 (iOS Picker Chip)
 * 选中态为 System Blue 或高对比度浅灰，未选中态为静音深灰
 */
@Composable
fun TechSelectorChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = AppleBlue,
    activeTextColor: Color = Color.White
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) AppleBlue else AppleInputBg,
        border = if (isSelected) null else BorderStroke(0.5.dp, AppleBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else TextSecondary,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.1).sp
            )
        }
    }
}
