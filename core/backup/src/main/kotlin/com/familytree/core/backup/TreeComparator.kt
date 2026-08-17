package com.familytree.core.backup

import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.entity.EventEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.PersonNameEntity
import com.familytree.core.model.OwnerType
import com.familytree.core.model.RecordType
import com.familytree.core.model.TreeComparison
import com.familytree.core.model.TreeDifference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out what a returning copy of a tree has that this one does not.
 *
 * Records are matched by their GEDCOM cross-reference id, not by name or by row id. Row
 * ids are local to a device and mean nothing across a share; names are not unique — a
 * family with three Mehmet Yılmaz in it is ordinary, not a corner case. The cross-reference
 * id is the only thing that survives an export, a trip through someone else's phone and an
 * import, still pointing at the same person.
 *
 * The comparison is deliberately at record level rather than field level. "Ahmet changed"
 * is something a user can judge; "`person_names.surnamePrefix` changed from null to empty"
 * is not.
 */
@Singleton
class TreeComparator @Inject constructor(
    private val database: FamilyTreeDatabase,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun compare(localTreeId: Long, incomingTreeId: Long): TreeComparison =
        withContext(ioDispatcher) {
            val differences = buildList {
                addAll(comparePeople(localTreeId, incomingTreeId))
                addAll(compareFamilies(localTreeId, incomingTreeId))
                addAll(compareNotes(localTreeId, incomingTreeId))
                addAll(compareSources(localTreeId, incomingTreeId))
                addAll(compareMedia(localTreeId, incomingTreeId))
            }
            TreeComparison(localTreeId, incomingTreeId, differences)
        }

    private suspend fun comparePeople(local: Long, incoming: Long): List<TreeDifference> {
        val here = database.personDao().getAll(local).associateBy { it.gedcomId }
        val there = database.personDao().getAll(incoming).associateBy { it.gedcomId }

        val hereNames = database.personDao().getAllNames(local).groupBy { it.personId }
        val thereNames = database.personDao().getAllNames(incoming).groupBy { it.personId }
        val hereEvents = database.eventDao().getAll(local)
            .filter { it.ownerType == OwnerType.PERSON }.groupBy { it.ownerId }
        val thereEvents = database.eventDao().getAll(incoming)
            .filter { it.ownerType == OwnerType.PERSON }.groupBy { it.ownerId }

        fun fingerprint(
            person: PersonEntity,
            names: List<PersonNameEntity>,
            events: List<EventEntity>,
        ): String = buildString {
            append(person.sex).append('|')
            names.sortedBy { it.position }.forEach { name ->
                append(name.value.orEmpty()).append(name.given.orEmpty()).append(name.surname.orEmpty()).append(';')
            }
            append('|')
            events.sortedBy { it.tag }.forEach { event ->
                append(event.tag).append(event.date.orEmpty()).append(event.place.orEmpty()).append(';')
            }
        }

        fun describe(person: PersonEntity, names: List<PersonNameEntity>): String =
            names.minByOrNull { it.position }?.let { name ->
                // "Ayşe /Yılmaz/" becomes "Ayşe Yılmaz": the slashes that mark the surname
                // leave a double space behind, and the rest of the application collapses
                // them the same way.
                name.value?.replace("/", " ")?.collapseSpaces()
                    ?: listOfNotNull(name.given, name.surname).joinToString(" ")
            }?.takeIf { it.isNotBlank() } ?: person.gedcomId.orEmpty()

        return diff(
            RecordType.PERSON,
            here = here,
            there = there,
            fingerprintHere = { fingerprint(it, hereNames[it.id].orEmpty(), hereEvents[it.id].orEmpty()) },
            fingerprintThere = { fingerprint(it, thereNames[it.id].orEmpty(), thereEvents[it.id].orEmpty()) },
            describeHere = { describe(it, hereNames[it.id].orEmpty()) },
            describeThere = { describe(it, thereNames[it.id].orEmpty()) },
        )
    }

    private suspend fun compareFamilies(local: Long, incoming: Long): List<TreeDifference> {
        val here = database.familyDao().getAll(local).associateBy { it.gedcomId }
        val there = database.familyDao().getAll(incoming).associateBy { it.gedcomId }

        val herePeople = database.personDao().getAll(local).associateBy { it.id }
        val therePeople = database.personDao().getAll(incoming).associateBy { it.id }
        val hereMembers = database.familyDao().getAllMemberships(local).groupBy { it.familyId }
        val thereMembers = database.familyDao().getAllMemberships(incoming).groupBy { it.familyId }

        // A family is only its membership: the same people in the same roles means the
        // same family, whatever the row ids happen to be on either device.
        fun fingerprint(members: List<FamilyMemberEntity>, people: Map<Long, PersonEntity>): String =
            members
                .map { "${it.role}:${people[it.personId]?.gedcomId.orEmpty()}" }
                .sorted()
                .joinToString(",")

        return diff(
            RecordType.FAMILY,
            here = here,
            there = there,
            fingerprintHere = { fingerprint(hereMembers[it.id].orEmpty(), herePeople) },
            fingerprintThere = { fingerprint(thereMembers[it.id].orEmpty(), therePeople) },
            describeHere = { it.gedcomId.orEmpty() },
            describeThere = { it.gedcomId.orEmpty() },
        )
    }

    private suspend fun compareNotes(local: Long, incoming: Long) = diff(
        RecordType.NOTE,
        here = database.noteDao().getAll(local).filter { it.gedcomId != null }.associateBy { it.gedcomId },
        there = database.noteDao().getAll(incoming).filter { it.gedcomId != null }.associateBy { it.gedcomId },
        fingerprintHere = { it.value.orEmpty() },
        fingerprintThere = { it.value.orEmpty() },
        describeHere = { it.value.orEmpty().take(SUMMARY_LENGTH) },
        describeThere = { it.value.orEmpty().take(SUMMARY_LENGTH) },
    )

    private suspend fun compareSources(local: Long, incoming: Long) = diff(
        RecordType.SOURCE,
        here = database.sourceDao().getAll(local).associateBy { it.gedcomId },
        there = database.sourceDao().getAll(incoming).associateBy { it.gedcomId },
        fingerprintHere = { "${it.title}|${it.author}|${it.text}" },
        fingerprintThere = { "${it.title}|${it.author}|${it.text}" },
        describeHere = { it.title ?: it.gedcomId.orEmpty() },
        describeThere = { it.title ?: it.gedcomId.orEmpty() },
    )

    private suspend fun compareMedia(local: Long, incoming: Long) = diff(
        RecordType.MEDIA,
        here = database.mediaDao().getAll(local).filter { it.gedcomId != null }.associateBy { it.gedcomId },
        there = database.mediaDao().getAll(incoming).filter { it.gedcomId != null }.associateBy { it.gedcomId },
        fingerprintHere = { "${it.file}|${it.title}" },
        fingerprintThere = { "${it.file}|${it.title}" },
        describeHere = { it.title ?: it.file.orEmpty() },
        describeThere = { it.title ?: it.file.orEmpty() },
    )

    /**
     * The comparison itself, once both sides are keyed by cross-reference id.
     *
     * Kept generic because the rule is the same for every record type, and writing it five
     * times is five chances to write it differently.
     */
    private fun <T> diff(
        type: RecordType,
        here: Map<String?, T>,
        there: Map<String?, T>,
        fingerprintHere: (T) -> String,
        fingerprintThere: (T) -> String,
        describeHere: (T) -> String,
        describeThere: (T) -> String,
    ): List<TreeDifference> = buildList {
        there.forEach { (gedcomId, incoming) ->
            if (gedcomId == null) return@forEach
            val local = here[gedcomId]
            when {
                local == null -> add(
                    TreeDifference(
                        kind = TreeDifference.Kind.ADDED,
                        recordType = type,
                        gedcomId = gedcomId,
                        incomingSummary = describeThere(incoming),
                    ),
                )

                fingerprintHere(local) != fingerprintThere(incoming) -> add(
                    TreeDifference(
                        kind = TreeDifference.Kind.CHANGED,
                        recordType = type,
                        gedcomId = gedcomId,
                        localSummary = describeHere(local),
                        incomingSummary = describeThere(incoming),
                    ),
                )
            }
        }

        here.forEach { (gedcomId, local) ->
            if (gedcomId != null && gedcomId !in there) {
                add(
                    TreeDifference(
                        kind = TreeDifference.Kind.REMOVED,
                        recordType = type,
                        gedcomId = gedcomId,
                        localSummary = describeHere(local),
                    ),
                )
            }
        }
    }

    private companion object {
        const val SUMMARY_LENGTH = 80
    }
}

private fun String.collapseSpaces(): String = trim().replace(Regex("\\s+"), " ")
