package com.vipsearch.app.data.model

data class Show(
  val name: String,
  val type: String = "",
  val year: String = "",
  val remarks: String = "",
  val pic: String = "",
  val episodes: List<Episode> = emptyList()
)

data class Episode(
  val name: String,
  val playUrl: String,
  val source: String,
  val altUrls: List<String> = emptyList()
)

data class SearchHistoryItem(
  val keyword: String,
  val timestamp: Long
)

data class PlayHistoryItem(
  val showName: String,
  val episodeName: String,
  val url: String,
  val timestamp: Long
)

data class FavoriteItem(
  val showName: String,
  val pic: String = "",
  val type: String = "",
  val year: String = "",
  val remarks: String = ""
)
