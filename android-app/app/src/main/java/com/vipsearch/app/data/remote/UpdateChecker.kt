package com.vipsearch.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AppUpdate(
  val versionName: String,
  val downloadUrl: String,
  val releaseNotes: String,
  val htmlUrl: String
)

class UpdateChecker(
  private val httpClient: OkHttpClient = NetworkConfig.createClient()
) {
  suspend fun check(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url(RELEASES_API)
        .header("Accept", "application/vnd.github.v3+json")
        .build()
      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext null
        val json = JSONObject(response.body?.string() ?: return@withContext null)
        val tagName = json.optString("tag_name", "")
        val latestVersion = tagName.removePrefix("android-v")
        if (latestVersion.isBlank() || !isNewer(latestVersion, currentVersion)) {
          return@withContext null
        }
        val assets = json.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0))
          .map { assets!!.getJSONObject(it) }
          .firstOrNull { it.optString("name", "").endsWith(".apk") }
          ?.optString("browser_download_url", "")
          .orEmpty()
        if (apkUrl.isBlank()) return@withContext null

        AppUpdate(
          versionName = latestVersion,
          downloadUrl = apkUrl,
          releaseNotes = json.optString("body", ""),
          htmlUrl = json.optString("html_url", "")
        )
      }
    }.getOrNull()
  }

  private fun isNewer(remote: String, local: String): Boolean {
    val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val l = local.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(r.size, l.size)) {
      val rv = r.getOrElse(i) { 0 }
      val lv = l.getOrElse(i) { 0 }
      if (rv > lv) return true
      if (rv < lv) return false
    }
    return false
  }

  companion object {
    private const val RELEASES_API =
      "https://api.github.com/repos/HanChen-cn/VIPER/releases/latest"
  }
}
