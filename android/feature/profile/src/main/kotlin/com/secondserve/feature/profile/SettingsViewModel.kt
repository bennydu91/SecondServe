package com.secondserve.feature.profile

import androidx.lifecycle.ViewModel
import com.secondserve.domain.notification.NotificationFrequency
import com.secondserve.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    data class SettingsUiState(
        val frequency: NotificationFrequency = NotificationFrequency.DAILY,
        val silentModeUntil: Long = 0L
    )

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            frequency = notificationRepository.getFrequency(),
            silentModeUntil = notificationRepository.getSilentModeUntil()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onFrequencyChanged(frequency: NotificationFrequency) {
        notificationRepository.setFrequency(frequency)
        _uiState.update { it.copy(frequency = frequency) }
    }

    fun onSilentModeUntilChanged(epochMs: Long) {
        notificationRepository.setSilentModeUntil(epochMs)
        _uiState.update { it.copy(silentModeUntil = epochMs) }
    }

    fun onSilentModeCleared() {
        notificationRepository.setSilentModeUntil(0L)
        _uiState.update { it.copy(silentModeUntil = 0L) }
    }
}
