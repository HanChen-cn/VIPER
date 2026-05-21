package com.vipsearch.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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

private val VipLightColorScheme = lightColorScheme(
  primary = VipActionBlue,
  onPrimary = VipWhite,
  secondary = VipActionBlue,
  onSecondary = VipWhite,
  background = VipWhite,
  onBackground = VipCanvasBlack,
  surface = VipParchment,
  onSurface = VipCanvasBlack,
  surfaceVariant = VipParchment,
  onSurfaceVariant = VipInkMuted,
  error = VipErrorRed,
  onError = VipWhite,
  outline = VipHairline,
  outlineVariant = VipLightOutline
)

@Composable
fun VipSearchTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit
) {
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
  val colorScheme = if (darkTheme) VipDarkColorScheme else VipLightColorScheme
  val vipColors = if (darkTheme) DarkVipColors else LightVipColors

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val activity = view.context as? Activity ?: return@SideEffect
      activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
      activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
      WindowInsetsControllerCompat(activity.window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  CompositionLocalProvider(LocalVipColors provides vipColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = VipTypography,
      shapes = VipShapes,
      content = content
    )
  }
}
