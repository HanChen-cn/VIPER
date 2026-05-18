package com.vipsearch.app

import com.vipsearch.app.player.PlaybackRoute
import com.vipsearch.app.player.UrlClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlClassifierTest {
  private val classifier = UrlClassifier()

  @Test
  fun `m3u8 should use exoplayer route`() {
    val route = classifier.classify("https://cdn.example.com/video/index.m3u8")
    assertEquals(PlaybackRoute.EXO_PLAYER, route)
  }

  @Test
  fun `mp4 should use exoplayer route`() {
    val route = classifier.classify("https://cdn.example.com/video/ep01.mp4")
    assertEquals(PlaybackRoute.EXO_PLAYER, route)
  }

  @Test
  fun `iqiyi page should use webview route`() {
    val route = classifier.classify("https://www.iqiyi.com/v_123456.html")
    assertEquals(PlaybackRoute.WEB_VIEW, route)
  }
}
