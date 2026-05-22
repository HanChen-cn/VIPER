package com.vipsearch.app.dlna

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CastState {
  IDLE,
  DISCOVERING,
  CONNECTING,
  CASTING
}

class DlnaSessionManager(
  val discovery: DlnaDeviceDiscovery,
  val controller: DlnaController,
  val proxyServer: DlnaProxyServer
) {
  private val _castState = MutableStateFlow(CastState.IDLE)
  val castState: StateFlow<CastState> = _castState.asStateFlow()

  private val _connectedDevice = MutableStateFlow<DlnaDevice?>(null)
  val connectedDevice: StateFlow<DlnaDevice?> = _connectedDevice.asStateFlow()

  private var castScope: CoroutineScope? = null

  fun startDiscovery() {
    _castState.value = CastState.DISCOVERING
    discovery.startDiscovery()
  }

  fun stopDiscovery() {
    discovery.stopDiscovery()
    if (_castState.value == CastState.DISCOVERING) {
      _castState.value = CastState.IDLE
    }
  }

  suspend fun connectAndPlay(
    device: DlnaDevice,
    mediaUrl: String,
    title: String,
    scope: CoroutineScope
  ): Boolean {
    _castState.value = CastState.CONNECTING
    _connectedDevice.value = device
    castScope = scope
    discovery.stopDiscovery()

    controller.connect(device)

    val urlToSend = resolveUrl(mediaUrl)
    val success = controller.setAVTransportURI(urlToSend, title) && controller.play()

    if (success) {
      _castState.value = CastState.CASTING
      controller.startPolling(scope) { onDisconnected() }
    } else {
      disconnect()
    }
    return success
  }

  suspend fun switchMedia(mediaUrl: String, title: String): Boolean {
    if (_castState.value != CastState.CASTING) return false

    controller.stop()

    val urlToSend = resolveUrl(mediaUrl)
    val success = controller.setAVTransportURI(urlToSend, title) && controller.play()

    if (!success) {
      disconnect()
    }
    return success
  }

  fun disconnect() {
    controller.disconnect()
    stopProxyServer()
    _connectedDevice.value = null
    _castState.value = CastState.IDLE
    castScope = null
  }

  private fun resolveUrl(mediaUrl: String): String {
    val isDirectMedia = mediaUrl.contains(".m3u8", ignoreCase = true)
      || mediaUrl.contains(".mp4", ignoreCase = true)

    if (!isDirectMedia) {
      return mediaUrl
    }

    val localIp = discovery.getLocalIpAddress() ?: return mediaUrl
    return if (needsProxy(mediaUrl)) {
      startProxyServer()
      proxyServer.buildProxyUrl(localIp, mediaUrl)
    } else {
      mediaUrl
    }
  }

  private fun needsProxy(url: String): Boolean = proxyServer.needsProxy(url)

  private fun startProxyServer() {
    try {
      if (!proxyServer.isAlive) proxyServer.start()
    } catch (_: Exception) {
    }
  }

  private fun stopProxyServer() {
    try {
      if (proxyServer.isAlive) proxyServer.stop()
    } catch (_: Exception) {
    }
  }

  private fun onDisconnected() {
    _connectedDevice.value = null
    _castState.value = CastState.IDLE
    stopProxyServer()
  }
}
