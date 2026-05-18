package com.vipsearch.app.data.model

data class ApiSource(
  val id: String,
  val name: String,
  val baseUrl: String,
  val searchPath: String,
  val searchParam: String,
  val enabled: Boolean,
  val params: Map<String, String> = emptyMap()
)

data class ParseApi(
  val id: String,
  val name: String,
  val url: String,
  val mobile: Boolean
)

data class ApiSourceBundle(
  val cmsSources: List<ApiSource>,
  val parseApis: List<ParseApi>
)
