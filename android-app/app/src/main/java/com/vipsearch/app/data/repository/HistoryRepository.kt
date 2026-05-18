package com.vipsearch.app.data.repository

import com.vipsearch.app.data.local.HistoryDao
import com.vipsearch.app.data.local.PlayHistoryEntity
import com.vipsearch.app.data.local.SearchHistoryEntity
import com.vipsearch.app.data.model.PlayHistoryItem
import com.vipsearch.app.data.model.SearchHistoryItem

class HistoryRepository(
  private val historyDao: HistoryDao
) {
  suspend fun addSearchHistory(keyword: String) {
    if (keyword.isBlank()) return
    historyDao.insertSearchHistory(
      SearchHistoryEntity(
        keyword = keyword.trim(),
        timestamp = System.currentTimeMillis()
      )
    )
  }

  suspend fun addPlayHistory(showName: String, episodeName: String, url: String) {
    if (url.isBlank()) return
    historyDao.insertPlayHistory(
      PlayHistoryEntity(
        showName = showName.trim(),
        episodeName = episodeName.trim(),
        url = url,
        timestamp = System.currentTimeMillis()
      )
    )
  }

  suspend fun getSearchHistory(limit: Int = 20): List<SearchHistoryItem> {
    return historyDao.latestSearchHistory(limit).map {
      SearchHistoryItem(keyword = it.keyword, timestamp = it.timestamp)
    }
  }

  suspend fun getPlayHistory(limit: Int = 50): List<PlayHistoryItem> {
    return historyDao.latestPlayHistory(limit).map {
      PlayHistoryItem(
        showName = it.showName,
        episodeName = it.episodeName,
        url = it.url,
        timestamp = it.timestamp
      )
    }
  }

  suspend fun clearSearchHistory() {
    historyDao.clearSearchHistory()
  }
}
