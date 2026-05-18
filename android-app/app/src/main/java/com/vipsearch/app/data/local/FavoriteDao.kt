package com.vipsearch.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: FavoriteEntity)

  @Query("SELECT * FROM favorites ORDER BY id DESC")
  suspend fun getAll(): List<FavoriteEntity>

  @Query("DELETE FROM favorites WHERE showName = :showName")
  suspend fun removeByName(showName: String)
}
