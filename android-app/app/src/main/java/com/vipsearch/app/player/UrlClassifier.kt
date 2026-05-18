package com.vipsearch.app.player

enum class PlaybackRoute {
  EXO_PLAYER,
  WEB_VIEW
}

class UrlClassifier {
  private val directMediaPatterns = listOf(
    Regex("\\.m3u8(\\?.*)?$", RegexOption.IGNORE_CASE),
    Regex("\\.mp4(\\?.*)?$", RegexOption.IGNORE_CASE)
  )

  private val parseNeededPatterns = listOf(
    Regex("iqiyi\\.com", RegexOption.IGNORE_CASE),
    Regex("youku\\.com", RegexOption.IGNORE_CASE),
    Regex("v\\.qq\\.com", RegexOption.IGNORE_CASE),
    Regex("mgtv\\.com", RegexOption.IGNORE_CASE),
    Regex("bilibili\\.com", RegexOption.IGNORE_CASE),
    Regex("le\\.com", RegexOption.IGNORE_CASE),
    Regex("sohu\\.com", RegexOption.IGNORE_CASE),
    Regex("pptv\\.com", RegexOption.IGNORE_CASE)
  )

  fun classify(url: String): PlaybackRoute {
    if (url.isBlank()) return PlaybackRoute.WEB_VIEW
    if (directMediaPatterns.any { it.containsMatchIn(url) }) {
      return PlaybackRoute.EXO_PLAYER
    }
    if (parseNeededPatterns.any { it.containsMatchIn(url) }) {
      return PlaybackRoute.WEB_VIEW
    }
    return PlaybackRoute.EXO_PLAYER
  }
}
