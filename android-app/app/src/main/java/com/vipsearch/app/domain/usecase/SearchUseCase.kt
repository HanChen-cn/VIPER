package com.vipsearch.app.domain.usecase

import com.vipsearch.app.data.model.Show
import com.vipsearch.app.data.repository.SearchRepository

class SearchUseCase(
  private val searchRepository: SearchRepository
) {
  suspend operator fun invoke(keyword: String): List<Show> = searchRepository.search(keyword)
}
