package com.vipsearch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vipsearch.app.ui.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
  private val appContainer by lazy { AppContainer(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppNavGraph(appContainer)
    }
  }
}
