package com.secondserve.domain.event

import com.secondserve.domain.model.MatchScore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class DataLayerEventBus {
    private val _closeSessionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeSessionRequests: SharedFlow<Unit> = _closeSessionRequests

    fun emitCloseRequest() {
        _closeSessionRequests.tryEmit(Unit)
    }

    private val _gameOverEvents = MutableSharedFlow<MatchScore>(extraBufferCapacity = 1)
    val gameOverEvents: SharedFlow<MatchScore> = _gameOverEvents

    fun emitGameOver(score: MatchScore) {
        _gameOverEvents.tryEmit(score)
    }

    private val _startSessionRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val startSessionRequests: SharedFlow<Long> = _startSessionRequests

    fun emitStartSession(sessionId: Long) {
        _startSessionRequests.tryEmit(sessionId)
    }
}
