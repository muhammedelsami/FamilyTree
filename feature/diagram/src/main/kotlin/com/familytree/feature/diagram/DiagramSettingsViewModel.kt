package com.familytree.feature.diagram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.model.DiagramSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagramSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<DiagramSettings> = settingsRepository.settings
        .map { it.diagram }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagramSettings())

    /**
     * Every change is normalised before it is stored, so an impossible combination — say
     * great-uncles reaching further back than ancestors — can never be persisted and then
     * confuse the layout engine.
     */
    fun update(transform: (DiagramSettings) -> DiagramSettings) = viewModelScope.launch {
        settingsRepository.update { app ->
            app.copy(diagram = transform(app.diagram).normalised())
        }
    }

    fun reset() = update { DiagramSettings() }
}
