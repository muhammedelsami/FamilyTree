package com.familytree.core.domain.usecase

import com.familytree.core.domain.repository.EventRepository
import com.familytree.core.domain.repository.FamilyRepository
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.model.Event
import com.familytree.core.model.MemberRole
import com.familytree.core.model.OwnerType
import com.familytree.core.model.Person
import com.familytree.core.model.PersonDraft
import com.familytree.core.model.PersonName
import com.familytree.core.model.Relation
import com.familytree.core.model.RelativeTarget
import com.familytree.core.model.Sex
import javax.inject.Inject

/**
 * Creates or updates a person from what the editor collected.
 *
 * Empty fields remove their record rather than storing a blank one: a person whose
 * death details are cleared should stop being marked as deceased, not carry an empty
 * `DEAT` around.
 */
class SavePersonUseCase @Inject constructor(
    private val personRepository: PersonRepository,
    private val eventRepository: EventRepository,
    private val treeRepository: TreeRepository,
) {

    suspend operator fun invoke(draft: PersonDraft): Long {
        val editingId = draft.personId
        val personId = if (editingId == null) {
            personRepository.createPerson(
                person = Person(treeId = draft.treeId, sex = draft.sex),
                names = listOfNotNull(draft.toName(personId = 0L)),
            )
        } else {
            val existing = personRepository.getPerson(editingId)
                ?: error("Person $editingId no longer exists")
            personRepository.updatePerson(existing.copy(sex = draft.sex))
            saveName(draft, editingId)
            editingId
        }

        saveEvent(
            personId = personId,
            treeId = draft.treeId,
            tag = BIRTH_TAG,
            date = draft.birthDate,
            place = draft.birthPlace,
            wanted = draft.hasBirthDetails,
        )
        saveEvent(
            personId = personId,
            treeId = draft.treeId,
            tag = DEATH_TAG,
            date = draft.deathDate,
            place = draft.deathPlace,
            // A death record with no details still records the fact of death.
            wanted = draft.deceased,
        )

        // A first person becomes the tree's starting point automatically.
        val tree = treeRepository.getTree(draft.treeId)
        if (tree?.rootPersonId == null) treeRepository.setRootPerson(draft.treeId, personId)
        treeRepository.refreshCounters(draft.treeId)
        return personId
    }

    private suspend fun saveName(draft: PersonDraft, personId: Long) {
        val existing = personRepository.getNames(personId).minByOrNull { it.position }
        val value = draft.nameValue()
        if (value == null && existing == null) return
        if (value == null) {
            existing?.let { personRepository.deleteName(it.id) }
            return
        }
        personRepository.upsertName(
            (existing ?: PersonName(personId = personId)).copy(
                personId = personId,
                value = value,
                given = draft.given.trim().takeIf { it.isNotEmpty() },
                surname = draft.surname.trim().takeIf { it.isNotEmpty() },
            ),
        )
    }

    private suspend fun saveEvent(
        personId: Long,
        treeId: Long,
        tag: String,
        date: String,
        place: String,
        wanted: Boolean,
    ) {
        val existing = eventRepository.getFor(OwnerType.PERSON, personId).firstOrNull { it.tag == tag }
        if (!wanted) {
            existing?.let { eventRepository.delete(it.id) }
            return
        }
        eventRepository.upsert(
            (existing ?: Event(treeId = treeId, ownerType = OwnerType.PERSON, ownerId = personId, tag = tag)).copy(
                date = date.trim().takeIf { it.isNotEmpty() },
                place = place.trim().takeIf { it.isNotEmpty() },
                // `Y` is how GEDCOM records that an event happened when nothing else is known.
                value = if (date.isBlank() && place.isBlank()) "Y" else null,
            ),
        )
    }

    private fun PersonDraft.toName(personId: Long): PersonName? {
        val value = nameValue() ?: return null
        return PersonName(
            personId = personId,
            value = value,
            given = given.trim().takeIf { it.isNotEmpty() },
            surname = surname.trim().takeIf { it.isNotEmpty() },
        )
    }

    private companion object {
        const val BIRTH_TAG = "BIRT"
        const val DEATH_TAG = "DEAT"
    }
}

/**
 * Links a person to an existing one through a family.
 *
 * GEDCOM has no direct person-to-person links: every relationship is a shared family.
 * So adding a father means putting him in the family where the starting person is a
 * child, creating that family first if it does not exist yet.
 */
class AddRelativeUseCase @Inject constructor(
    private val personRepository: PersonRepository,
    private val familyRepository: FamilyRepository,
) {

    suspend operator fun invoke(
        treeId: Long,
        pivotPersonId: Long,
        newPersonId: Long,
        target: RelativeTarget,
    ) {
        val pivot = personRepository.getPerson(pivotPersonId) ?: return
        val newPerson = personRepository.getPerson(newPersonId) ?: return

        when (target.relation) {
            // Both live in the family the starting person belongs to as a child.
            Relation.PARENT, Relation.SIBLING -> {
                val familyId = target.familyId
                    ?: familyRepository.getParentFamilies(pivotPersonId).firstOrNull()?.id
                    ?: familyRepository.createFamily(treeId).also { created ->
                        familyRepository.addMember(created, pivotPersonId, MemberRole.CHILD)
                    }
                val role = if (target.relation == Relation.PARENT) {
                    spouseRoleFor(newPerson.sex, familyId)
                } else {
                    MemberRole.CHILD
                }
                familyRepository.addMember(familyId, newPersonId, role)
            }

            // Both live in a family the starting person belongs to as a spouse.
            Relation.PARTNER, Relation.CHILD -> {
                val familyId = target.familyId
                    ?: familyRepository.getSpouseFamilies(pivotPersonId).firstOrNull()?.id
                    ?: familyRepository.createFamily(treeId).also { created ->
                        familyRepository.addMember(created, pivotPersonId, spouseRoleFor(pivot.sex, created))
                    }
                val role = if (target.relation == Relation.PARTNER) {
                    spouseRoleFor(newPerson.sex, familyId)
                } else {
                    MemberRole.CHILD
                }
                familyRepository.addMember(familyId, newPersonId, role)
            }
        }
    }

    /**
     * Picks the spouse slot to fill.
     *
     * Sex decides it when stated. When it is not, the free slot is taken, so adding a
     * partner to a family that already has a husband produces a wife rather than a
     * second husband.
     */
    private suspend fun spouseRoleFor(sex: Sex, familyId: Long): MemberRole = when (sex) {
        Sex.MALE -> MemberRole.HUSBAND
        Sex.FEMALE -> MemberRole.WIFE
        else -> {
            val taken = familyRepository.getMembers(familyId).map { it.role }
            if (MemberRole.HUSBAND in taken) MemberRole.WIFE else MemberRole.HUSBAND
        }
    }
}
