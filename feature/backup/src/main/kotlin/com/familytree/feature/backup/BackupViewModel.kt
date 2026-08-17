package com.familytree.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.BackupFile
import com.familytree.core.domain.repository.BackupRepository
import com.familytree.core.domain.repository.TreeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val treeTitle: String = "",
    val backups: List<BackupFile> = emptyList(),
    val automatic: Boolean = false,
    val busy: Boolean = false,
    val keptPerTree: Int = 3,
    val message: Message? = null,
    /** Set when a restore produced a new tree, so the screen can offer to open it. */
    val restoredTreeId: Long? = null,
) {
    enum class Message { SAVED, RESTORED, EXPORTED, DELETED, FAILED }
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val trees: TreeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState(keptPerTree = backups.keptPerTree))
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var treeId = 0L

    fun load(treeId: Long) {
        this.treeId = treeId
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        val tree = trees.getTree(treeId)
        _uiState.update {
            it.copy(
                treeTitle = tree?.title.orEmpty(),
                automatic = tree?.backupEnabled == true,
                backups = backups.observeBackups(treeId).first(),
            )
        }
    }

    fun backUpNow() = run(BackupUiState.Message.SAVED) { backups.backUp(treeId).map { } }

    fun exportTo(uri: String) = run(BackupUiState.Message.EXPORTED) { backups.exportTo(treeId, uri) }

    fun restore(backup: BackupFile) = viewModelScope.launch {
        _uiState.update { it.copy(busy = true) }
        backups.restore(backup.id)
            .onSuccess { newTreeId ->
                _uiState.update {
                    it.copy(restoredTreeId = newTreeId, message = BackupUiState.Message.RESTORED)
                }
            }
            .onFailure { _uiState.update { s -> s.copy(message = BackupUiState.Message.FAILED) } }
        _uiState.update { it.copy(busy = false) }
        refresh()
    }

    fun restoreFrom(uri: String, fallbackTitle: String) = viewModelScope.launch {
        _uiState.update { it.copy(busy = true) }
        backups.restoreFrom(uri, fallbackTitle)
            .onSuccess { newTreeId ->
                _uiState.update {
                    it.copy(restoredTreeId = newTreeId, message = BackupUiState.Message.RESTORED)
                }
            }
            .onFailure { _uiState.update { s -> s.copy(message = BackupUiState.Message.FAILED) } }
        _uiState.update { it.copy(busy = false) }
    }

    fun delete(backup: BackupFile) = run(BackupUiState.Message.DELETED) { backups.delete(backup.id) }

    fun setAutomatic(enabled: Boolean) = viewModelScope.launch {
        backups.setAutomaticBackup(treeId, enabled)
        refresh()
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onRestoreHandled() = _uiState.update { it.copy(restoredTreeId = null) }

    private fun run(success: BackupUiState.Message, action: suspend () -> Result<Unit>) =
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val message = if (action().isSuccess) success else BackupUiState.Message.FAILED
            _uiState.update { it.copy(busy = false, message = message) }
            refresh()
        }
}
