package com.familytree.core.domain.usecase

import com.familytree.core.domain.repository.GedcomRepository
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.domain.repository.TreeRepository
import javax.inject.Inject

class ImportGedcomUseCase @Inject constructor(
    private val gedcomRepository: GedcomRepository,
    private val treeRepository: TreeRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * @param title falls back to the file name, so the user is not forced to invent one
     *   before they can see whether the import worked.
     */
    suspend operator fun invoke(uri: String, title: String? = null): Long {
        val resolved = title?.takeIf { it.isNotBlank() }
            ?: gedcomRepository.suggestTitle(uri)
            ?: "Imported tree"
        val treeId = gedcomRepository.importFrom(uri, resolved)
        // Counts and the generation span are only meaningful once every record is in.
        treeRepository.refreshCounters(treeId)
        settingsRepository.update { it.copy(openTreeId = treeId) }
        return treeId
    }
}

class ExportGedcomUseCase @Inject constructor(
    private val gedcomRepository: GedcomRepository,
) {
    suspend operator fun invoke(treeId: Long, uri: String) = gedcomRepository.exportTo(treeId, uri)
}
