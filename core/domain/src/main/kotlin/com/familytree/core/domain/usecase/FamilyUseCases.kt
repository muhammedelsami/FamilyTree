package com.familytree.core.domain.usecase

import com.familytree.core.domain.repository.FamilyRepository
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.model.EventCatalog
import com.familytree.core.model.FamilySorting
import com.familytree.core.model.FamilySummary
import com.familytree.core.model.LifeEvent
import com.familytree.core.model.MemberRole
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.date.GedcomDate
import com.familytree.core.model.matches
import com.familytree.core.model.sortedBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The family list, already filtered and ordered.
 *
 * A family has no name of its own, so a row is only meaningful once its members are
 * resolved — which is why this assembles people and memberships rather than reading
 * families alone.
 */
class ObserveFamilyListUseCase @Inject constructor(
    private val personRepository: PersonRepository,
    private val familyRepository: FamilyRepository,
) {

    operator fun invoke(
        treeId: Long,
        query: String,
        sorting: FamilySorting,
    ): Flow<List<FamilySummary>> = combine(
        familyRepository.observeFamilies(treeId),
        familyRepository.observeAllMemberships(treeId),
        personRepository.observePersonSummaries(treeId),
        familyRepository.observeFamilyEvents(treeId),
    ) { families, memberships, people, events ->
        val personById = people.associateBy { it.person.id }
        val membersByFamily = memberships.groupBy { it.familyId }
        val eventsByFamily = events.groupBy { it.ownerId }

        families.map { family ->
            val members = membersByFamily[family.id].orEmpty().sortedBy { it.position }
            val partners = members
                .filter { it.role != MemberRole.CHILD }
                .mapNotNull { personById[it.personId] }
            val children = members
                .filter { it.role == MemberRole.CHILD }
                .mapNotNull { personById[it.personId] }

            val marriageEvent = eventsByFamily[family.id].orEmpty()
                .firstOrNull { it.tag in EventCatalog.MARRIAGE_TAGS }

            FamilySummary(
                family = family,
                partners = partners,
                children = children,
                marriage = marriageEvent?.let {
                    LifeEvent(it.date, it.place, it.date?.let(::GedcomDate)?.year())
                },
                searchText = (partners + children).joinToString(" ") { it.searchText },
            )
        }
            .filter { it.matches(query) }
            .sortedBy(sorting)
    }
}

class CreateFamilyUseCase @Inject constructor(
    private val familyRepository: FamilyRepository,
) {
    suspend operator fun invoke(treeId: Long): Long = familyRepository.createFamily(treeId)
}

class DeleteFamilyUseCase @Inject constructor(
    private val familyRepository: FamilyRepository,
) {
    suspend operator fun invoke(familyId: Long) = familyRepository.deleteFamily(familyId)
}
