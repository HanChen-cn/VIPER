package com.vipsearch.app.player

class SourceSwitchController(
  private val maxAutoFallback: Int = 3
) {
  private val sources = mutableListOf<String>()
  private var currentIndex = 0
  private var autoFallbackCount = 0

  fun setSources(primary: String, alternatives: List<String>) {
    sources.clear()
    sources += listOf(primary) + alternatives.filter { it != primary }
    currentIndex = 0
    autoFallbackCount = 0
  }

  fun current(): String? = sources.getOrNull(currentIndex)

  fun switchTo(index: Int): String? {
    if (index !in sources.indices) return null
    currentIndex = index
    autoFallbackCount = 0
    return sources[currentIndex]
  }

  fun nextOnFailure(): String? {
    if (autoFallbackCount >= maxAutoFallback) return null
    val nextIndex = currentIndex + 1
    if (nextIndex !in sources.indices) return null
    currentIndex = nextIndex
    autoFallbackCount += 1
    return sources[currentIndex]
  }

  fun all(): List<String> = sources.toList()
}
