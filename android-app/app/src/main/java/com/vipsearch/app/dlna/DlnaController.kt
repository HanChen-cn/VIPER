package com.vipsearch.app.dlna

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class PlaybackInfo(
  val position: Long = 0L,
  val duration: Long = 0L,
  val volume: Int = -1,
  val isPlaying: Boolean = false
)

class DlnaController(private val httpClient: OkHttpClient) {

  companion object {
    private const val AVT_NS = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RC_NS = "urn:schemas-upnp-org:service:RenderingControl:1"
    private const val POLL_INTERVAL_MS = 1000L
    private const val MAX_CONSECUTIVE_FAILURES = 3
    private val SOAP_CONTENT_TYPE = "text/xml; charset=utf-8".toMediaType()
  }

  private val _playbackInfo = MutableStateFlow(PlaybackInfo())
  val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  private var controlUrl: String? = null
  private var renderingControlUrl: String? = null
  private var pollJob: Job? = null
  private var consecutiveFailures = 0

  private val soapClient = httpClient.newBuilder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

  fun connect(device: DlnaDevice) {
    controlUrl = device.controlUrl
    renderingControlUrl = device.controlUrl.replace("AVTransport", "RenderingControl")
    _isConnected.value = true
    consecutiveFailures = 0
  }

  fun disconnect() {
    pollJob?.cancel()
    pollJob = null
    controlUrl = null
    renderingControlUrl = null
    _isConnected.value = false
    _playbackInfo.value = PlaybackInfo()
  }

  suspend fun setAVTransportURI(url: String, title: String = ""): Boolean {
    val metadata = buildDIDLMetadata(url, title)
    val xml = buildSoapEnvelope(
      AVT_NS,
      "SetAVTransportURI",
      mapOf(
        "InstanceID" to "0",
        "CurrentURI" to escapeXml(url),
        "CurrentURIMetaData" to escapeXml(metadata)
      )
    )
    return sendSoapAction(controlUrl, AVT_NS, "SetAVTransportURI", xml)
  }

  suspend fun play(): Boolean {
    val xml = buildSoapEnvelope(
      AVT_NS,
      "Play",
      mapOf(
        "InstanceID" to "0",
        "Speed" to "1"
      )
    )
    return sendSoapAction(controlUrl, AVT_NS, "Play", xml)
  }

  suspend fun pause(): Boolean {
    val xml = buildSoapEnvelope(
      AVT_NS,
      "Pause",
      mapOf("InstanceID" to "0")
    )
    return sendSoapAction(controlUrl, AVT_NS, "Pause", xml)
  }

  suspend fun stop(): Boolean {
    pollJob?.cancel()
    pollJob = null
    val xml = buildSoapEnvelope(
      AVT_NS,
      "Stop",
      mapOf("InstanceID" to "0")
    )
    return sendSoapAction(controlUrl, AVT_NS, "Stop", xml)
  }

  suspend fun seek(positionMs: Long): Boolean {
    val timeStr = formatTime(positionMs)
    val xml = buildSoapEnvelope(
      AVT_NS,
      "Seek",
      mapOf(
        "InstanceID" to "0",
        "Unit" to "REL_TIME",
        "Target" to timeStr
      )
    )
    return sendSoapAction(controlUrl, AVT_NS, "Seek", xml)
  }

  suspend fun setVolume(volume: Int): Boolean {
    val clamped = volume.coerceIn(0, 100)
    val xml = buildSoapEnvelope(
      RC_NS,
      "SetVolume",
      mapOf(
        "InstanceID" to "0",
        "Channel" to "Master",
        "DesiredVolume" to clamped.toString()
      )
    )
    val rcUrl = renderingControlUrl ?: return false
    val ok = sendSoapAction(rcUrl, RC_NS, "SetVolume", xml)
    if (ok) {
      _playbackInfo.value = _playbackInfo.value.copy(volume = clamped)
    }
    return ok
  }

  suspend fun getVolume(): Int {
    val xml = buildSoapEnvelope(
      RC_NS,
      "GetVolume",
      mapOf(
        "InstanceID" to "0",
        "Channel" to "Master"
      )
    )
    val rcUrl = renderingControlUrl ?: return -1
    return withContext(Dispatchers.IO) {
      try {
        val body = xml.toRequestBody(SOAP_CONTENT_TYPE)
        val request = Request.Builder()
          .url(rcUrl)
          .post(body)
          .addHeader("SOAPAction", "\"$RC_NS#GetVolume\"")
          .build()
        val response = soapClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: return@withContext -1
        if (!response.isSuccessful) return@withContext -1

        val doc = DocumentBuilderFactory.newInstance()
          .newDocumentBuilder()
          .parse(responseBody.byteInputStream())
        doc.getElementsByTagName("CurrentVolume").item(0)?.textContent?.toIntOrNull() ?: -1
      } catch (_: Exception) {
        -1
      }
    }
  }

  fun startPolling(scope: CoroutineScope, onDisconnected: () -> Unit) {
    pollJob?.cancel()
    pollJob = scope.launch(Dispatchers.IO) {
      while (isActive) {
        val success = pollPositionInfo()
        if (success) {
          consecutiveFailures = 0
          val vol = getVolume()
          if (vol >= 0) {
            _playbackInfo.value = _playbackInfo.value.copy(volume = vol)
          }
        } else {
          consecutiveFailures++
          if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            _isConnected.value = false
            withContext(Dispatchers.Main) { onDisconnected() }
            break
          }
        }
        delay(POLL_INTERVAL_MS)
      }
    }
  }

  private suspend fun pollPositionInfo(): Boolean {
    val xml = buildSoapEnvelope(
      AVT_NS,
      "GetPositionInfo",
      mapOf("InstanceID" to "0")
    )
    return withContext(Dispatchers.IO) {
      try {
        val url = controlUrl ?: return@withContext false
        val body = xml.toRequestBody(SOAP_CONTENT_TYPE)
        val request = Request.Builder()
          .url(url)
          .post(body)
          .addHeader("SOAPAction", "\"$AVT_NS#GetPositionInfo\"")
          .build()
        val response = soapClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: return@withContext false
        if (!response.isSuccessful) return@withContext false

        val doc = DocumentBuilderFactory.newInstance()
          .newDocumentBuilder()
          .parse(responseBody.byteInputStream())

        val relTime = doc.getElementsByTagName("RelTime").item(0)?.textContent
        val trackDuration = doc.getElementsByTagName("TrackDuration").item(0)?.textContent

        val pos = parseTime(relTime)
        val dur = parseTime(trackDuration)
        val currentInfo = _playbackInfo.value
        _playbackInfo.value = currentInfo.copy(
          position = pos,
          duration = dur,
          isPlaying = pos > 0 || dur > 0
        )
        true
      } catch (_: Exception) {
        false
      }
    }
  }

  private suspend fun sendSoapAction(
    url: String?,
    namespace: String,
    action: String,
    soapXml: String
  ): Boolean {
    url ?: return false
    return withContext(Dispatchers.IO) {
      try {
        val body = soapXml.toRequestBody(SOAP_CONTENT_TYPE)
        val request = Request.Builder()
          .url(url)
          .post(body)
          .addHeader("SOAPAction", "\"$namespace#$action\"")
          .build()
        soapClient.newCall(request).execute().isSuccessful
      } catch (_: Exception) {
        false
      }
    }
  }

  private fun buildSoapEnvelope(
    namespace: String,
    action: String,
    params: Map<String, String>
  ): String {
    val paramXml = params.entries.joinToString("") { (k, v) -> "<$k>$v</$k>" }
    return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$action xmlns:u="$namespace">$paramXml</u:$action>
  </s:Body>
</s:Envelope>"""
  }

  private fun buildDIDLMetadata(url: String, title: String): String {
    val mime = when {
      url.contains(".m3u8", ignoreCase = true) -> "video/x-mpegurl"
      url.contains(".mp4", ignoreCase = true) -> "video/mp4"
      else -> "video/*"
    }
    val safeTitle = if (title.isBlank()) "Video" else escapeXml(title)
    return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
<item id="0" parentID="-1" restricted="1">
<dc:title>$safeTitle</dc:title>
<upnp:class>object.item.videoItem</upnp:class>
<res protocolInfo="http-get:*:$mime:*">${escapeXml(url)}</res>
</item>
</DIDL-Lite>"""
  }

  private fun escapeXml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

  fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, sec)
  }

  fun parseTime(timeStr: String?): Long {
    if (timeStr.isNullOrBlank() || timeStr == "NOT_IMPLEMENTED") return 0L
    return try {
      val parts = timeStr.split(":")
      if (parts.size != 3) return 0L
      val h = parts[0].toLongOrNull() ?: 0L
      val m = parts[1].toLongOrNull() ?: 0L
      val s = parts[2].split(".")[0].toLongOrNull() ?: 0L
      (h * 3600 + m * 60 + s) * 1000
    } catch (_: Exception) {
      0L
    }
  }
}
