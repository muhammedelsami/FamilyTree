package com.familytree.feature.trees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.usecase.DeleteTreeUseCase
import com.familytree.core.domain.usecase.ExportGedcomUseCase
import com.familytree.core.domain.usecase.FindTreeIssuesUseCase
import com.familytree.core.domain.usecase.ObserveTreesUseCase
import com.familytree.core.domain.usecase.RenameTreeUseCase
import com.familytree.core.domain.usecase.RepairTreeUseCase
import com.familytree.core.domain.usecase.ReorderTreesUseCase
import com.familytree.core.model.Tree
import com.familytree.core.model.TreeIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TreesUiState {
    data object Loading : TreesUiState
    data object Empty : TreesUiState

    data class Trees(val trees: List<Tree>) : TreesUiState
}

/** One-off outcomes the screen reports and then forgets. */
sealed interface TreesEvent {
    data class Exported(val treeTitle: String) : TreesEvent
    data class Failed(val message: String) : TreesEvent
}

@HiltViewModel
class TreesViewModel @Inject constructor(
    observeTrees: ObserveTreesUseCase,
    private val renameTree: RenameTreeUseCase,
    private val deleteTree: DeleteTreeUseCase,
    private val reorderTrees: ReorderTreesUseCase,
    private val exportGedcom: ExportGedcomUseCase,
    private val findTreeIssues: FindTreeIssuesUseCase,
    private val repairTree: RepairTreeUseCase,
) : ViewModel() {

    val uiState: StateFlow<TreesUiState> = observeTrees()
        .map { trees ->
            if (trees.isEmpty()) TreesUiState.Empty else TreesUiState.Trees(trees)
        }
        .stateIn(
            scope = viewModelScope,
            // Survives a rotation without re-querying, but stops when the screen leaves.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TreesUiState.Loading,
        )

    private val _events = Channel<TreesEvent>(Channel.BUFFERED)
    val events: Flow<TreesEvent> = _events.receiveAsFlow()

    fun onRename(treeId: Long, title: String) = viewModelScope.launch {
        runCatching { renameTree(treeId, title) }
            .onFailure { _events.send(TreesEvent.Failed(it.readableMessage())) }
    }

    fun onDelete(treeId: Long) = viewModelScope.launch {
        runCatching { deleteTree(treeId) }
            .onFailure { _events.send(TreesEvent.Failed(it.readableMessage())) }
    }

    fun onReorder(orderedIds: List<Long>) = viewModelScope.launch {
        reorderTrees(orderedIds)
    }

    /** Null while no check is in progress; a report once one has finished. */
    private val _issues = MutableStateFlow<IssueReport?>(null)
    val issues: kotlinx.coroutines.flow.StateFlow<IssueReport?> = _issues.asStateFlow()

    fun onCheckTree(tree: Tree) = viewModelScope.launch {
        runCatching { findTreeIssues(tree.id) }
            .onSuccess { _issues.value = IssueReport(tree, it, repaired = false) }
            .onFailure { _events.send(TreesEvent.Failed(it.readableMessage())) }
    }

    fun onRepair(tree: Tree) = viewModelScope.launch {
        runCatching { repairTree(tree.id) }
            .onSuccess { _issues.value = IssueReport(tree, it, repaired = true) }
            .onFailure { _events.send(TreesEvent.Failed(it.readableMessage())) }
    }

    fun onDismissIssues() {
        _issues.value = null
    }

    fun onExport(tree: Tree, uri: String) = viewModelScope.launch {
        runCatching { exportGedcom(tree.id, uri) }
            .onSuccess { _events.send(TreesEvent.Exported(tree.title)) }
            .onFailure { _events.send(TreesEvent.Failed(it.readableMessage())) }
    }
}

/** @param repaired false while listing findings, true once they have been acted on. */
data class IssueReport(val tree: Tree, val issues: List<TreeIssue>, val repaired: Boolean)

internal fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.simpleName.orEmpty()
