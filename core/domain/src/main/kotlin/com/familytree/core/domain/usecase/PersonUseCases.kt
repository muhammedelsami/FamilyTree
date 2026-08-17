package com.familytree.core.domain.usecase

import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.model.PersonSorting
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.matches
import com.familytree.core.model.sortedBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The person list, already filtered and ordered.
 *
 * Filtering and sorting happen here rather than in the ViewModel so the same rules
 * apply wherever a person list appears — the main list, the relative picker, the
 * merge screens — and so they stay unit-testable without Android.
 */
class ObservePersonListUseCase @Inject constructor(
    private val personRepository: PersonRepository,
) {
    operator fun invoke(
        treeId: Long,
        query: String,
        sorting: PersonSorting,
    ): Flow<List<PersonSummary>> = personRepository.observePersonSummaries(treeId)
        .map { summaries -> summaries.filter { it.matches(query) }.sortedBy(sorting) }
}

class DeletePersonUseCase @Inject constructor(
    private val personRepository: PersonRepository,
) {
    suspend operator fun invoke(personId: Long) = personRepository.deletePerson(personId)
}
