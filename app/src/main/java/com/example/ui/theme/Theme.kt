package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = EmberPrimaryDark,
  onPrimary = Color(0xFF4A1203),
  primaryContainer = EmberContainerDark,
  onPrimaryContainer = EmberContainerLight,
  secondary = SageSecondaryDark,
  onSecondary = Color(0xFF063824),
  secondaryContainer = SageContainerDark,
  onSecondaryContainer = SageContainerLight,
  tertiary = SaffronTertiaryDark,
  onTertiary = SaffronOnContainerLight,
  tertiaryContainer = Color(0xFF5A3B00),
  onTertiaryContainer = SaffronContainerLight,
  background = ObsidianBackground,
  onBackground = ObsidianInk,
  surface = ObsidianBackground,
  onSurface = ObsidianInk,
  surfaceVariant = ObsidianSurface,
  onSurfaceVariant = ObsidianMuted,
  surfaceContainerLow = ObsidianSurfaceLow,
  surfaceContainer = ObsidianSurface,
  surfaceContainerHigh = ObsidianSurfaceHigh,
  outline = ObsidianOutline,
  outlineVariant = ObsidianSurfaceHigh,
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
  primary = EmberPrimary,
  onPrimary = Color.White,
  primaryContainer = EmberContainerLight,
  onPrimaryContainer = EmberOnContainerLight,
  secondary = SageSecondary,
  onSecondary = Color.White,
  secondaryContainer = SageContainerLight,
  onSecondaryContainer = SageOnContainerLight,
  tertiary = SaffronTertiary,
  onTertiary = Color.White,
  tertiaryContainer = SaffronContainerLight,
  onTertiaryContainer = SaffronOnContainerLight,
  background = CreamBackground,
  onBackground = EspressoInk,
  surface = CreamBackground,
  onSurface = EspressoInk,
  surfaceVariant = WarmSurface,
  onSurfaceVariant = WarmStone,
  surfaceContainerLow = WarmSurfaceLow,
  surfaceContainer = WarmSurface,
  surfaceContainerHigh = WarmSurfaceHigh,
  outline = WarmOutline,
  outlineVariant = WarmOutlineVariant,
  error = AlertRed,
  onError = Color.White,
  errorContainer = AlertRedContainer,
)

val BiteRouteShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BiteRouteTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = BiteRouteTypography,
    shapes = BiteRouteShapes,
    content = content,
  )
}
