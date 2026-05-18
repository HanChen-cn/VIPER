package com.vipsearch.app.data.repository

import com.vipsearch.app.data.local.AltSourceCacheDao
import com.vipsearch.app.data.local.AltSourceCacheEntity
import java.security.MessageDigest
import org.json.JSONArray

class AltSourceRepository(
  private val dao: AltSourceCacheDao,
  private val ttlMs: Long = 24 * 60 * 60 * 1000L
) {
  suspend fun getOrFetch(
    showName: String,
    episodeName: String,
    primaryUrl: String,
    fetcher: suspend () -> List<String>
  ): List<String> {
    dao.clearExpired()
    val hash = sha1(primaryUrl)
    val now = System.currentTimeMillis()
    val cached = dao.find(showName, episodeName, hash)
    if (cached != null && cached.expiresAt > now && cached.status == "ready") {
      return fromJsonArray(cached.altUrlsJson)
    }

    dao.upsert(
      AltSourceCacheEntity(
        showName = showName,
        episodeName = episodeName,
        primaryUrlHash = hash,
        altUrlsJson = "[]",
        fetchedAt = now,
        expiresAt = now + ttlMs,
        status = "fetching"
      )
    )

    val alt = runCatching { fetcher() }.getOrDefault(emptyList())
    val nextStatus = if (alt.isEmpty()) "failed" else "ready"
    dao.upsert(
      AltSourceCacheEntity(
        showName = showName,
        episodeName = episodeName,
        primaryUrlHash = hash,
        altUrlsJson = toJsonArray(alt),
        fetchedAt = now,
        expiresAt = now + ttlMs,
        status = nextStatus
      )
    )
    return alt
  }

  private fun toJsonArray(items: List<String>): String = JSONArray(items).toString()

  private fun fromJsonArray(json: String): List<String> {
    return runCatching {
      val arr = JSONArray(json)
      buildList {
        for (i in 0 until arr.length()) {
          add(arr.getString(i))
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun sha1(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }
}
