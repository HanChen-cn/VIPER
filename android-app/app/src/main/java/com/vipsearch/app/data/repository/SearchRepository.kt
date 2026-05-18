package com.vipsearch.app.data.repository

import com.vipsearch.app.data.model.ApiSource
import com.vipsearch.app.data.model.Episode
import com.vipsearch.app.data.model.ParseApi
import com.vipsearch.app.data.model.Show
import com.vipsearch.app.data.remote.ApiSourcesProvider
import com.vipsearch.app.data.remote.CmsApiClient
import com.vipsearch.app.data.remote.ParserApiClient
import com.vipsearch.app.data.remote.WarmupManager
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
  private suspend fun fetchAllSources(keyword: String): List<List<Show>> {
    val bundle = apiSourcesProvider.load()
    val enabledSources = bundle.cmsSources.filter { it.enabled }
    if (enabledSources.isEmpty()) return emptyList()

    return coroutineScope {
      enabledSources.map { source ->
        async { fetchSourceShows(source, keyword) }
      }.awaitAll()
    }.filter { it.isNotEmpty() }
  }

  private suspend fun searchAndMerge(keyword: String): List<Show> {
    val sourceResults = fetchAllSources(keyword)
    if (sourceResults.isEmpty()) return emptyList()
    return mergeAndDedupe(sourceResults)
  }

  suspend fun search(keyword: String): List<Show> {
    if (keyword.isBlank()) return emptyList()

    val bundle = apiSourcesProvider.load()
    val merged = searchAndMerge(keyword)
    if (merged.isEmpty()) return emptyList()

    val warmupApis = WarmupManager.awaitResult()
    val mobileApis = bundle.parseApis.filter { it.mobile }
    val fallbackApis = if (mobileApis.isNotEmpty()) mobileApis else bundle.parseApis

    val rankedApis = if (warmupApis.isNotEmpty()) warmupApis.map { it.api } else fallbackApis
    val bestApi = rankedApis.firstOrNull()

    return merged.map { show ->
      show.copy(
        episodes = show.episodes.map { ep ->
          val encoded = urlEncode(ep.playUrl)
          val alt = rankedApis.take(8).map { api -> "${api.url}$encoded" }
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

    val sourceResults = fetchAllSources(showName)
    if (sourceResults.isEmpty()) return emptyList()
    val allShows = mergeOnly(sourceResults)
    val targetShow = allShows.firstOrNull { it.name.trim() == showName.trim() }
      ?: return emptyList()

    val seen = linkedSetOf<String>()
    targetShow.episodes.forEach { ep ->
      if (ep.name.trim() == episodeName.trim() && ep.playUrl != currentUrl) {
        seen += ep.playUrl
      }
    }
    return seen.toList()
  }

  suspend fun getEpisodeList(showName: String): List<Episode> {
    if (showName.isBlank()) return emptyList()

    val merged = searchAndMerge(showName)
    val show = merged.firstOrNull { it.name.trim() == showName.trim() }
      ?: return emptyList()

    val warmupApis = WarmupManager.awaitResult()
    val bundle = apiSourcesProvider.load()
    val mobileApis = bundle.parseApis.filter { it.mobile }
    val fallbackApis = if (mobileApis.isNotEmpty()) mobileApis else bundle.parseApis
    val bestApi = if (warmupApis.isNotEmpty()) warmupApis.first().api else fallbackApis.firstOrNull()

    val rankedApis = if (warmupApis.isNotEmpty()) warmupApis.map { it.api } else fallbackApis

    return deduplicateEpisodes(show.episodes).map { ep ->
      val encoded = urlEncode(ep.playUrl)
      val alt = rankedApis.take(8).map { api -> "${api.url}$encoded" }
      if (needsParsing(ep.playUrl) && bestApi != null) {
        ep.copy(playUrl = parserApiClient.buildParseUrl(bestApi, encoded), altUrls = alt)
      } else {
        ep.copy(altUrls = alt)
      }
    }
  }

  private fun deduplicateEpisodes(episodes: List<Episode>): List<Episode> =
    episodes.groupBy { it.name.trim() }
      .mapNotNull { (_, list) -> list.firstOrNull() }

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
    fun mergeOnly(allResults: List<List<Show>>): List<Show> {
      val merged = linkedMapOf<String, Show>()

      allResults.flatten().forEach { show ->
        val key = show.name.trim()
        val existing = merged[key]
        if (existing == null) {
          merged[key] = show.copy(episodes = show.episodes.toMutableList())
        } else {
          merged[key] = existing.copy(
            pic = existing.pic.ifBlank { show.pic },
            year = existing.year.ifBlank { show.year },
            type = existing.type.ifBlank { show.type },
            remarks = existing.remarks.ifBlank { show.remarks },
            episodes = existing.episodes + show.episodes
          )
        }
      }

      return merged.values.toList()
    }

    fun mergeAndDedupe(allResults: List<List<Show>>): List<Show> {
      return mergeOnly(allResults).map { show ->
        show.copy(
          episodes = show.episodes
            .groupBy { it.name.trim() }
            .mapNotNull { (_, list) -> list.firstOrNull() }
        )
      }
    }

    fun buildAltUrls(parseApis: List<ParseApi>, rawUrl: String): List<String> {
      val encoded = URLEncoder.encode(rawUrl, StandardCharsets.UTF_8.toString()).replace("+", "%20")
      return parseApis.take(8).map { "${it.url}$encoded" }
    }
  }
}
