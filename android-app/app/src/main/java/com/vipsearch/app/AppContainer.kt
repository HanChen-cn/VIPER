package com.vipsearch.app

import android.content.Context
import com.vipsearch.app.data.local.AppDatabase
import com.vipsearch.app.data.remote.ApiSourcesProvider
import com.vipsearch.app.data.remote.CmsApiClient
import com.vipsearch.app.data.remote.NetworkConfig
import com.vipsearch.app.data.remote.ParserApiClient
import com.vipsearch.app.data.remote.WarmupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vipsearch.app.data.repository.AltSourceRepository
import com.vipsearch.app.data.repository.FavoritesRepository
import com.vipsearch.app.data.repository.HistoryRepository
import com.vipsearch.app.data.repository.SearchRepository
import com.vipsearch.app.dlna.DlnaController
import com.vipsearch.app.dlna.DlnaDeviceDiscovery
import com.vipsearch.app.dlna.DlnaProxyServer
import com.vipsearch.app.dlna.DlnaSessionManager
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

  val dlnaSessionManager by lazy {
    val discovery = DlnaDeviceDiscovery(appContext, httpClient)
    val controller = DlnaController(httpClient)
    val proxyServer = DlnaProxyServer(httpClient)
    DlnaSessionManager(discovery, controller, proxyServer)
  }

  fun startWarmup(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
      val bundle = apiSourcesProvider.load()
      val mobileApis = bundle.parseApis.filter { it.mobile }
      val apis = if (mobileApis.isNotEmpty()) mobileApis else bundle.parseApis
      WarmupManager.start(scope, parserApiClient, apis)
    }
  }
}
