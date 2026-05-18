package com.vipsearch.app.data.remote

import android.content.Context
import com.vipsearch.app.data.model.ApiSource
import com.vipsearch.app.data.model.ApiSourceBundle
import com.vipsearch.app.data.model.ParseApi
import org.json.JSONObject

class ApiSourcesProvider(private val context: Context) {
  fun load(): ApiSourceBundle {
    val jsonText = context.resources.openRawResource(
      com.vipsearch.app.R.raw.api_sources
    ).bufferedReader().use { it.readText() }
    val root = JSONObject(jsonText)

    val cmsSources = buildList {
      val cmsArray = root.getJSONArray("cmsSources")
      for (i in 0 until cmsArray.length()) {
        val item = cmsArray.getJSONObject(i)
        val params = mutableMapOf<String, String>()
        val paramsObj = item.optJSONObject("params")
        if (paramsObj != null) {
          val keys = paramsObj.keys()
          while (keys.hasNext()) {
            val key = keys.next()
            params[key] = paramsObj.optString(key, "")
          }
        }
        add(
          ApiSource(
            id = item.getString("id"),
            name = item.getString("name"),
            baseUrl = item.getString("baseUrl"),
            searchPath = item.getString("searchPath"),
            searchParam = item.getString("searchParam"),
            enabled = item.optBoolean("enabled", true),
            params = params
          )
        )
      }
    }

    val parseApis = buildList {
      val parseArray = root.getJSONArray("parseApis")
      for (i in 0 until parseArray.length()) {
        val item = parseArray.getJSONObject(i)
        add(
          ParseApi(
            id = item.getString("id"),
            name = item.getString("name"),
            url = item.getString("url"),
            mobile = item.optBoolean("mobile", false)
          )
        )
      }
    }

    return ApiSourceBundle(cmsSources = cmsSources, parseApis = parseApis)
  }
}
