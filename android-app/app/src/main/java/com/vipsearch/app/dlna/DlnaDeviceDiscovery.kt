package com.vipsearch.app.dlna

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Document
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class DlnaDeviceDiscovery(
  private val context: Context,
  private val httpClient: OkHttpClient
) {
  companion object {
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val SEARCH_TARGET = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val SEARCH_INTERVAL_MS = 15_000L
    private const val DEVICE_TIMEOUT_MS = 60_000L
    private const val PACKET_REPEAT = 3
  }

  private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
  val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

  private var discoveryScope: CoroutineScope? = null
  private var multicastLock: WifiManager.MulticastLock? = null

  private val deviceMap = mutableMapOf<String, DlnaDevice>()
  private val discoveryClient = httpClient.newBuilder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

  private val mSearchMessage = buildString {
    append("M-SEARCH * HTTP/1.1\r\n")
    append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
    append("MAN: \"ssdp:discover\"\r\n")
    append("MX: 3\r\n")
    append("ST: $SEARCH_TARGET\r\n")
    append("\r\n")
  }

  fun startDiscovery() {
    if (discoveryScope != null) return
    acquireMulticastLock()

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    discoveryScope = scope

    scope.launch { listenForResponses() }
    scope.launch { periodicSearch() }
  }

  fun stopDiscovery() {
    discoveryScope?.cancel()
    discoveryScope = null
    releaseMulticastLock()
  }

  private fun acquireMulticastLock() {
    val wifiManager = context.applicationContext
      .getSystemService(Context.WIFI_SERVICE) as WifiManager
    multicastLock = wifiManager.createMulticastLock("dlna_discovery").apply {
      setReferenceCounted(true)
      acquire()
    }
  }

  private fun releaseMulticastLock() {
    multicastLock?.let { if (it.isHeld) it.release() }
    multicastLock = null
  }

  private suspend fun periodicSearch() {
    while (currentCoroutineContext().isActive) {
      sendMSearch()
      pruneStaleDevices()
      delay(SEARCH_INTERVAL_MS)
    }
  }

  private fun sendMSearch() {
    try {
      val socket = DatagramSocket(null).apply {
        reuseAddress = true
        bind(InetSocketAddress(0))
      }
      socket.use { s ->
        val data = mSearchMessage.toByteArray()
        val addr = InetAddress.getByName(SSDP_ADDRESS)
        repeat(PACKET_REPEAT) {
          s.send(DatagramPacket(data, data.size, addr, SSDP_PORT))
        }
      }
    } catch (_: Exception) {
    }
  }

  private suspend fun listenForResponses() {
    try {
      val socket = MulticastSocket(SSDP_PORT).apply {
        reuseAddress = true
        val group = InetAddress.getByName(SSDP_ADDRESS)
        val iface = NetworkInterface.getByInetAddress(getLocalAddress())
        if (iface != null) {
          joinGroup(InetSocketAddress(group, SSDP_PORT), iface)
        } else {
          joinGroup(group)
        }
        soTimeout = 0
      }
      socket.use { s ->
        val buf = ByteArray(4096)
        while (currentCoroutineContext().isActive) {
          val pkt = DatagramPacket(buf, buf.size)
          withContext(Dispatchers.IO) { s.receive(pkt) }
          val response = String(pkt.data, 0, pkt.length)
          processResponse(response)
        }
      }
    } catch (_: Exception) {
    }
  }

  private suspend fun processResponse(response: String) {
    val location = extractHeader(response, "LOCATION") ?: return
    val usn = extractHeader(response, "USN") ?: location

    if (deviceMap.containsKey(usn)) {
      deviceMap[usn] = deviceMap[usn]!!.copy(lastSeen = System.currentTimeMillis())
      publishDevices()
      return
    }

    val device = fetchDeviceDescription(location, usn) ?: return
    deviceMap[usn] = device
    publishDevices()
  }

  private suspend fun fetchDeviceDescription(location: String, usn: String): DlnaDevice? {
    return withContext(Dispatchers.IO) {
      try {
        val request = Request.Builder().url(location).build()
        val body = discoveryClient.newCall(request).execute().body?.string()
          ?: return@withContext null

        val doc = DocumentBuilderFactory.newInstance()
          .newDocumentBuilder()
          .parse(body.byteInputStream())
        doc.documentElement.normalize()

        val friendlyName = doc.getElementsByTagName("friendlyName")
          .item(0)?.textContent ?: "Unknown Device"

        val slashIndex = location.indexOf("/", 8)
        val baseUrl = if (slashIndex > 0) location.substring(0, slashIndex) else location

        val controlUrl = findAvTransportControlUrl(doc, baseUrl) ?: return@withContext null

        DlnaDevice(
          usn = usn,
          friendlyName = friendlyName,
          controlUrl = controlUrl,
          location = location
        )
      } catch (_: Exception) {
        null
      }
    }
  }

  private fun findAvTransportControlUrl(doc: Document, baseUrl: String): String? {
    val services = doc.getElementsByTagName("service")
    for (i in 0 until services.length) {
      val service = services.item(i)
      val children = service.childNodes
      var serviceType: String? = null
      var controlUrl: String? = null
      for (j in 0 until children.length) {
        val child = children.item(j)
        when (child.nodeName) {
          "serviceType" -> serviceType = child.textContent
          "controlURL" -> controlUrl = child.textContent
        }
      }
      if (serviceType == SEARCH_TARGET && controlUrl != null) {
        return if (controlUrl.startsWith("http")) {
          controlUrl
        } else {
          "$baseUrl${if (controlUrl.startsWith("/")) "" else "/"}$controlUrl"
        }
      }
    }
    return null
  }

  private fun extractHeader(response: String, header: String): String? {
    val regex = Regex("(?i)$header:\\s*(.+)")
    return regex.find(response)?.groupValues?.get(1)?.trim()
  }

  private fun pruneStaleDevices() {
    val now = System.currentTimeMillis()
    val stale = deviceMap.filter { now - it.value.lastSeen > DEVICE_TIMEOUT_MS }
    if (stale.isNotEmpty()) {
      stale.keys.forEach { deviceMap.remove(it) }
      publishDevices()
    }
  }

  private fun publishDevices() {
    _devices.value = deviceMap.values.toList().sortedBy { it.friendlyName }
  }

  private fun getLocalAddress(): InetAddress? {
    return try {
      val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
      val ip = wifiManager.connectionInfo.ipAddress
      if (ip == 0) return null
      InetAddress.getByAddress(
        byteArrayOf(
          (ip and 0xFF).toByte(),
          (ip shr 8 and 0xFF).toByte(),
          (ip shr 16 and 0xFF).toByte(),
          (ip shr 24 and 0xFF).toByte()
        )
      )
    } catch (_: Exception) {
      null
    }
  }

  fun getLocalIpAddress(): String? = getLocalAddress()?.hostAddress
}
