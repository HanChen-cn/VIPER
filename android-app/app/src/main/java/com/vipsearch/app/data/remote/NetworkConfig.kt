package com.vipsearch.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object NetworkConfig {
  fun createClient(debug: Boolean = false): OkHttpClient {
    val builder = OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .writeTimeout(10, TimeUnit.SECONDS)

    if (debug) {
      val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
      }
      builder.addInterceptor(logger)
    }

    builder.addInterceptor { chain ->
      val request = chain.request().newBuilder()
        .header("User-Agent", "VIPSearchAndroid/0.1")
        .build()
      chain.proceed(request)
    }

    return builder.build()
  }
}
