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

  private val parserPagePatterns = listOf(
    Regex("[?&](url|v)=", RegexOption.IGNORE_CASE),
    Regex("jiexi|jx", RegexOption.IGNORE_CASE),
    Regex("%3A%2F%2F", RegexOption.IGNORE_CASE)
  )

  fun classify(url: String): PlaybackRoute {
    val normalized = url.trim()
    if (normalized.isBlank()) return PlaybackRoute.WEB_VIEW
    if (directMediaPatterns.any { it.containsMatchIn(normalized) }) {
      return PlaybackRoute.EXO_PLAYER
    }
    if (parseNeededPatterns.any { it.containsMatchIn(normalized) }) {
      return PlaybackRoute.WEB_VIEW
    }
    if (parserPagePatterns.any { it.containsMatchIn(normalized) }) {
      return PlaybackRoute.WEB_VIEW
    }
    return PlaybackRoute.EXO_PLAYER
  }
}
