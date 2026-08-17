package com.familytree.core.data.repository

import androidx.room.withTransaction
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.data.mapper.buildPersonSummary
import com.familytree.core.data.mapper.toDomain
import com.familytree.core.data.mapper.toEntity
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.dao.EventDao
import com.familytree.core.database.dao.FamilyDao
import com.familytree.core.database.dao.ExtensionDao
import com.familytree.core.database.dao.MaintenanceDao
import com.familytree.core.database.dao.MediaDao
import com.familytree.core.database.dao.NoteDao
import com.familytree.core.database.dao.PersonDao
import com.familytree.core.database.dao.SourceDao
import com.familytree.core.database.dao.TreeDao
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.model.Event
import com.familytree.core.model.OwnerType
import com.familytree.core.model.Person
import com.familytree.core.model.PersonDetails
import com.familytree.core.model.PersonName
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.RecordType
import com.familytree.core.model.TreeSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinLocalDate
import javax.inject.Inject

class PersonRepositoryImpl @Inject constructor(
    private val database: FamilyTreeDatabase,
    private val personDao: PersonDao,
    private val eventDao: EventDao,
    private val familyDao: FamilyDao,
    private val treeDao: TreeDao,
    private val noteDao: NoteDao,
    private val mediaDao: MediaDao,
    private val sourceDao: SourceDao,
    private val extensionDao: ExtensionDao,
    private val maintenanceDao: MaintenanceDao,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PersonRepository {

    override fun observePeople(treeId: Long): Flow<List<Person>> =
        personDao.observeAll(treeId).map { list -> list.map { it.toDomain() } }

    /**
     * Builds the whole list from five tree-wide queries rather than a handful per
     * person: a 5,000-person tree would otherwise issue tens of thousands of queries
     * to render one screen.
     */
    override fun observePersonSummaries(treeId: Long): Flow<List<PersonSummary>> =
        combine(
            personDao.observeAll(treeId),
            personDao.observeAllNames(treeId),
            eventDao.observeAllForTree(treeId),
            familyDao.observeAllMemberships(treeId),
            mediaDao.observePortraitLinks(treeId),
        ) { people, names, events, memberships, portraits ->
            val namesByPerson = names.groupBy { it.personId }
            val eventsByPerson = events
                .filter { it.ownerType == OwnerType.PERSON }
                .groupBy { it.ownerId }
            // How many other people share a family with this one.
            val familyIdsByPerson = memberships.groupBy({ it.personId }, { it.familyId })
            val membersByFamily = memberships.groupBy { it.familyId }
            val portraitByPerson = portraits.associate { it.ownerId to it.mediaId }

            val today = java.time.LocalDate.now().toKotlinLocalDate()
            val lifeSpan = treeDao.get(treeId)?.lifeSpan ?: TreeSettings.DEFAULT_LIFE_SPAN

            people.map { person ->
                val relatives = familyIdsByPerson[person.id].orEmpty()
                    .flatMap { membersByFamily[it].orEmpty() }
                    .map { it.personId }
                    .toSet()
                    .count { it != person.id }

                buildPersonSummary(
                    person = person,
                    names = namesByPerson[person.id].orEmpty(),
                    events = eventsByPerson[person.id].orEmpty(),
                    relativeCount = relatives,
                    portraitMediaId = portraitByPerson[person.id],
                    today = today,
                    lifeSpan = lifeSpan,
                )
            }
        }

    override fun observePerson(personId: Long): Flow<Person?> =
        personDao.observe(personId).map { it?.toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePersonDetails(personId: Long): Flow<PersonDetails?> =
        personDao.observe(personId).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                combine(
                    personDao.observeNames(personId),
                    eventDao.observeFor(OwnerType.PERSON, personId),
                    noteDao.observeFor(OwnerType.PERSON, personId),
                    mediaDao.observeFor(OwnerType.PERSON, personId),
                    sourceDao.observeCitationsFor(OwnerType.PERSON, personId),
                ) { names, events, notes, media, citations ->
                    PersonDetails(
                        person = entity.toDomain(),
                        names = names.map { it.toDomain() },
                        events = events.map { it.toDomain() },
                        notes = notes.map { it.toDomain() },
                        media = media.map { it.toDomain() },
                        citations = citations.map { it.toDomain() },
                    )
                }
            }
        }

    override suspend fun getPerson(personId: Long): Person? = withContext(ioDispatcher) {
        personDao.get(personId)?.toDomain()
    }

    override suspend fun getNames(personId: Long): List<PersonName> = withContext(ioDispatcher) {
        personDao.getNames(personId).map { it.toDomain() }
    }

    override suspend fun getEvents(personId: Long): List<Event> = withContext(ioDispatcher) {
        eventDao.getFor(OwnerType.PERSON, personId).map { it.toDomain() }
    }

    override suspend fun createPerson(person: Person, names: List<PersonName>): Long =
        withContext(ioDispatcher) {
            database.withTransaction {
                // A cross-reference id is assigned at creation, not deferred to export.
                // The diagram engine and every GEDCOM writer address records by it, so a
                // person without one is invisible to them the moment they are created.
                val gedcomId = person.gedcomId ?: nextGedcomId(person.treeId)
                val personId = personDao.insert(person.toEntity().copy(gedcomId = gedcomId))
                if (names.isNotEmpty()) {
                    personDao.insertNames(
                        names.mapIndexed { index, name ->
                            name.copy(personId = personId, position = index).toEntity()
                        },
                    )
                }
                personId
            }
        }

    private suspend fun nextGedcomId(treeId: Long): String {
        val prefix = RecordType.PERSON.idPrefix
        return "$prefix${maintenanceDao.nextPersonNumber(treeId, prefix)}"
    }

    override suspend fun updatePerson(person: Person) = withContext(ioDispatcher) {
        personDao.update(person.toEntity().copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deletePerson(personId: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            // Names and family memberships cascade through their foreign keys; the
            // polymorphic attachments cannot, so they are cleared explicitly here.
            eventDao.deleteFor(OwnerType.PERSON, personId)
            noteDao.unlinkAllFrom(OwnerType.PERSON, personId)
            mediaDao.unlinkAllFrom(OwnerType.PERSON, personId)
            sourceDao.deleteCitationsFor(OwnerType.PERSON, personId)
            extensionDao.deleteFor(OwnerType.PERSON, personId)
            personDao.deleteById(personId)
        }
    }

    override suspend fun upsertName(name: PersonName): Long = withContext(ioDispatcher) {
        personDao.upsertName(name.toEntity())
    }

    override suspend fun deleteName(nameId: Long) = withContext(ioDispatcher) {
        personDao.deleteNameById(nameId)
    }

}
