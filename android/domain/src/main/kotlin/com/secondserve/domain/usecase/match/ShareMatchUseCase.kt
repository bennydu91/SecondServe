package com.secondserve.domain.usecase.match

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.repository.LiveShareRepository
import javax.inject.Inject

class ShareMatchUseCase @Inject constructor(
    private val liveShareRepository: LiveShareRepository
) {
    suspend operator fun invoke(sessionId: Long): AppResult<LiveShareInfo> =
        liveShareRepository.getOrCreateShare(sessionId)
}
