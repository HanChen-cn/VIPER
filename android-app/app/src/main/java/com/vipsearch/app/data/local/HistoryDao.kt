package com.vipsearch.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSearchHistory(item: SearchHistoryEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlayHistory(item: PlayHistoryEntity)

  @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
  suspend fun latestSearchHistory(limit: Int = 20): List<SearchHistoryEntity>

  @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT :limit")
  suspend fun latestPlayHistory(limit: Int = 50): List<PlayHistoryEntity>

  @Query("DELETE FROM search_history")
  suspend fun clearSearchHistory()
}
