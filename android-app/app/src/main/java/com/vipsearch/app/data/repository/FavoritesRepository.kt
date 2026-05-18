package com.vipsearch.app.data.repository

import com.vipsearch.app.data.local.FavoriteDao
import com.vipsearch.app.data.local.FavoriteEntity
import com.vipsearch.app.data.model.FavoriteItem
import com.vipsearch.app.data.model.Show

class FavoritesRepository(
  private val favoriteDao: FavoriteDao
) {
  suspend fun addFavorite(show: Show) {
    if (show.name.isBlank()) return
    favoriteDao.upsert(
      FavoriteEntity(
        showName = show.name.trim(),
        pic = show.pic,
        type = show.type,
        year = show.year,
        remarks = show.remarks
      )
    )
  }

  suspend fun removeFavorite(showName: String) {
    favoriteDao.removeByName(showName)
  }

  suspend fun getFavorites(): List<FavoriteItem> {
    return favoriteDao.getAll().map {
      FavoriteItem(
        showName = it.showName,
        pic = it.pic,
        type = it.type,
        year = it.year,
        remarks = it.remarks
      )
    }
  }
}
