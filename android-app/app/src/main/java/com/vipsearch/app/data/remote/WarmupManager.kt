package com.vipsearch.app.data.remote

import com.vipsearch.app.data.model.ParseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WarmupManager {
  private val mutex = Mutex()
  private var rankedApis: List<ParseApiHealth> = emptyList()
  private var warmupDeferred: Deferred<List<ParseApiHealth>>? = null
  private var done = false

  fun start(
    scope: CoroutineScope,
    parserApiClient: ParserApiClient,
    parseApis: List<ParseApi>
  ) {
    if (done || warmupDeferred != null) return
    val testUrl = "https://v.qq.com/x/page/test.html"
    warmupDeferred = scope.async(Dispatchers.IO) {
      parserApiClient.warmup(parseApis, testUrl)
    }
  }

  suspend fun awaitResult(): List<ParseApiHealth> {
    if (done) return rankedApis

    val deferred = mutex.withLock {
      if (done) return rankedApis
      warmupDeferred
    } ?: return rankedApis

    val result = runCatching { deferred.await() }.getOrDefault(emptyList())

    mutex.withLock {
      if (!done) {
        rankedApis = result
        done = true
      }
    }
    return rankedApis
  }

  fun bestApi(fallback: ParseApi?): ParseApi? =
    rankedApis.firstOrNull()?.api ?: fallback

  fun rankedApiList(): List<ParseApi> =
    if (rankedApis.isNotEmpty()) rankedApis.map { it.api }
    else emptyList()

  fun isReady(): Boolean = done

  fun statusText(): String {
    if (!done) return "⏳ 检测解析接口中..."
    if (rankedApis.isEmpty()) return "⚠ 接口检测失败，将使用默认接口"
    val fastest = rankedApis.first()
    return "✓ ${rankedApis.size} 个接口可用，最快: ${fastest.api.name} (${fastest.latencyMs}ms)"
  }
}
