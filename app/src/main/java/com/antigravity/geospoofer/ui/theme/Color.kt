package com.antigravity.geospoofer.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Apple Human Interface Guidelines Dark Palette
// iOS / macOS / VisionOS Design System
// ==========================================

// Apple System Backgrounds
val AppleBg = Color(0xFF000000)          // Pure OLED Black
val AppleCard = Color(0xFF1C1C1E)        // iOS SystemGray6 (elevated surface)
val AppleCardSubtle = Color(0xFF2C2C2E)  // iOS SystemGray5 (secondary card)
val AppleInputBg = Color(0xFF242426)     // Inset grouped field background
val AppleDivider = Color(0xFF38383A)     // Hairline separator (0.5dp)
val AppleBorder = Color.White.copy(alpha = 0.08f) // Subtle glass edge highlight

// Apple Accent & Semantic Colors (Restrained)
val AppleBlue = Color(0xFF0A84FF)        // iOS System Blue
val AppleGreen = Color(0xFF30D158)       // iOS System Green (Active state only)
val AppleOrange = Color(0xFFFF9F0A)      // iOS System Orange (Warning / Pause)
val AppleRed = Color(0xFFFF453A)         // iOS System Red (Stop / Destructive)
val ApplePurple = Color(0xFFBF5AF2)      // iOS System Purple
val AppleGray = Color(0xFF8E8E93)        // iOS System Gray

// Apple Label Typography Colors
val TextPrimary = Color(0xFFFFFFFF)      // Apple Primary Label
val TextSecondary = Color(0xFF8E8E93)    // Apple Secondary Label
val TextTertiary = Color(0xFF636366)     // Apple Tertiary / Caption Label
val TextQuaternary = Color(0xFF48484A)   // Apple Quaternary Label

// Status Capsule Colors (Restrained Apple Dynamic Island Aesthetic)
val StatusActiveBg = Color(0xFF1C1C1E)
val StatusActiveText = Color(0xFFFFFFFF)
val StatusPauseBg = Color(0xFF1C1C1E)
val StatusPauseText = Color(0xFFFF9F0A)
val StatusIdleBg = Color(0xFF1C1C1E)
val StatusIdleText = Color(0xFF8E8E93)

// Backward-compatibility aliases for existing references
val TechBlue = AppleBlue
val TechBlueLight = AppleBlue
val TechCyan = AppleBlue
val TechCyanLight = AppleBlue
val TechEmerald = AppleGreen
val TechEmeraldLight = AppleGreen
val TechEmeraldDark = Color(0xFF1C2D22)
val TechEmeraldSurface = AppleCard
val TechAmber = AppleOrange
val TechAmberLight = AppleOrange
val TechAmberDark = Color(0xFF332314)
val TechAmberSurface = AppleCard
val TechRose = AppleRed
val TechRoseLight = AppleRed
val TechRoseDark = Color(0xFF3A1818)
val TechRoseSurface = AppleCard
val TechPurple = ApplePurple
val TechPurpleLight = ApplePurple
val TechPurpleDark = Color(0xFF2B1638)
val DarkBg = AppleBg
val DarkCard = AppleCard
val DarkCardSubtle = AppleCardSubtle
val DarkCardBorder = AppleBorder
val DarkCardBorderGlow = AppleBlue.copy(alpha = 0.25f)
val DarkInputBg = AppleInputBg
val DarkDivider = AppleDivider
val GlassBorderHighlight = Color.White.copy(alpha = 0.08f)
val GlassSurfaceHighlight = Color.White.copy(alpha = 0.03f)
