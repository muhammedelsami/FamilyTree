package com.familytree.feature.trees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.usecase.CreateTreeUseCase
import com.familytree.core.domain.usecase.ImportGedcomUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NewTreeEvent {
    /** The tree is ready; the caller navigates into it. */
    data class Created(val treeId: Long) : NewTreeEvent
    data class Failed(val message: String) : NewTreeEvent
}

@HiltViewModel
class NewTreeViewModel @Inject constructor(
    private val createTree: CreateTreeUseCase,
    private val importGedcom: ImportGedcomUseCase,
) : ViewModel() {

    /** Importing a large file takes a while, so the screen blocks and shows progress. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<NewTreeEvent>(Channel.BUFFERED)
    val events: Flow<NewTreeEvent> = _events.receiveAsFlow()

    fun onCreateEmpty(title: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { createTree(title) }
            .onSuccess { _events.send(NewTreeEvent.Created(it)) }
            .onFailure { _events.send(NewTreeEvent.Failed(it.readableMessage())) }
        _busy.value = false
    }

    fun onImportGedcom(uri: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { importGedcom(uri) }
            .onSuccess { _events.send(NewTreeEvent.Created(it)) }
            .onFailure { _events.send(NewTreeEvent.Failed(it.readableMessage())) }
        _busy.value = false
    }
}
