package com.vipsearch.app.domain.usecase

import com.vipsearch.app.data.repository.AltSourceRepository

class GetAltSourcesUseCase(
  private val altSourceRepository: AltSourceRepository
) {
  suspend operator fun invoke(
    showName: String,
    episodeName: String,
    primaryUrl: String,
    fetcher: suspend () -> List<String>
  ): List<String> {
    return altSourceRepository.getOrFetch(
      showName = showName,
      episodeName = episodeName,
      primaryUrl = primaryUrl,
      fetcher = fetcher
    )
  }
}
