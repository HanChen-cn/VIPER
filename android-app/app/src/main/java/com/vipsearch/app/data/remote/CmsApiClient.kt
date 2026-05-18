package com.vipsearch.app.data.remote

import com.vipsearch.app.data.model.ApiSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class CmsApiClient(
  private val httpClient: OkHttpClient = NetworkConfig.createClient()
) {
  suspend fun searchRaw(source: ApiSource, keyword: String): String? = withContext(Dispatchers.IO) {
    val base = "${source.baseUrl}${source.searchPath}".toHttpUrlOrNull() ?: return@withContext null
    val builder = base.newBuilder()
    source.params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
    builder.addQueryParameter(source.searchParam, keyword)
    val request = Request.Builder().url(builder.build()).get().build()

    runCatching {
      httpClient.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        resp.body?.string()
      }
    }.getOrNull()
  }
}
