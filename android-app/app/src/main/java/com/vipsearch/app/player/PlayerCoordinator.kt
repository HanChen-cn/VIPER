package com.vipsearch.app.player

sealed interface PlaybackTarget {
  data class Exo(val mediaUrl: String) : PlaybackTarget
  data class Web(val pageUrl: String) : PlaybackTarget
}

class PlayerCoordinator(
  private val classifier: UrlClassifier = UrlClassifier()
) {
  fun resolve(url: String): PlaybackTarget {
    return when (classifier.classify(url)) {
      PlaybackRoute.EXO_PLAYER -> PlaybackTarget.Exo(url)
      PlaybackRoute.WEB_VIEW -> PlaybackTarget.Web(url)
    }
  }
}
