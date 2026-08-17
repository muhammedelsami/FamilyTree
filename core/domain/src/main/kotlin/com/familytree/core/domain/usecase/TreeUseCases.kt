package com.familytree.core.domain.usecase

import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.model.Tree
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTreesUseCase @Inject constructor(
    private val treeRepository: TreeRepository,
) {
    operator fun invoke(): Flow<List<Tree>> = treeRepository.observeTrees()
}

class CreateTreeUseCase @Inject constructor(
    private val treeRepository: TreeRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Creates an empty tree and makes it the open one, so the UI can navigate straight in. */
    suspend operator fun invoke(title: String): Long {
        val treeId = treeRepository.createTree(title)
        settingsRepository.update { it.copy(openTreeId = treeId) }
        return treeId
    }
}

class RenameTreeUseCase @Inject constructor(
    private val treeRepository: TreeRepository,
) {
    suspend operator fun invoke(treeId: Long, title: String) {
        require(title.isNotBlank()) { "A tree title cannot be blank" }
        treeRepository.renameTree(treeId, title.trim())
    }
}

class DeleteTreeUseCase @Inject constructor(
    private val treeRepository: TreeRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(treeId: Long) {
        treeRepository.deleteTree(treeId)
        // Deleting the tree that is currently open would otherwise leave a dangling id
        // that the launcher tries to reopen on next start.
        settingsRepository.update { settings ->
            if (settings.openTreeId == treeId) settings.copy(openTreeId = null) else settings
        }
    }
}

class ReorderTreesUseCase @Inject constructor(
    private val treeRepository: TreeRepository,
) {
    suspend operator fun invoke(orderedIds: List<Long>) = treeRepository.reorderTrees(orderedIds)
}
