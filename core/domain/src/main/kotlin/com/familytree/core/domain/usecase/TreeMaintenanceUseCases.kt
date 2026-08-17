package com.familytree.core.domain.usecase

import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.model.TreeIssue
import javax.inject.Inject

class FindTreeIssuesUseCase @Inject constructor(
    private val treeRepository: TreeRepository,
) {
    suspend operator fun invoke(treeId: Long): List<TreeIssue> = treeRepository.findIssues(treeId)
}

/**
 * Repairs are only ever run after the user has seen the findings — changing someone's
 * genealogy data without showing them what is about to change would not be acceptable.
 */
class RepairTreeUseCase @Inject constructor(
    private val treeRepository: TreeRepository,
) {
    suspend operator fun invoke(treeId: Long): List<TreeIssue> = treeRepository.repairIssues(treeId)
}
