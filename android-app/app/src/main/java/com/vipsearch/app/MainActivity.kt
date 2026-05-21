package com.vipsearch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.vipsearch.app.ui.navigation.AppNavGraph
import com.vipsearch.app.ui.theme.VipSearchTheme
import com.vipsearch.app.ui.theme.rememberThemeMode

class MainActivity : ComponentActivity() {
  private val appContainer by lazy { AppContainer(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    super.onCreate(savedInstanceState)

    appContainer.startWarmup(lifecycleScope)
    setContent {
      val themeMode = rememberThemeMode()
      VipSearchTheme(themeMode = themeMode.value) {
        AppNavGraph(appContainer, themeMode)
      }
    }
  }
}
