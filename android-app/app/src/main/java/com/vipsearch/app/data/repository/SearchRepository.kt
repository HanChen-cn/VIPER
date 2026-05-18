package com.vipsearch.app.data.repository

import com.vipsearch.app.data.model.ApiSource
import com.vipsearch.app.data.model.Episode
import com.vipsearch.app.data.model.ParseApi
import com.vipsearch.app.data.model.Show
import com.vipsearch.app.data.remote.ApiSourcesProvider
import com.vipsearch.app.data.remote.CmsApiClient
import com.vipsearch.app.data.remote.ParserApiClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

class SearchRepository(
  private val apiSourcesProvider: ApiSourcesProvider,
  private val cmsApiClient: CmsApiClient,
  private val parserApiClient: ParserApiClient
) {
  suspend fun search(keyword: String): List<Show> {
    if (keyword.isBlank()) return emptyList()

    val bundle = apiSourcesProvider.load()
    val enabledSources = bundle.cmsSources.filter { it.enabled }
    if (enabledSources.isEmpty()) return emptyList()

    val sourceResults = coroutineScope {
      enabledSources.map { source ->
        async { fetchSourceShows(source, keyword) }
      }.awaitAll()
    }.filter { it.isNotEmpty() }

    if (sourceResults.isEmpty()) return emptyList()

    val mobileApis = bundle.parseApis.filter { it.mobile }
    val availableApis = if (mobileApis.isNotEmpty()) mobileApis else bundle.parseApis
    val bestApi = availableApis.firstOrNull()

    return mergeAndDedupe(sourceResults).map { show ->
      show.copy(
        episodes = show.episodes.map { ep ->
          val encoded = urlEncode(ep.playUrl)
          val alt = availableApis.take(8).map { api -> "${api.url}$encoded" }
          if (needsParsing(ep.playUrl) && bestApi != null) {
            ep.copy(playUrl = parserApiClient.buildParseUrl(bestApi, encoded), altUrls = alt)
          } else {
            ep.copy(playUrl = ep.playUrl, altUrls = alt)
          }
        }
      )
    }
  }

  suspend fun getAltSources(
    showName: String,
    episodeName: String,
    currentUrl: String
  ): List<String> {
    if (showName.isBlank() || episodeName.isBlank()) return emptyList()
    val bundle = apiSourcesProvider.load()
    val enabledSources = bundle.cmsSources.filter { it.enabled }
    if (enabledSources.isEmpty()) return emptyList()

    val sourceResults = coroutineScope {
      enabledSources.map { source ->
        async { fetchSourceShows(source, showName) }
      }.awaitAll()
    }.filter { it.isNotEmpty() }

    if (sourceResults.isEmpty()) return emptyList()

    val targetShow = mergeAndDedupe(sourceResults)
      .firstOrNull { it.name.trim() == showName.trim() } ?: return emptyList()

    val seen = linkedSetOf<String>()
    targetShow.episodes.forEach { ep ->
      if (ep.name.trim() == episodeName.trim() && ep.playUrl != currentUrl) {
        seen += ep.playUrl
      }
    }
    return seen.toList()
  }

  private suspend fun fetchSourceShows(source: ApiSource, keyword: String): List<Show> {
    val raw = cmsApiClient.searchRaw(source, keyword) ?: return emptyList()
    return parseCmsResponse(raw, source.id)
  }

  private fun parseCmsResponse(rawJson: String, sourceId: String): List<Show> {
    return runCatching {
      val root = JSONObject(rawJson)
      if (root.optInt("code", 0) != 1) return emptyList()
      val list = root.optJSONArray("list") ?: return emptyList()
      buildList {
        for (i in 0 until list.length()) {
          val item = list.getJSONObject(i)
          val episodes = parseEpisodes(
            playFrom = item.optString("vod_play_from", ""),
            playUrl = item.optString("vod_play_url", "")
          )
          add(
            Show(
              name = item.optString("vod_name", "").ifBlank { item.optString("type_name", "") },
              pic = item.optString("vod_pic", ""),
              year = item.optString("vod_year", ""),
              type = item.optString("type_name", ""),
              remarks = item.optString("vod_remarks", ""),
              episodes = episodes.map { ep ->
                ep.copy(source = "${ep.source}|$sourceId")
              }
            )
          )
        }
      }.filter { it.name.isNotBlank() && it.episodes.isNotEmpty() }
    }.getOrDefault(emptyList())
  }

  private fun parseEpisodes(playFrom: String, playUrl: String): List<Episode> {
    if (playFrom.isBlank() || playUrl.isBlank()) return emptyList()
    val sources = playFrom.split("\$\$\$")
    val urlGroups = playUrl.split("\$\$\$")
    val allEpisodes = mutableListOf<Episode>()

    for (i in sources.indices) {
      val sourceName = sources.getOrNull(i).orEmpty().ifBlank { "线路${i + 1}" }
      val group = urlGroups.getOrNull(i).orEmpty()
      group.split("#").forEach { item ->
        val dollar = item.lastIndexOf('$')
        if (dollar <= 0) return@forEach
        val epName = item.substring(0, dollar).trim()
        val epUrl = item.substring(dollar + 1).trim()
        if (epName.isNotBlank() && epUrl.startsWith("http")) {
          allEpisodes.add(Episode(name = epName, playUrl = epUrl, source = sourceName))
        }
      }
    }
    return allEpisodes
  }

  private fun needsParsing(url: String): Boolean {
    val patterns = listOf(
      Regex("iqiyi\\.com", RegexOption.IGNORE_CASE),
      Regex("youku\\.com", RegexOption.IGNORE_CASE),
      Regex("v\\.qq\\.com", RegexOption.IGNORE_CASE),
      Regex("mgtv\\.com", RegexOption.IGNORE_CASE),
      Regex("bilibili\\.com", RegexOption.IGNORE_CASE),
      Regex("le\\.com", RegexOption.IGNORE_CASE),
      Regex("sohu\\.com", RegexOption.IGNORE_CASE),
      Regex("pptv\\.com", RegexOption.IGNORE_CASE)
    )
    return patterns.any { it.containsMatchIn(url) }
  }

  private fun urlEncode(url: String): String =
    URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
      .replace("+", "%20")

  companion object {
    fun mergeAndDedupe(allResults: List<List<Show>>): List<Show> {
      val merged = linkedMapOf<String, Show>()

      allResults.flatten().forEach { show ->
        val key = show.name.trim()
        val existing = merged[key]
        if (existing == null) {
          merged[key] = show.copy(episodes = show.episodes.toMutableList())
        } else {
          val mergedEpisodes = (existing.episodes + show.episodes)
            .groupBy { it.name.trim() }
            .mapNotNull { (_, list) -> list.firstOrNull() }
          merged[key] = existing.copy(
            pic = existing.pic.ifBlank { show.pic },
            year = existing.year.ifBlank { show.year },
            type = existing.type.ifBlank { show.type },
            remarks = existing.remarks.ifBlank { show.remarks },
            episodes = mergedEpisodes
          )
        }
      }

      return merged.values.toList()
    }

    fun buildAltUrls(parseApis: List<ParseApi>, rawUrl: String): List<String> {
      val encoded = URLEncoder.encode(rawUrl, StandardCharsets.UTF_8.toString()).replace("+", "%20")
      return parseApis.take(8).map { "${it.url}$encoded" }
    }
  }
}
