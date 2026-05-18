package com.vipsearch.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val keyword: String,
  val timestamp: Long
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val showName: String,
  val episodeName: String,
  val url: String,
  val timestamp: Long
)

@Entity(
  tableName = "favorites",
  indices = [Index(value = ["showName"], unique = true)]
)
data class FavoriteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val showName: String,
  val pic: String,
  val type: String,
  val year: String,
  val remarks: String
)

@Entity(
  tableName = "alt_source_cache",
  indices = [Index(value = ["showName", "episodeName", "primaryUrlHash"], unique = true)]
)
data class AltSourceCacheEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val showName: String,
  val episodeName: String,
  val primaryUrlHash: String,
  val altUrlsJson: String,
  val fetchedAt: Long,
  val expiresAt: Long,
  val status: String
)
