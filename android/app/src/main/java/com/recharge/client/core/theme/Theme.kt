package com.recharge.client.core.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Primary = Color(0xFFF59E0B)
    val PrimaryDark = Color(0xFFD97706)
    val Accent = Color(0xFFFFB703)
    val Background = Color(0xFFFFFBF3)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceWarm = Color(0xFFFFF3D9)
    val TextPrimary = Color(0xFF171717)
    val TextSecondary = Color(0xFF737373)
    val Success = Color(0xFF16A34A)
    val Error = Color(0xFFDC2626)
}

private val LightScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.SurfaceWarm,
    onPrimaryContainer = AppColors.PrimaryDark,
    secondary = AppColors.Accent,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
    error = AppColors.Error
)

@Composable
fun RechargeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography(),
        content = content
    )
}
