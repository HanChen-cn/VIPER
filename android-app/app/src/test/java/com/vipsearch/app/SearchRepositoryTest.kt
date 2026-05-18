package com.vipsearch.app

import com.vipsearch.app.data.model.Episode
import com.vipsearch.app.data.model.Show
import com.vipsearch.app.data.repository.SearchRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRepositoryTest {
  @Test
  fun `merge should dedupe shows by name and episodes by episode name`() {
    val sourceA = listOf(
      Show(
        name = "测试剧",
        episodes = listOf(
          Episode(name = "第1集", playUrl = "https://a.com/1", source = "A"),
          Episode(name = "第2集", playUrl = "https://a.com/2", source = "A")
        )
      )
    )
    val sourceB = listOf(
      Show(
        name = "测试剧",
        episodes = listOf(
          Episode(name = "第1集", playUrl = "https://b.com/1", source = "B"),
          Episode(name = "第3集", playUrl = "https://b.com/3", source = "B")
        )
      )
    )

    val merged = SearchRepository.mergeAndDedupe(listOf(sourceA, sourceB))
    assertEquals(1, merged.size)
    assertEquals(3, merged.first().episodes.size)
    assertEquals(listOf("第1集", "第2集", "第3集"), merged.first().episodes.map { it.name })
  }
}
