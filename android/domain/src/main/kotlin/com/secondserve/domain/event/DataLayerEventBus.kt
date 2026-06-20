package com.secondserve.domain.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class DataLayerEventBus {
    private val _closeSessionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeSessionRequests: SharedFlow<Unit> = _closeSessionRequests

    fun emitCloseRequest() {
        _closeSessionRequests.tryEmit(Unit)
    }
}
