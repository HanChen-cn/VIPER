package com.vipsearch.app.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class PlaybackSession(
  val showName: String = "",
  val episodeName: String = "",
  val primaryUrl: String = "",
  val altUrls: List<String> = emptyList()
)

object PlaybackSessionStore {
  var session by mutableStateOf(PlaybackSession())
    private set

  fun update(
    showName: String,
    episodeName: String,
    primaryUrl: String,
    altUrls: List<String>
  ) {
    session = PlaybackSession(
      showName = showName,
      episodeName = episodeName,
      primaryUrl = primaryUrl,
      altUrls = altUrls
    )
  }
}
