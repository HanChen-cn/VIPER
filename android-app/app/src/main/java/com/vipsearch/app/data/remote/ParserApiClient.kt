package com.vipsearch.app.data.remote

import com.vipsearch.app.data.model.ParseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ParseApiHealth(
  val api: ParseApi,
  val latencyMs: Long,
  val available: Boolean
)

class ParserApiClient(
  private val httpClient: OkHttpClient = NetworkConfig.createClient()
) {
  fun buildParseUrl(api: ParseApi, rawUrl: String): String = "${api.url}$rawUrl"

  suspend fun warmup(parseApis: List<ParseApi>, testUrl: String): List<ParseApiHealth> = coroutineScope {
    parseApis.map { api ->
      async {
        val start = System.currentTimeMillis()
        val ok = probe(api, testUrl)
        ParseApiHealth(
          api = api,
          latencyMs = if (ok) System.currentTimeMillis() - start else Long.MAX_VALUE,
          available = ok
        )
      }
    }.awaitAll()
      .filter { it.available }
      .sortedBy { it.latencyMs }
  }

  private suspend fun probe(api: ParseApi, testUrl: String): Boolean = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(buildParseUrl(api, testUrl)).head().build()
    runCatching {
      httpClient.newCall(req).execute().use { resp ->
        resp.isSuccessful || resp.code in 300..499
      }
    }.getOrDefault(false)
  }
}
