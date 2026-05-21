package com.vipsearch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VipDarkColorScheme = darkColorScheme(
  primary = VipActionBlue,
  onPrimary = VipWhite,
  secondary = VipSkyBlue,
  onSecondary = VipWhite,
  background = VipCanvasBlack,
  onBackground = VipWhite,
  surface = VipSurface,
  onSurface = VipWhite,
  surfaceVariant = VipSurfaceVariant,
  onSurfaceVariant = VipTextMuted,
  error = VipErrorRed,
  onError = VipWhite,
  outline = VipDivider,
  outlineVariant = VipOutline
)

@Composable
fun VipSearchTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = VipDarkColorScheme,
    typography = VipTypography,
    shapes = VipShapes,
    content = content
  )
}
