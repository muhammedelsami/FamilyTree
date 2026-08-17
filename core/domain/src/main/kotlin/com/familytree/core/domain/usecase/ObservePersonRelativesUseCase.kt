package com.familytree.core.domain.usecase

import com.familytree.core.domain.repository.FamilyRepository
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.model.MemberRole
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.RelativeGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * A person's relatives, grouped by the family that connects them.
 *
 * Built from three tree-wide streams and assembled in memory rather than queried per
 * family, so a person with several marriages and a dozen children still costs the same
 * three queries.
 */
class ObservePersonRelativesUseCase @Inject constructor(
    private val personRepository: PersonRepository,
    private val familyRepository: FamilyRepository,
) {

    operator fun invoke(treeId: Long, personId: Long): Flow<List<RelativeGroup>> =
        combine(
            personRepository.observePersonSummaries(treeId),
            familyRepository.observeAllMemberships(treeId),
            familyRepository.observeFamilies(treeId),
        ) { summaries, memberships, families ->
            val summaryById = summaries.associateBy { it.person.id }
            val familyById = families.associateBy { it.id }
            val membersByFamily = memberships.groupBy { it.familyId }

            memberships
                .filter { it.personId == personId }
                .mapNotNull { membership ->
                    val family = familyById[membership.familyId] ?: return@mapNotNull null
                    val members = membersByFamily[membership.familyId].orEmpty()

                    // Being a child here makes this the family the person came from.
                    val kind = if (membership.role == MemberRole.CHILD) {
                        RelativeGroup.Kind.ORIGIN
                    } else {
                        RelativeGroup.Kind.OWN
                    }

                    RelativeGroup(
                        family = family,
                        kind = kind,
                        partners = members
                            .filter { it.role != MemberRole.CHILD && it.personId != personId }
                            .toSummaries(summaryById),
                        children = members
                            .filter { it.role == MemberRole.CHILD && it.personId != personId }
                            .toSummaries(summaryById),
                    )
                }
                // Origin first: a profile reads more naturally from where someone came
                // from towards the family they made.
                .sortedBy { if (it.kind == RelativeGroup.Kind.ORIGIN) 0 else 1 }
        }

    private fun List<com.familytree.core.model.FamilyMember>.toSummaries(
        summaryById: Map<Long, PersonSummary>,
    ): List<PersonSummary> = sortedBy { it.position }.mapNotNull { summaryById[it.personId] }
}
