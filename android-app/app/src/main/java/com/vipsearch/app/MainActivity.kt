package com.vipsearch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.vipsearch.app.ui.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
  private val appContainer by lazy { AppContainer(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    window.statusBarColor = android.graphics.Color.parseColor("#1D1D1F")
    window.navigationBarColor = android.graphics.Color.parseColor("#272729")
    WindowInsetsControllerCompat(window, window.decorView).apply {
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }

    appContainer.startWarmup(lifecycleScope)
    setContent {
      AppNavGraph(appContainer)
    }
  }
}
