package com.vipsearch.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    SearchHistoryEntity::class,
    PlayHistoryEntity::class,
    FavoriteEntity::class,
    AltSourceCacheEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun historyDao(): HistoryDao
  abstract fun favoriteDao(): FavoriteDao
  abstract fun altSourceCacheDao(): AltSourceCacheDao

  companion object {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "vip-search-android.db"
        ).build().also { instance = it }
      }
  }
}
