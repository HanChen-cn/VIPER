package com.vipsearch.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "vip_theme"
private const val KEY_MODE = "theme_mode"

object ThemePreference {
  private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun get(context: Context): ThemeMode {
    val value = prefs(context).getString(KEY_MODE, ThemeMode.SYSTEM.name)
    return try { ThemeMode.valueOf(value ?: ThemeMode.SYSTEM.name) } catch (_: Exception) { ThemeMode.SYSTEM }
  }

  fun set(context: Context, mode: ThemeMode) {
    prefs(context).edit().putString(KEY_MODE, mode.name).apply()
  }
}

@Composable
fun rememberThemeMode(): androidx.compose.runtime.MutableState<ThemeMode> {
  val context = LocalContext.current
  return remember { mutableStateOf(ThemePreference.get(context)) }
}
