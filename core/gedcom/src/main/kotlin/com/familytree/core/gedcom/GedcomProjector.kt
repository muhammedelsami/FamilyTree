package com.familytree.core.gedcom

import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.entity.AddressEntity
import com.familytree.core.database.entity.EventEntity
import com.familytree.core.database.entity.ExtensionEntity
import com.familytree.core.database.entity.MediaEntity
import com.familytree.core.database.entity.NoteEntity
import com.familytree.core.database.entity.PersonNameEntity
import com.familytree.core.database.entity.SourceCitationEntity
import com.familytree.core.model.MemberRole
import com.familytree.core.model.OwnerType
import com.familytree.core.model.RecordType
import com.familytree.core.model.Sex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.Address
import org.folg.gedcom.model.Change
import org.folg.gedcom.model.CharacterSet
import org.folg.gedcom.model.ChildRef
import org.folg.gedcom.model.DateTime
import org.folg.gedcom.model.EventFact
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.GedcomTag
import org.folg.gedcom.model.GedcomVersion
import org.folg.gedcom.model.Generator
import org.folg.gedcom.model.Header
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.MediaRef
import org.folg.gedcom.model.Name
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.NoteRef
import org.folg.gedcom.model.ParentFamilyRef
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.RepositoryRef
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer
import org.folg.gedcom.model.SpouseFamilyRef
import org.folg.gedcom.model.SpouseRef
import org.folg.gedcom.model.Submitter
import org.folg.gedcom.parser.ModelParser
import javax.inject.Inject

/**
 * Rebuilds a full in-memory GEDCOM model from Room.
 *
 * Three very different features need the whole tree as one object graph — the diagram
 * layout engine, GEDCOM export, and tree merging — so materialising it lives here once
 * rather than three times.
 *
 * The projection is the exact inverse of [GedcomImporter]: typed rows go back to typed
 * tags, and the `extensions` table goes back to the vendor tags it captured, which is
 * what lets a round trip come out unchanged.
 */
class GedcomProjector @Inject constructor(
    private val database: FamilyTreeDatabase,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun project(treeId: Long): Gedcom = withContext(ioDispatcher) {
        val data = loadTree(treeId)
        assemble(data)
    }

    // --- loading ----------------------------------------------------------------------

    private suspend fun loadTree(treeId: Long) = TreeData(
        treeId = treeId,
        header = database.treeDao().getHeader(treeId),
        people = database.personDao().getAll(treeId),
        names = database.personDao().getAllNames(treeId),
        families = database.familyDao().getAll(treeId),
        memberships = database.familyDao().getAllMemberships(treeId),
        events = database.eventDao().getAll(treeId),
        addresses = database.eventDao().getAllAddresses(treeId).associateBy { it.id },
        notes = database.noteDao().getAll(treeId),
        noteLinks = database.noteDao().getAllLinks(treeId),
        media = database.mediaDao().getAll(treeId),
        mediaLinks = database.mediaDao().getAllLinks(treeId),
        sources = database.sourceDao().getAll(treeId),
        citations = database.sourceDao().getAllCitations(treeId),
        repositories = database.repositoryDao().getAll(treeId),
        repositoryRefs = database.repositoryDao().getAllRefs(treeId),
        submitters = database.submitterDao().getAll(treeId),
        extensions = database.extensionDao().getAll(treeId),
    )

    // --- assembly ---------------------------------------------------------------------

    private fun assemble(data: TreeData): Gedcom {
        val gedcom = Gedcom()
        val ids = IdAssigner(data)

        // Records first, so anything that points at them can be resolved afterwards.
        val notesById = data.notes.associate { entity ->
            entity.id to Note().apply {
                id = ids.noteId(entity)
                value = entity.value
                rin = entity.rin
                change = entity.changeDate.toChange()
            }
        }
        val mediaById = data.media.associate { entity -> entity.id to entity.toModel(ids) }
        val sourcesById = data.sources.associate { entity ->
            entity.id to Source().apply {
                id = ids.sourceId(entity)
                title = entity.title
                author = entity.author
                abbreviation = entity.abbreviation
                publicationFacts = entity.publication
                text = entity.text
                date = entity.date
                callNumber = entity.callNumber
                mediaType = entity.mediaType
                typeTag = entity.typeTag ?: entity.mediaType?.let { "TYPE" }
                referenceNumber = entity.referenceNumber
                rin = entity.rin
                uid = entity.uid
                uidTag = entity.uidTag ?: entity.uid?.let { "_UID" }
                change = entity.changeDate.toChange()
            }
        }
        val repositoriesById = data.repositories.associate { entity ->
            entity.id to Repository().apply {
                id = ids.repositoryId(entity)
                name = entity.name
                // GedcomWriter dereferences a record's value without a null check, so a
                // repository that legitimately has none would crash the export.
                value = entity.value.orEmpty()
                address = data.addresses[entity.addressId]?.toModel()
                phone = entity.phone
                fax = entity.fax
                www = entity.www
                wwwTag = entity.wwwTag ?: entity.www?.let { "WWW" }
                email = entity.email
                emailTag = entity.emailTag ?: entity.email?.let { "EMAIL" }
                rin = entity.rin
                change = entity.changeDate.toChange()
            }
        }
        val submittersById = data.submitters.associate { entity ->
            entity.id to Submitter().apply {
                id = ids.submitterId(entity)
                name = entity.name
                value = entity.value.orEmpty()
                address = data.addresses[entity.addressId]?.toModel()
                phone = entity.phone
                fax = entity.fax
                www = entity.www
                wwwTag = entity.wwwTag ?: entity.www?.let { "WWW" }
                email = entity.email
                emailTag = entity.emailTag ?: entity.email?.let { "EMAIL" }
                language = entity.language
                rin = entity.rin
                change = entity.changeDate.toChange()
            }
        }

        val attach = Attacher(data, ids, notesById, mediaById, sourcesById)

        val peopleById = data.people.associate { entity ->
            entity.id to Person().apply {
                id = ids.personId(entity)
                uid = entity.uid
                uidTag = entity.uidTag
                rin = entity.rin
                entity.referenceNumbers?.lineSequence()?.filter { it.isNotBlank() }
                    ?.forEach { addReferenceNumber(it) }
                address = data.addresses[entity.addressId]?.toModel()
                phone = entity.phone
                fax = entity.fax
                email = entity.email
                // A null tag with a non-null value makes GedcomWriter dereference null,
                // so the standard spelling stands in when the source did not record one.
                emailTag = entity.emailTag ?: entity.email?.let { "EMAIL" }
                www = entity.www
                wwwTag = entity.wwwTag ?: entity.www?.let { "WWW" }
                change = entity.changeDate.toChange()
                data.namesByPerson[entity.id].orEmpty()
                    .sortedBy { it.position }
                    .forEach { addName(it.toModel(attach)) }
                // Sex is a column here but a fact in the file, so it is written back first.
                if (entity.sex != Sex.NONE) {
                    addEventFact(EventFact().apply { tag = "SEX"; value = entity.sex.gedcomValue })
                }
                attach.events(OwnerType.PERSON, entity.id).forEach { addEventFact(it) }
                attach.all(this, OwnerType.PERSON, entity.id)
            }
        }

        val familiesById = data.families.associate { entity ->
            entity.id to Family().apply {
                id = ids.familyId(entity)
                uid = entity.uid
                uidTag = entity.uidTag
                rin = entity.rin
                entity.referenceNumbers?.lineSequence()?.filter { it.isNotBlank() }
                    ?.forEach { addReferenceNumber(it) }
                change = entity.changeDate.toChange()
                attach.events(OwnerType.FAMILY, entity.id).forEach { addEventFact(it) }
                attach.all(this, OwnerType.FAMILY, entity.id)
            }
        }

        // Both directions of every membership are regenerated from the single stored row.
        data.memberships.sortedBy { it.position }.forEach { membership ->
            val family = familiesById[membership.familyId] ?: return@forEach
            val person = peopleById[membership.personId] ?: return@forEach
            val personRef = person.id
            val familyRef = family.id
            when (membership.role) {
                MemberRole.HUSBAND -> {
                    family.addHusband(SpouseRef().apply { ref = personRef })
                    person.addSpouseFamilyRef(SpouseFamilyRef().apply { ref = familyRef })
                }
                MemberRole.WIFE -> {
                    family.addWife(SpouseRef().apply { ref = personRef })
                    person.addSpouseFamilyRef(SpouseFamilyRef().apply { ref = familyRef })
                }
                MemberRole.CHILD -> {
                    family.addChild(ChildRef().apply { ref = personRef })
                    person.addParentFamilyRef(
                        ParentFamilyRef().apply {
                            ref = familyRef
                            relationshipType = membership.pedigree?.gedcomValue
                        },
                    )
                }
            }
        }

        // Details of the standalone records, now that everything they point at exists.
        notesById.forEach { (rowId, note) ->
            attach.citations(OwnerType.NOTE, rowId).forEach { note.addSourceCitation(it) }
            attach.extensions(note, OwnerType.NOTE, rowId)
        }
        mediaById.forEach { (rowId, media) -> attach.all(media, OwnerType.MEDIA, rowId) }
        sourcesById.forEach { (rowId, source) ->
            data.repositoryRefs.firstOrNull { it.sourceId == rowId }?.let { refEntity ->
                source.repositoryRef = RepositoryRef().apply {
                    ref = refEntity.repositoryId?.let { repositoriesById[it]?.id }
                    value = refEntity.value
                    callNumber = refEntity.callNumber
                    mediaType = refEntity.mediaType
                    attach.all(this, OwnerType.REPOSITORY_REF, refEntity.id)
                }
            }
            attach.all(source, OwnerType.SOURCE, rowId)
        }
        repositoriesById.forEach { (rowId, repository) ->
            attach.all(repository, OwnerType.REPOSITORY, rowId)
        }
        submittersById.forEach { (rowId, submitter) ->
            attach.all(submitter, OwnerType.SUBMITTER, rowId)
        }

        gedcom.people = data.people.mapNotNull { peopleById[it.id] }
        gedcom.families = data.families.mapNotNull { familiesById[it.id] }
        gedcom.notes = data.notes.filter { it.gedcomId != null }.mapNotNull { notesById[it.id] }
        gedcom.media = data.media.filter { it.gedcomId != null }.mapNotNull { mediaById[it.id] }
        gedcom.sources = data.sources.mapNotNull { sourcesById[it.id] }
        gedcom.repositories = data.repositories.mapNotNull { repositoriesById[it.id] }
        gedcom.submitters = data.submitters.mapNotNull { submittersById[it.id] }
        // Header-owned tags are attached to the header only. Attaching them to the
        // Gedcom root as well would emit each one twice, and a re-import would then
        // see two copies of tags like `_ROOT`.
        gedcom.header = buildHeader(data, submittersById, attach)
        gedcom.createIndexes()
        return gedcom
    }

    private fun buildHeader(
        data: TreeData,
        submittersById: Map<Long, Submitter>,
        attach: Attacher,
    ): Header {
        val entity = data.header
        return Header().apply {
            generator = Generator().apply {
                value = entity?.generatorValue ?: GENERATOR_VALUE
                name = entity?.generatorName ?: GENERATOR_NAME
                version = entity?.generatorVersion
            }
            destination = entity?.destination
            dateTime = entity?.dateTime?.let { DateTime().apply { value = it } }
            submitterRef = entity?.submitterId?.let { submittersById[it]?.id }
            file = entity?.file
            copyright = entity?.copyright
            gedcomVersion = GedcomVersion().apply {
                version = entity?.gedcomVersion ?: "5.5.1"
                form = entity?.gedcomForm ?: "LINEAGE-LINKED"
            }
            characterSet = CharacterSet().apply { value = entity?.characterSet ?: "UTF-8" }
            language = entity?.language
            attach.extensions(this, OwnerType.HEADER, data.treeId)
        }
    }

    private companion object {
        const val GENERATOR_VALUE = "FAMILY_TREE"
        const val GENERATOR_NAME = "FamilyTree"
    }
}

// --- supporting types -------------------------------------------------------------------

internal class TreeData(
    val treeId: Long,
    val header: com.familytree.core.database.entity.HeaderEntity?,
    val people: List<com.familytree.core.database.entity.PersonEntity>,
    names: List<PersonNameEntity>,
    val families: List<com.familytree.core.database.entity.FamilyEntity>,
    val memberships: List<com.familytree.core.database.entity.FamilyMemberEntity>,
    events: List<EventEntity>,
    val addresses: Map<Long, AddressEntity>,
    val notes: List<NoteEntity>,
    noteLinks: List<com.familytree.core.database.entity.NoteLinkEntity>,
    val media: List<MediaEntity>,
    mediaLinks: List<com.familytree.core.database.entity.MediaLinkEntity>,
    val sources: List<com.familytree.core.database.entity.SourceEntity>,
    citations: List<SourceCitationEntity>,
    val repositories: List<com.familytree.core.database.entity.RepositoryEntity>,
    val repositoryRefs: List<com.familytree.core.database.entity.RepositoryRefEntity>,
    val submitters: List<com.familytree.core.database.entity.SubmitterEntity>,
    extensions: List<ExtensionEntity>,
) {
    val namesByPerson = names.groupBy { it.personId }
    val eventsByOwner = events.groupBy { it.ownerType to it.ownerId }
    val noteLinksByOwner = noteLinks.groupBy { it.ownerType to it.ownerId }
    val mediaLinksByOwner = mediaLinks.groupBy { it.ownerType to it.ownerId }
    val citationsByOwner = citations.groupBy { it.ownerType to it.ownerId }
    val extensionsByOwner = extensions.groupBy { it.ownerType to it.ownerId }
    val extensionsByParent = extensions.groupBy { it.parentExtensionId }
}

/**
 * Hands out GEDCOM cross-reference ids.
 *
 * Records created in the app carry no id until they are exported; ids are minted here
 * so they never collide with the ones an imported file already uses.
 */
internal class IdAssigner(data: TreeData) {
    private val used = HashSet<String>()
    private val counters = HashMap<RecordType, Int>()

    init {
        (
            data.people.map { it.gedcomId } + data.families.map { it.gedcomId } +
                data.notes.map { it.gedcomId } + data.media.map { it.gedcomId } +
                data.sources.map { it.gedcomId } + data.repositories.map { it.gedcomId } +
                data.submitters.map { it.gedcomId }
            ).filterNotNull().forEach { used += it }
    }

    private fun next(type: RecordType): String {
        var counter = counters.getOrDefault(type, 0)
        var candidate: String
        do {
            counter++
            candidate = "${type.idPrefix}$counter"
        } while (!used.add(candidate))
        counters[type] = counter
        return candidate
    }

    fun personId(entity: com.familytree.core.database.entity.PersonEntity) =
        entity.gedcomId ?: next(RecordType.PERSON)

    fun familyId(entity: com.familytree.core.database.entity.FamilyEntity) =
        entity.gedcomId ?: next(RecordType.FAMILY)

    fun noteId(entity: NoteEntity) = entity.gedcomId
    fun mediaId(entity: MediaEntity) = entity.gedcomId

    fun sourceId(entity: com.familytree.core.database.entity.SourceEntity) =
        entity.gedcomId ?: next(RecordType.SOURCE)

    fun repositoryId(entity: com.familytree.core.database.entity.RepositoryEntity) =
        entity.gedcomId ?: next(RecordType.REPOSITORY)

    fun submitterId(entity: com.familytree.core.database.entity.SubmitterEntity) =
        entity.gedcomId ?: next(RecordType.SUBMITTER)
}

/** Rebuilds the attachments hanging off a record: events, notes, media, citations, raw tags. */
internal class Attacher(
    private val data: TreeData,
    private val ids: IdAssigner,
    private val notesById: Map<Long, Note>,
    private val mediaById: Map<Long, Media>,
    private val sourcesById: Map<Long, Source>,
) {

    fun events(ownerType: OwnerType, ownerId: Long): List<EventFact> =
        data.eventsByOwner[ownerType to ownerId].orEmpty()
            .sortedBy { it.position }
            .map { entity ->
                EventFact().apply {
                    tag = entity.tag
                    value = entity.value
                    type = entity.type
                    date = entity.date
                    place = entity.place
                    cause = entity.cause
                    address = data.addresses[entity.addressId]?.toModel()
                    phone = entity.phone
                    fax = entity.fax
                    www = entity.www
                    email = entity.email
                    rin = entity.rin
                    uid = entity.uid
                    uidTag = entity.uidTag ?: entity.uid?.let { "_UID" }
                    emailTag = entity.emailTag ?: entity.email?.let { "EMAIL" }
                    wwwTag = entity.wwwTag ?: entity.www?.let { "WWW" }
                    all(this, OwnerType.EVENT, entity.id)
                }
            }

    fun citations(ownerType: OwnerType, ownerId: Long): List<SourceCitation> =
        data.citationsByOwner[ownerType to ownerId].orEmpty()
            .sortedBy { it.position }
            .map { entity ->
                SourceCitation().apply {
                    ref = entity.sourceId?.let { sourcesById[it]?.id }
                    value = entity.value
                    page = entity.page
                    date = entity.date
                    text = entity.text
                    quality = entity.quality
                    all(this, OwnerType.SOURCE_CITATION, entity.id)
                }
            }

    /** Attaches everything a container can hold, skipping what its type does not support. */
    fun all(container: ExtensionContainer, ownerType: OwnerType, ownerId: Long) {
        if (container is NoteContainer) {
            data.noteLinksByOwner[ownerType to ownerId].orEmpty()
                .sortedBy { it.position }
                .forEach { link ->
                    val note = notesById[link.noteId] ?: return@forEach
                    if (note.id != null) {
                        container.addNoteRef(NoteRef().apply { ref = note.id })
                    } else {
                        container.addNote(note)
                    }
                }
        }
        if (container is MediaContainer) {
            data.mediaLinksByOwner[ownerType to ownerId].orEmpty()
                .sortedBy { it.position }
                .forEach { link ->
                    val media = mediaById[link.mediaId] ?: return@forEach
                    if (media.id != null) {
                        container.addMediaRef(MediaRef().apply { ref = media.id })
                    } else {
                        container.addMedia(media)
                    }
                }
        }
        if (container is SourceCitationContainer) {
            citations(ownerType, ownerId).forEach { container.addSourceCitation(it) }
        }
        extensions(container, ownerType, ownerId)
    }

    /** Puts the unmapped tags back exactly where they came from. */
    fun extensions(container: ExtensionContainer, ownerType: OwnerType, ownerId: Long) {
        val roots = data.extensionsByOwner[ownerType to ownerId].orEmpty()
            .filter { it.parentExtensionId == null }
            .sortedBy { it.position }
        if (roots.isEmpty()) return
        container.putExtension(ModelParser.MORE_TAGS_EXTENSION_KEY, roots.map { it.toTag() })
    }

    private fun ExtensionEntity.toTag(): GedcomTag {
        // The constructor takes (id, tag, ref) — the value is a separate field.
        val tag = GedcomTag(null, this.tag, ref)
        tag.value = value
        val children = data.extensionsByParent[id].orEmpty().sortedBy { it.position }
        // GedcomWriter walks getChildren() without a null check, and the constructor
        // leaves it null, so a leaf tag needs an explicit empty list.
        tag.children = mutableListOf()
        children.forEach { tag.addChild(it.toTag()) }
        return tag
    }

    fun nameModel(entity: PersonNameEntity): Name = entity.toModel(this)
}

// --- entity to model ---------------------------------------------------------------------

internal fun PersonNameEntity.toModel(attach: Attacher): Name = Name().apply {
    // Other genealogy programs read the slashed value, not the pieces, so it is
    // regenerated whenever it is missing.
    value = this@toModel.value?.takeIf { it.isNotBlank() } ?: buildSlashedValue()
    prefix = this@toModel.prefix
    given = this@toModel.given
    surnamePrefix = this@toModel.surnamePrefix
    surname = this@toModel.surname
    suffix = this@toModel.suffix
    nickname = this@toModel.nickname
    type = this@toModel.type
    typeTag = this@toModel.typeTag ?: this@toModel.type?.let { "TYPE" }
    marriedName = this@toModel.marriedName
    marriedNameTag = this@toModel.marriedNameTag ?: this@toModel.marriedName?.let { "_MARRNM" }
    aka = alsoKnownAs
    akaTag = alsoKnownAsTag ?: alsoKnownAs?.let { "_AKA" }
    fone = phonetic
    romn = romanised
    attach.all(this, OwnerType.NAME, id)
}

private fun PersonNameEntity.buildSlashedValue(): String? {
    if (prefix == null && given == null && surname == null && suffix == null) return null
    return buildString {
        prefix?.takeIf { it.isNotBlank() }?.let { append(it) }
        given?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(' '); append(it) }
        surname?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(' ')
            append('/').append(it).append('/')
        }
        suffix?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(' '); append(it) }
    }.trim().ifBlank { null }
}

internal fun MediaEntity.toModel(ids: IdAssigner): Media = Media().apply {
    id = ids.mediaId(this@toModel)
    file = this@toModel.file
    // Files without a FILE tag are dropped by other programs on import.
    fileTag = this@toModel.fileTag ?: "FILE"
    title = this@toModel.title
    format = this@toModel.format
    type = mediaType
    primary = if (isPrimary) "Y" else null
    change = changeDate.toChange()
}

internal fun AddressEntity.toModel(): Address = Address().apply {
    value = this@toModel.value
    addressLine1 = line1
    addressLine2 = line2
    addressLine3 = line3
    city = this@toModel.city
    state = this@toModel.state
    postalCode = this@toModel.postalCode
    country = this@toModel.country
    name = this@toModel.name
}

internal fun String?.toChange(): Change? {
    if (this == null) return null
    return Change().apply { dateTime = DateTime().apply { value = this@toChange } }
}
