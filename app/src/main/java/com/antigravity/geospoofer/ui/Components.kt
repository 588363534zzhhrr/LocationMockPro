package com.antigravity.geospoofer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.geospoofer.ui.theme.*

/**
 * 流光毛玻璃卡片 (Luminous Frosted Glass Card)
 * 具备顶光斜向高光折射边框、深邃微光渐变基底与空间阴影层次
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    containerColor: Color = DarkCard,
    borderColor: Color = GlassBorderHighlight,
    borderWidth: Dp = 1.dp,
    accentGlowColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val topBorderColor = accentGlowColor?.copy(alpha = 0.55f)
        ?: if (borderColor == AppleBorder || borderColor == DarkCardBorder) GlassBorderHighlight else borderColor
    val bottomBorderColor = accentGlowColor?.copy(alpha = 0.12f) ?: GlassBorderSubtle

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            topBorderColor,
            Color.White.copy(alpha = 0.10f),
            bottomBorderColor
        ),
        start = Offset(0f, 0f),
        end = Offset(900f, 900f)
    )

    val surfaceBrush = Brush.linearGradient(
        colors = listOf(
            DarkCardElevated.copy(alpha = 0.92f),
            containerColor.copy(alpha = 0.96f),
            Color(0xFF0C1220).copy(alpha = 0.98f)
        ),
        start = Offset(0f, 0f),
        end = Offset(0f, 800f)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = accentGlowColor?.copy(alpha = 0.25f) ?: Color.Black.copy(alpha = 0.6f),
                spotColor = accentGlowColor?.copy(alpha = 0.35f) ?: Color.Black.copy(alpha = 0.7f)
            ),
        shape = shape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = surfaceBrush, shape = shape)
                .border(width = borderWidth, brush = borderBrush, shape = shape)
        ) {
            // 顶部微光流光反射线 (Top Specular Shimmer Line)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                (accentGlowColor ?: TechCyanLight).copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * 精致空间分组标题 (Luminous Section Header)
 * 配备宝石质感渐变微光图标底座与高对比度胶囊徽章
 */
@Composable
fun TechSectionHeader(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color = TechCyanLight,
    badgeText: String? = null,
    badgeColor: Color = TechCyanLight,
    actionSlot: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectiveTint = if (iconTint == TextPrimary) TechCyanLight else iconTint
    val effectiveBadgeColor = if (badgeColor == TextSecondary) TechBlueLight else badgeColor

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    effectiveTint.copy(alpha = 0.24f),
                                    effectiveTint.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(
                            width = 0.8.dp,
                            color = effectiveTint.copy(alpha = 0.38f),
                            shape = RoundedCornerShape(9.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = effectiveTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = effectiveBadgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, effectiveBadgeColor.copy(alpha = 0.36f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = effectiveBadgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
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
 * 灵动呼吸光晕状态胶囊 (Breathing Luminous Status Pill)
 */
@Composable
fun StatusPill(
    text: String,
    isActive: Boolean,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val dotColor = when {
        isPaused -> TechAmberLight
        isActive -> TechEmeraldLight
        else -> TextTertiary
    }

    val containerBg = when {
        isPaused -> TechAmber.copy(alpha = 0.14f)
        isActive -> TechEmerald.copy(alpha = 0.14f)
        else -> DarkCardSubtle.copy(alpha = 0.75f)
    }

    val borderCol = when {
        isPaused -> TechAmberLight.copy(alpha = 0.45f)
        isActive -> TechEmeraldLight.copy(alpha = 0.45f)
        else -> GlassBorderHighlight
    }

    val textCol = when {
        isPaused -> TechAmberLight
        isActive -> TechEmeraldLight
        else -> TextSecondary
    }

    Surface(
        color = containerBg,
        shape = RoundedCornerShape(50),
        border = BorderStroke(0.8.dp, borderCol),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier.size(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isActive || isPaused) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = pulseAlpha))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(6.5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = text,
                color = textCol,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * 航空级流光遥测磁贴 (Luminous Telemetry Tile)
 */
@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color = TechCyanLight,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.12f),
                        DarkInputBg.copy(alpha = 0.95f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(300f, 300f)
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = accentColor.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * 精致空间设置开关行 (Luminous Setting Toggle Row)
 */
@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color = TechCyanLight,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
                val activeTint = if (checked) iconColor else TextSecondary
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (checked) {
                                    listOf(iconColor.copy(alpha = 0.24f), iconColor.copy(alpha = 0.08f))
                                } else {
                                    listOf(DarkCardSubtle, DarkInputBg)
                                }
                            )
                        )
                        .border(
                            width = 0.8.dp,
                            color = if (checked) iconColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = activeTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
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
                checkedTrackColor = TechEmerald,
                checkedBorderColor = TechEmeraldLight.copy(alpha = 0.5f),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = DarkInputBg,
                uncheckedBorderColor = Color.White.copy(alpha = 0.14f)
            )
        )
    }
}

/**
 * 极光悬浮分段导航坞 (Aurora Floating Segmented Dock)
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
            .height(48.dp)
            .shadow(8.dp, RoundedCornerShape(15.dp), spotColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(15.dp),
        color = DarkInputBg.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                val animText by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextSecondary,
                    animationSpec = tween(220),
                    label = "tabText"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.97f,
                    animationSpec = tween(220),
                    label = "tabScale"
                )

                val itemShape = RoundedCornerShape(11.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .scale(scale)
                        .clip(itemShape)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF2563EB),
                                                Color(0xFF0891B2),
                                                Color(0xFF06B6D4)
                                            )
                                        ),
                                        shape = itemShape
                                    )
                                    .border(
                                        width = 0.8.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.42f),
                                                Color.White.copy(alpha = 0.08f)
                                            )
                                        ),
                                        shape = itemShape
                                    )
                            } else {
                                Modifier.background(Color.Transparent)
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (icons != null && index < icons.size) {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = null,
                                tint = animText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = title,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = animText,
                            letterSpacing = (-0.1).sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 流光极光主控按钮 (Luminous Aurora Primary CTA Button)
 */
@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val gradientBrush = if (isActive) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFE11D48),
                Color(0xFFF43F5E),
                Color(0xFFFB7185)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF2563EB),
                Color(0xFF0284C7),
                Color(0xFF06B6D4)
            )
        )
    }

    val glowColor = if (isActive) TechRose else TechCyan

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = glowColor.copy(alpha = 0.45f),
                spotColor = glowColor.copy(alpha = 0.55f)
            ),
        shape = shape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradientBrush, shape = shape)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = shape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

/**
 * 宝石玻璃智能工具磁贴 (Jewel Glass Toolbox Tile)
 * 每个工具拥有独立主题色微光底座与精致高光边框
 */
@Composable
fun ToolboxTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    accentColor: Color = TechCyanLight
) {
    val effectiveAccent = if (accentColor == TextPrimary) TechCyanLight else accentColor
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        effectiveAccent.copy(alpha = 0.12f),
                        DarkInputBg.copy(alpha = 0.92f)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        effectiveAccent.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(effectiveAccent.copy(alpha = 0.16f))
                    .border(
                        width = 0.6.dp,
                        color = effectiveAccent.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = effectiveAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = (-0.2).sp
            )
        }
    }
}

/**
 * 流光胶囊选择器 (Luminous Glass Selector Chip)
 * 选中态呈现晶莹渐变高光与明亮描边，未选中态为深邃内凹玻璃
 */
@Composable
fun TechSelectorChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = TechBlue,
    activeTextColor: Color = Color.White
) {
    val shape = RoundedCornerShape(10.dp)
    val bgBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                activeColor.copy(alpha = 0.35f),
                TechCyan.copy(alpha = 0.25f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                DarkInputBg.copy(alpha = 0.9f),
                DarkInputBg.copy(alpha = 0.9f)
            )
        )
    }

    val borderBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                activeColor.copy(alpha = 0.85f),
                TechCyanLight.copy(alpha = 0.65f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = bgBrush, shape = shape)
            .border(
                width = if (isSelected) 1.2.dp else 0.8.dp,
                brush = borderBrush,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeTextColor else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = (-0.1).sp
        )
    }
}
