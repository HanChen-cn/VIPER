package com.vipsearch.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vipsearch.app.dlna.CastState
import com.vipsearch.app.dlna.DlnaDevice
import com.vipsearch.app.dlna.DlnaSessionManager
import com.vipsearch.app.dlna.PlaybackInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CastViewModel(private val sessionManager: DlnaSessionManager) : ViewModel() {

  val castState: StateFlow<CastState> = sessionManager.castState
  val connectedDevice: StateFlow<DlnaDevice?> = sessionManager.connectedDevice
  val devices: StateFlow<List<DlnaDevice>> = sessionManager.discovery.devices
  val playbackInfo: StateFlow<PlaybackInfo> = sessionManager.controller.playbackInfo

  fun startDiscovery() = sessionManager.startDiscovery()

  fun stopDiscovery() = sessionManager.stopDiscovery()

  fun connectAndPlay(device: DlnaDevice, mediaUrl: String, title: String) {
    viewModelScope.launch {
      sessionManager.connectAndPlay(device, mediaUrl, title, viewModelScope)
    }
  }

  fun switchMedia(mediaUrl: String, title: String) {
    viewModelScope.launch {
      sessionManager.switchMedia(mediaUrl, title)
    }
  }

  fun play() {
    viewModelScope.launch { sessionManager.controller.play() }
  }

  fun pause() {
    viewModelScope.launch { sessionManager.controller.pause() }
  }

  fun stop() {
    viewModelScope.launch { sessionManager.controller.stop() }
  }

  fun seek(positionMs: Long) {
    viewModelScope.launch { sessionManager.controller.seek(positionMs) }
  }

  fun setVolume(volume: Int) {
    viewModelScope.launch { sessionManager.controller.setVolume(volume) }
  }

  fun disconnect() = sessionManager.disconnect()

  override fun onCleared() {
    super.onCleared()
    sessionManager.disconnect()
  }
}

class CastViewModelFactory(
  private val sessionManager: DlnaSessionManager
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(CastViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return CastViewModel(sessionManager) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
