package com.vipsearch.app

import android.content.Context
import com.vipsearch.app.data.local.AppDatabase
import com.vipsearch.app.data.remote.ApiSourcesProvider
import com.vipsearch.app.data.remote.CmsApiClient
import com.vipsearch.app.data.remote.NetworkConfig
import com.vipsearch.app.data.remote.ParserApiClient
import com.vipsearch.app.data.repository.AltSourceRepository
import com.vipsearch.app.data.repository.FavoritesRepository
import com.vipsearch.app.data.repository.HistoryRepository
import com.vipsearch.app.data.repository.SearchRepository
import com.vipsearch.app.domain.usecase.GetAltSourcesUseCase
import com.vipsearch.app.domain.usecase.SearchUseCase

class AppContainer(context: Context) {
  private val appContext = context.applicationContext
  private val httpClient by lazy { NetworkConfig.createClient(debug = true) }
  private val db by lazy { AppDatabase.get(appContext) }

  private val apiSourcesProvider by lazy { ApiSourcesProvider(appContext) }
  private val cmsApiClient by lazy { CmsApiClient(httpClient) }
  private val parserApiClient by lazy { ParserApiClient(httpClient) }

  val searchRepository by lazy {
    SearchRepository(
      apiSourcesProvider = apiSourcesProvider,
      cmsApiClient = cmsApiClient,
      parserApiClient = parserApiClient
    )
  }

  val altSourceRepository by lazy {
    AltSourceRepository(dao = db.altSourceCacheDao())
  }
  val historyRepository by lazy { HistoryRepository(db.historyDao()) }
  val favoritesRepository by lazy { FavoritesRepository(db.favoriteDao()) }

  val searchUseCase by lazy { SearchUseCase(searchRepository) }
  val getAltSourcesUseCase by lazy { GetAltSourcesUseCase(altSourceRepository) }
}
