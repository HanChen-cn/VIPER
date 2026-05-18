package com.vipsearch.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AltSourceCacheDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: AltSourceCacheEntity)

  @Query(
    """
    SELECT * FROM alt_source_cache
    WHERE showName = :showName
      AND episodeName = :episodeName
      AND primaryUrlHash = :primaryUrlHash
    LIMIT 1
    """
  )
  suspend fun find(showName: String, episodeName: String, primaryUrlHash: String): AltSourceCacheEntity?

  @Query("DELETE FROM alt_source_cache WHERE expiresAt < :now")
  suspend fun clearExpired(now: Long = System.currentTimeMillis())
}
