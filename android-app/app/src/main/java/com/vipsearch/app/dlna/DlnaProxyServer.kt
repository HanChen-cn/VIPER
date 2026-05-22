package com.vipsearch.app.dlna

import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DlnaProxyServer(
  private val httpClient: OkHttpClient,
  port: Int = 18899
) : NanoHTTPD(port) {

  private val serverPort = port

  private val proxyClient = httpClient.newBuilder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private var currentHeaders: Map<String, String> = emptyMap()

  fun setProxyHeaders(headers: Map<String, String>) {
    currentHeaders = headers
  }

  fun buildProxyUrl(localIp: String, videoUrl: String): String {
    val encoded = URLEncoder.encode(videoUrl, "UTF-8")
    return "http://$localIp:$serverPort/cast?url=$encoded"
  }

  fun needsProxy(url: String): Boolean {
    return try {
      val requestBuilder = Request.Builder().url(url).head()
      currentHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
      val response = proxyClient.newCall(requestBuilder.build()).execute()
      response.code == 401 || response.code == 403
    } catch (_: Exception) {
      true
    }
  }

  override fun serve(session: IHTTPSession): Response {
    if (session.uri != "/cast") {
      return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }

    val videoUrl = session.parms["url"]
    if (videoUrl.isNullOrBlank()) {
      return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing url param")
    }

    return try {
      val requestBuilder = Request.Builder().url(videoUrl)
      currentHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
      if (!currentHeaders.containsKey("User-Agent")) {
        requestBuilder.addHeader(
          "User-Agent",
          "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        )
      }

      val upstreamResponse = proxyClient.newCall(requestBuilder.build()).execute()
      val body = upstreamResponse.body
        ?: return newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR,
          MIME_PLAINTEXT,
          "Upstream returned empty body"
        )

      val contentType = upstreamResponse.header("Content-Type") ?: "video/mp4"
      val contentLength = upstreamResponse.header("Content-Length")?.toLongOrNull() ?: -1L

      val response = if (contentLength > 0) {
        newFixedLengthResponse(Response.Status.OK, contentType, body.byteStream(), contentLength)
      } else {
        newChunkedResponse(Response.Status.OK, contentType, body.byteStream())
      }

      response.addHeader("Access-Control-Allow-Origin", "*")
      response
    } catch (e: Exception) {
      newFixedLengthResponse(
        Response.Status.INTERNAL_ERROR,
        MIME_PLAINTEXT,
        "Proxy error: ${e.message}"
      )
    }
  }
}
