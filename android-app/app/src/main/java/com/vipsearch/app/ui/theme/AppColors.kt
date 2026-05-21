package com.vipsearch.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VipColors(
  val canvas: Color,
  val cardSurface: Color,
  val actionBlue: Color,
  val skyBlue: Color,
  val textPrimary: Color,
  val textMuted: Color,
  val errorRed: Color,
  val successGreen: Color,
  val divider: Color,
  val pillBorder: Color,
  val cardBorder: Color,
  val inputBorder: Color,
)

val DarkVipColors = VipColors(
  canvas = Color(0xFF1D1D1F),
  cardSurface = Color(0xFF272729),
  actionBlue = Color(0xFF0066CC),
  skyBlue = Color(0xFF2997FF),
  textPrimary = Color(0xFFFFFFFF),
  textMuted = Color(0xFFCCCCCC),
  errorRed = Color(0xFFFF6B6B),
  successGreen = Color(0xFF4CAF50),
  divider = Color(0xFF3A3A3C),
  pillBorder = Color(0x59FFFFFF),
  cardBorder = Color(0x0FFFFFFF),
  inputBorder = Color(0x14FFFFFF),
)

val LightVipColors = VipColors(
  canvas = Color(0xFFFFFFFF),
  cardSurface = Color(0xFFF5F5F7),
  actionBlue = Color(0xFF0066CC),
  skyBlue = Color(0xFF0066CC),
  textPrimary = Color(0xFF1D1D1F),
  textMuted = Color(0xFF7A7A7A),
  errorRed = Color(0xFFFF3B30),
  successGreen = Color(0xFF34C759),
  divider = Color(0xFFE0E0E0),
  pillBorder = Color(0x33000000),
  cardBorder = Color(0x0F000000),
  inputBorder = Color(0x14000000),
)

val LocalVipColors = staticCompositionLocalOf { DarkVipColors }

object AppColors {
  val Canvas: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.canvas
  val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.cardSurface
  val ActionBlue: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.actionBlue
  val SkyBlue: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.skyBlue
  val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.textPrimary
  val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.textMuted
  val ErrorRed: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.errorRed
  val SuccessGreen: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.successGreen
  val Divider: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.divider
  val PillBorder: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.pillBorder
  val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.cardBorder
  val InputBorder: Color @Composable @ReadOnlyComposable get() = LocalVipColors.current.inputBorder
}
