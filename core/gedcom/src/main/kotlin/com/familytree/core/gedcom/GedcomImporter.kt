package com.familytree.core.gedcom

import androidx.room.withTransaction
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.entity.AddressEntity
import com.familytree.core.database.entity.EventEntity
import com.familytree.core.database.entity.ExtensionEntity
import com.familytree.core.database.entity.FamilyEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.database.entity.HeaderEntity
import com.familytree.core.database.entity.MediaEntity
import com.familytree.core.database.entity.MediaLinkEntity
import com.familytree.core.database.entity.NoteEntity
import com.familytree.core.database.entity.NoteLinkEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.PersonNameEntity
import com.familytree.core.database.entity.RepositoryEntity
import com.familytree.core.database.entity.RepositoryRefEntity
import com.familytree.core.database.entity.SourceCitationEntity
import com.familytree.core.database.entity.SourceEntity
import com.familytree.core.database.entity.SubmitterEntity
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.model.MemberRole
import com.familytree.core.model.OwnerType
import com.familytree.core.model.Pedigree
import com.familytree.core.model.Sex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.Address
import org.folg.gedcom.model.EventFact
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.GedcomTag
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.Name
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.RepositoryRef
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer
import org.folg.gedcom.model.Submitter
import org.folg.gedcom.parser.ModelParser
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/** Raised when a file cannot be read as GEDCOM at all. */
class InvalidGedcomException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads a GEDCOM file into Room.
 *
 * Every tag the app models becomes a typed row; **everything else is preserved in the
 * `extensions` table** with its nesting and order intact, which is what makes a
 * subsequent export byte-comparable with the original.
 *
 * The whole file lands in one transaction: a half-imported tree is worse than none.
 */
class GedcomImporter @Inject constructor(
    private val database: FamilyTreeDatabase,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun import(input: InputStream, title: String): Long = withContext(ioDispatcher) {
        // ModelParser only accepts a File, so the stream is staged in the cache first.
        val staged = File.createTempFile("import", ".ged")
        try {
            staged.outputStream().use { input.copyTo(it) }
            importFile(staged, title)
        } finally {
            staged.delete()
        }
    }

    suspend fun importFile(file: File, title: String): Long = withContext(ioDispatcher) {
        val gedcom = try {
            ModelParser().parseGedcom(file)
        } catch (exception: Exception) {
            throw InvalidGedcomException("This file could not be read as GEDCOM.", exception)
        } ?: throw InvalidGedcomException("This file could not be read as GEDCOM.")

        if (gedcom.header == null && gedcom.people.isEmpty() && gedcom.families.isEmpty()) {
            throw InvalidGedcomException("This file contains no GEDCOM records.")
        }
        database.withTransaction { writeTree(gedcom, title) }
    }

    private suspend fun writeTree(gedcom: Gedcom, title: String): Long {
        val now = System.currentTimeMillis()
        val treeId = database.treeDao().insert(
            TreeEntity(title = title, createdAt = now, updatedAt = now, sortOrder = Int.MAX_VALUE),
        )
        val context = ImportContext(treeId)

        writeTopLevelRecords(gedcom, context)
        writeHeader(gedcom, context)
        writePeople(gedcom, context)
        writeFamilies(gedcom, context)
        writeMembershipsFromPeople(gedcom, context)
        writeRecordDetails(gedcom, context)

        // Root-level tags the parser could not place anywhere else.
        writeExtensions(gedcom, OwnerType.HEADER, treeId, context)

        database.treeDao().setRootPerson(treeId, chooseRootPerson(gedcom, context))
        database.treeDao().refreshCounters(treeId, generations = 0, now = now)
        return treeId
    }

    // --- top-level records ------------------------------------------------------------

    /**
     * Inserts the records other records point at, before anything that references them,
     * so cross-references can be resolved in a single pass afterwards.
     */
    private suspend fun writeTopLevelRecords(gedcom: Gedcom, context: ImportContext) {
        val treeId = context.treeId

        gedcom.people.forEach { person ->
            val id = database.personDao().insert(
                PersonEntity(
                    treeId = treeId,
                    gedcomId = person.id,
                    sex = person.readSex(),
                    sortOrder = context.personIds.size,
                    // The parser lifts `_UID` and `REFN` onto typed fields rather than
                    // leaving them among the raw tags, so they are carried across here.
                    uid = person.uid,
                    uidTag = person.uidTag,
                    rin = person.rin,
                    referenceNumbers = person.referenceNumbers?.joinToString("\n")?.ifBlank { null },
                    addressId = writeAddress(person.address, context),
                    phone = person.phone,
                    fax = person.fax,
                    email = person.email,
                    emailTag = person.emailTag,
                    www = person.www,
                    wwwTag = person.wwwTag,
                    changeDate = person.change?.dateTime?.value,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            context.personIds[person.id] = id
        }

        gedcom.families.forEach { family ->
            val id = database.familyDao().insert(
                FamilyEntity(
                    treeId = treeId,
                    gedcomId = family.id,
                    sortOrder = context.familyIds.size,
                    uid = family.uid,
                    uidTag = family.uidTag,
                    rin = family.rin,
                    referenceNumbers = family.referenceNumbers?.joinToString("\n")?.ifBlank { null },
                    changeDate = family.change?.dateTime?.value,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            context.familyIds[family.id] = id
        }

        gedcom.notes.forEach { note ->
            val id = database.noteDao().insert(
                NoteEntity(
                    treeId = treeId,
                    gedcomId = note.id,
                    value = note.value.orEmpty(),
                    rin = note.rin,
                    changeDate = note.change?.dateTime?.value,
                ),
            )
            context.noteIds[note.id] = id
        }

        gedcom.media.forEach { media ->
            val id = database.mediaDao().insert(media.toEntity(treeId))
            context.mediaIds[media.id] = id
        }

        gedcom.sources.forEach { source ->
            val id = database.sourceDao().insert(
                SourceEntity(
                    treeId = treeId,
                    gedcomId = source.id,
                    title = source.title,
                    author = source.author,
                    abbreviation = source.abbreviation,
                    publication = source.publicationFacts,
                    text = source.text,
                    date = source.date,
                    callNumber = source.callNumber,
                    mediaType = source.mediaType,
                    typeTag = source.typeTag,
                    uidTag = source.uidTag,
                    referenceNumber = source.referenceNumber,
                    rin = source.rin,
                    uid = source.uid,
                    changeDate = source.change?.dateTime?.value,
                ),
            )
            context.sourceIds[source.id] = id
        }

        gedcom.repositories.forEach { repository ->
            val addressId = writeAddress(repository.address, context)
            val id = database.repositoryDao().insert(
                RepositoryEntity(
                    treeId = treeId,
                    gedcomId = repository.id,
                    name = repository.name,
                    value = repository.value,
                    addressId = addressId,
                    phone = repository.phone,
                    fax = repository.fax,
                    www = repository.www,
                    wwwTag = repository.wwwTag,
                    email = repository.email,
                    emailTag = repository.emailTag,
                    rin = repository.rin,
                    changeDate = repository.change?.dateTime?.value,
                ),
            )
            context.repositoryIds[repository.id] = id
        }

        gedcom.submitters.forEach { submitter ->
            val addressId = writeAddress(submitter.address, context)
            val id = database.submitterDao().insert(
                SubmitterEntity(
                    treeId = treeId,
                    gedcomId = submitter.id,
                    name = submitter.name,
                    value = submitter.value,
                    addressId = addressId,
                    phone = submitter.phone,
                    fax = submitter.fax,
                    www = submitter.www,
                    wwwTag = submitter.wwwTag,
                    email = submitter.email,
                    emailTag = submitter.emailTag,
                    language = submitter.language,
                    rin = submitter.rin,
                    changeDate = submitter.change?.dateTime?.value,
                ),
            )
            context.submitterIds[submitter.id] = id
        }
    }

    private suspend fun writeHeader(gedcom: Gedcom, context: ImportContext) {
        val header = gedcom.header ?: return
        database.treeDao().upsertHeader(
            HeaderEntity(
                treeId = context.treeId,
                generatorValue = header.generator?.value,
                generatorName = header.generator?.name,
                generatorVersion = header.generator?.version,
                corporation = header.generator?.generatorCorporation?.value,
                destination = header.destination,
                dateTime = header.dateTime?.value,
                submitterId = header.submitterRef?.let { context.submitterIds[it] },
                file = header.file,
                copyright = header.copyright,
                gedcomVersion = header.gedcomVersion?.version,
                gedcomForm = header.gedcomVersion?.form,
                characterSet = header.characterSet?.value,
                language = header.language,
            ),
        )
        writeExtensions(header, OwnerType.HEADER, context.treeId, context)
    }

    // --- people and families ----------------------------------------------------------

    private suspend fun writePeople(gedcom: Gedcom, context: ImportContext) {
        gedcom.people.forEach { person ->
            val personId = context.personIds[person.id] ?: return@forEach

            person.names.forEachIndexed { index, name ->
                val nameId = database.personDao().upsertName(name.toEntity(personId, index))
                writeAttachments(name, OwnerType.NAME, nameId, context)
            }
            writeEvents(person.eventsFacts, OwnerType.PERSON, personId, context)
            writeAttachments(person, OwnerType.PERSON, personId, context)
        }
    }

    private suspend fun writeFamilies(gedcom: Gedcom, context: ImportContext) {
        gedcom.families.forEach { family ->
            val familyId = context.familyIds[family.id] ?: return@forEach

            family.husbandRefs.forEachIndexed { index, ref ->
                context.addMember(familyId, ref.ref, MemberRole.HUSBAND, index, ref.preferred != null)
            }
            family.wifeRefs.forEachIndexed { index, ref ->
                context.addMember(familyId, ref.ref, MemberRole.WIFE, index, ref.preferred != null)
            }
            family.childRefs.forEachIndexed { index, ref ->
                context.addMember(
                    familyId = familyId,
                    personGedcomId = ref.ref,
                    role = MemberRole.CHILD,
                    position = index,
                    preferred = ref.preferred != null,
                    fatherRelation = Pedigree.fromGedcom(ref.fatherRelationship?.value),
                    motherRelation = Pedigree.fromGedcom(ref.motherRelationship?.value),
                )
            }
            writeEvents(family.eventsFacts, OwnerType.FAMILY, familyId, context)
            writeAttachments(family, OwnerType.FAMILY, familyId, context)
        }
    }

    /**
     * Folds `INDI.FAMC` / `INDI.FAMS` into the memberships built from the family side.
     *
     * A membership stated by only one of the two sides is exactly the corruption
     * FamilyGem had to detect and repair; taking the union here means an imported tree
     * comes out consistent, and the `PEDI` and `_PRIMARY` details that live only on the
     * person side are not lost.
     */
    private suspend fun writeMembershipsFromPeople(gedcom: Gedcom, context: ImportContext) {
        gedcom.people.forEach { person ->
            val personId = context.personIds[person.id] ?: return@forEach

            person.parentFamilyRefs.forEach { ref ->
                val familyId = context.familyIds[ref.ref] ?: return@forEach
                context.mergeMember(
                    familyId = familyId,
                    personId = personId,
                    role = MemberRole.CHILD,
                    pedigree = Pedigree.fromGedcom(ref.relationshipType),
                    isPrimary = ref.primary != null,
                )
            }
            person.spouseFamilyRefs.forEach { ref ->
                val familyId = context.familyIds[ref.ref] ?: return@forEach
                // Which of the two spouse roles is unknown from this side alone; the
                // family side has already assigned it when both sides agree.
                context.mergeSpouseMember(familyId, personId, person.readSex())
            }
        }
        database.familyDao().insertMembers(context.members.values.toList())
    }

    // --- attachments ------------------------------------------------------------------

    private suspend fun writeEvents(
        events: List<EventFact>,
        ownerType: OwnerType,
        ownerId: Long,
        context: ImportContext,
    ) {
        events.forEachIndexed { index, event ->
            // SEX is a fact in the file but a column on the person here, so it is not
            // duplicated as an event row.
            if (ownerType == OwnerType.PERSON && event.tag.equals("SEX", ignoreCase = true)) return@forEachIndexed

            val addressId = writeAddress(event.address, context)
            val eventId = database.eventDao().insert(
                EventEntity(
                    treeId = context.treeId,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    tag = event.tag.orEmpty().uppercase(),
                    value = event.value,
                    type = event.type,
                    date = event.date,
                    place = event.place,
                    cause = event.cause,
                    addressId = addressId,
                    phone = event.phone,
                    fax = event.fax,
                    www = event.www,
                    email = event.email,
                    rin = event.rin,
                    uid = event.uid,
                    uidTag = event.uidTag,
                    emailTag = event.emailTag,
                    wwwTag = event.wwwTag,
                    position = index,
                ),
            )
            writeAttachments(event, OwnerType.EVENT, eventId, context)
        }
    }

    /** Notes, media, source citations and unmapped tags hanging off any container. */
    private suspend fun writeAttachments(
        container: ExtensionContainer,
        ownerType: OwnerType,
        ownerId: Long,
        context: ImportContext,
    ) {
        if (container is NoteContainer) writeNotes(container, ownerType, ownerId, context)
        if (container is MediaContainer) writeMedia(container, ownerType, ownerId, context)
        if (container is SourceCitationContainer) {
            writeCitations(container.sourceCitations, ownerType, ownerId, context)
        }
        // Note is the odd one out: it carries citations without extending the container.
        if (container is Note) writeCitations(container.sourceCitations, ownerType, ownerId, context)
        writeExtensions(container, ownerType, ownerId, context)
    }

    private suspend fun writeNotes(
        container: NoteContainer,
        ownerType: OwnerType,
        ownerId: Long,
        context: ImportContext,
    ) {
        var position = 0
        container.noteRefs.forEach { ref ->
            val noteId = context.noteIds[ref.ref] ?: return@forEach
            database.noteDao().insertLink(
                NoteLinkEntity(
                    treeId = context.treeId,
                    noteId = noteId,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    position = position++,
                ),
            )
        }
        container.notes.forEach { note ->
            // An inline note belongs to one record only, so it gets no GEDCOM id.
            val noteId = database.noteDao().insert(
                NoteEntity(treeId = context.treeId, value = note.value.orEmpty(), rin = note.rin),
            )
            database.noteDao().insertLink(
                NoteLinkEntity(
                    treeId = context.treeId,
                    noteId = noteId,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    position = position++,
                ),
            )
            writeCitations(note.sourceCitations, OwnerType.NOTE, noteId, context)
            writeExtensions(note, OwnerType.NOTE, noteId, context)
        }
    }

    private suspend fun writeMedia(
        container: MediaContainer,
        ownerType: OwnerType,
        ownerId: Long,
        context: ImportContext,
    ) {
        var position = 0
        container.mediaRefs.forEach { ref ->
            val mediaId = context.mediaIds[ref.ref] ?: return@forEach
            database.mediaDao().insertLink(
                MediaLinkEntity(
                    treeId = context.treeId,
                    mediaId = mediaId,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    position = position++,
                ),
            )
        }
        container.media.forEach { media ->
            val mediaId = database.mediaDao().insert(media.toEntity(context.treeId, inline = true))
            database.mediaDao().insertLink(
                MediaLinkEntity(
                    treeId = context.treeId,
                    mediaId = mediaId,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    position = position++,
                ),
            )
            writeAttachments(media, OwnerType.MEDIA, mediaId, context)
        }
    }

    private suspend fun writeCitations(
        citations: List<SourceCitation>,
        ownerType: OwnerType,
        ownerId: Long,
        context: ImportContext,
    ) {
        citations.forEachIndexed { index, citation ->
            val citationId = database.sourceDao().upsertCitation(
                SourceCitationEntity(
                    treeId = context.treeId,
                    sourceId = citation.ref?.let { context.sourceIds[it] },
                    ownerType = ownerType,
                    ownerId = ownerId,
                    value = citation.value,
                    page = citation.page,
                    date = citation.date,
                    text = citation.text,
                    quality = citation.quality,
                    position = index,
                ),
            )
            writeAttachments(citation, OwnerType.SOURCE_CITATION, citationId, context)
        }
    }

    /** Details of records that reference other top-level records. */
    private suspend fun writeRecordDetails(gedcom: Gedcom, context: ImportContext) {
        gedcom.notes.forEach { note ->
            val noteId = context.noteIds[note.id] ?: return@forEach
            writeCitations(note.sourceCitations, OwnerType.NOTE, noteId, context)
            writeExtensions(note, OwnerType.NOTE, noteId, context)
        }
        gedcom.media.forEach { media ->
            val mediaId = context.mediaIds[media.id] ?: return@forEach
            writeAttachments(media, OwnerType.MEDIA, mediaId, context)
        }
        gedcom.sources.forEach { source ->
            val sourceId = context.sourceIds[source.id] ?: return@forEach
            writeRepositoryRef(source.repositoryRef, sourceId, context)
            writeAttachments(source, OwnerType.SOURCE, sourceId, context)
        }
        gedcom.repositories.forEach { repository ->
            val repositoryId = context.repositoryIds[repository.id] ?: return@forEach
            writeAttachments(repository, OwnerType.REPOSITORY, repositoryId, context)
        }
        gedcom.submitters.forEach { submitter ->
            val submitterId = context.submitterIds[submitter.id] ?: return@forEach
            writeAttachments(submitter, OwnerType.SUBMITTER, submitterId, context)
        }
    }

    private suspend fun writeRepositoryRef(
        ref: RepositoryRef?,
        sourceId: Long,
        context: ImportContext,
    ) {
        if (ref == null) return
        val refId = database.repositoryDao().upsertRef(
            RepositoryRefEntity(
                treeId = context.treeId,
                sourceId = sourceId,
                repositoryId = ref.ref?.let { context.repositoryIds[it] },
                value = ref.value,
                callNumber = ref.callNumber,
                mediaType = ref.mediaType,
            ),
        )
        writeAttachments(ref, OwnerType.REPOSITORY_REF, refId, context)
    }

    private suspend fun writeAddress(address: Address?, context: ImportContext): Long? {
        if (address == null) return null
        return database.eventDao().upsertAddress(
            AddressEntity(
                treeId = context.treeId,
                value = address.value,
                line1 = address.addressLine1,
                line2 = address.addressLine2,
                line3 = address.addressLine3,
                city = address.city,
                state = address.state,
                postalCode = address.postalCode,
                country = address.country,
                name = address.name,
            ),
        )
    }

    // --- unmapped tags ----------------------------------------------------------------

    /**
     * Copies every tag the parser could not map onto a typed field.
     *
     * This is the whole reason a round trip can be lossless: `_UID`, `_MILT`, `_ROOT`,
     * `_APID` and the rest of the vendor zoo survive untouched, nested as deeply as the
     * file nested them.
     */
    private suspend fun writeExtensions(
        container: ExtensionContainer,
        ownerType: OwnerType,
        ownerId: Long,
        context: ImportContext,
    ) {
        @Suppress("UNCHECKED_CAST")
        val tags = container.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) as? List<GedcomTag>
            ?: return
        tags.forEachIndexed { index, tag ->
            writeExtensionTag(tag, ownerType, ownerId, parentId = null, position = index, context = context)
        }
    }

    private suspend fun writeExtensionTag(
        tag: GedcomTag,
        ownerType: OwnerType,
        ownerId: Long,
        parentId: Long?,
        position: Int,
        context: ImportContext,
    ) {
        val id = database.extensionDao().insert(
            ExtensionEntity(
                treeId = context.treeId,
                ownerType = ownerType,
                ownerId = ownerId,
                parentExtensionId = parentId,
                tag = tag.tag.orEmpty(),
                ref = tag.ref,
                value = tag.value,
                position = position,
            ),
        )
        tag.children?.forEachIndexed { index, child ->
            writeExtensionTag(child, ownerType, ownerId, id, index, context)
        }
    }

    // --- root person ------------------------------------------------------------------

    /**
     * Honours the root a file declares before falling back.
     *
     * Family Historian writes `_ROOT` and Ahnenblatt writes `_HOME`; recognising them
     * means an imported tree opens on the person its author considered central.
     */
    private fun chooseRootPerson(gedcom: Gedcom, context: ImportContext): Long? {
        val header = gedcom.header
        if (header != null) {
            @Suppress("UNCHECKED_CAST")
            val headerTags = header.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) as? List<GedcomTag>
            headerTags?.firstOrNull { it.tag == "_ROOT" || it.tag == "_HOME" }
                ?.let { tag -> context.personIds[tag.ref]?.let { return it } }
        }
        val lowestNumbered = gedcom.people
            .filter { it.id != null }
            .minByOrNull { person -> person.id.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
        return context.personIds[lowestNumbered?.id] ?: context.personIds[gedcom.people.firstOrNull()?.id]
    }
}

// --- helpers ---------------------------------------------------------------------------

private fun Person.readSex(): Sex =
    Sex.fromGedcom(eventsFacts.firstOrNull { it.tag.equals("SEX", ignoreCase = true) }?.value)

private fun Name.toEntity(personId: Long, position: Int) = PersonNameEntity(
    personId = personId,
    position = position,
    value = value,
    prefix = prefix,
    given = given,
    surnamePrefix = surnamePrefix,
    surname = surname,
    suffix = suffix,
    nickname = nickname,
    type = type,
    typeTag = typeTag,
    marriedName = marriedName,
    marriedNameTag = marriedNameTag,
    alsoKnownAs = aka,
    alsoKnownAsTag = akaTag,
    phonetic = fone,
    romanised = romn,
)

private fun Media.toEntity(treeId: Long, inline: Boolean = false) = MediaEntity(
    treeId = treeId,
    gedcomId = if (inline) null else id,
    file = file,
    fileTag = fileTag,
    title = title,
    format = format,
    mediaType = type,
    isPrimary = primary.equals("Y", ignoreCase = true),
    changeDate = change?.dateTime?.value,
)

/**
 * Carries the GEDCOM-id to row-id maps across the import, plus the family memberships
 * being assembled from both sides of the file.
 */
private class ImportContext(val treeId: Long) {
    val personIds = HashMap<String, Long>()
    val familyIds = HashMap<String, Long>()
    val noteIds = HashMap<String, Long>()
    val mediaIds = HashMap<String, Long>()
    val sourceIds = HashMap<String, Long>()
    val repositoryIds = HashMap<String, Long>()
    val submitterIds = HashMap<String, Long>()

    /** Keyed by family + person + role so the two sides of the file merge rather than duplicate. */
    val members = LinkedHashMap<Triple<Long, Long, MemberRole>, FamilyMemberEntity>()

    fun addMember(
        familyId: Long,
        personGedcomId: String?,
        role: MemberRole,
        position: Int,
        preferred: Boolean,
        fatherRelation: Pedigree? = null,
        motherRelation: Pedigree? = null,
    ) {
        val personId = personIds[personGedcomId] ?: return
        members[Triple(familyId, personId, role)] = FamilyMemberEntity(
            familyId = familyId,
            personId = personId,
            role = role,
            position = position,
            preferred = preferred,
            fatherRelation = fatherRelation,
            motherRelation = motherRelation,
        )
    }

    /** Adds detail from the person side, or the whole membership when the family omitted it. */
    fun mergeMember(
        familyId: Long,
        personId: Long,
        role: MemberRole,
        pedigree: Pedigree?,
        isPrimary: Boolean,
    ) {
        val key = Triple(familyId, personId, role)
        val existing = members[key]
        members[key] = existing?.copy(pedigree = pedigree ?: existing.pedigree, isPrimary = isPrimary || existing.isPrimary)
            ?: FamilyMemberEntity(
                familyId = familyId,
                personId = personId,
                role = role,
                position = members.size,
                pedigree = pedigree,
                isPrimary = isPrimary,
            )
    }

    /**
     * A `FAMS` link says the person is a spouse but not which role. If the family side
     * already assigned one, keep it; otherwise fall back to their sex.
     */
    fun mergeSpouseMember(familyId: Long, personId: Long, sex: Sex) {
        if (members.containsKey(Triple(familyId, personId, MemberRole.HUSBAND)) ||
            members.containsKey(Triple(familyId, personId, MemberRole.WIFE))
        ) {
            return
        }
        val role = if (sex == Sex.FEMALE) MemberRole.WIFE else MemberRole.HUSBAND
        members[Triple(familyId, personId, role)] = FamilyMemberEntity(
            familyId = familyId,
            personId = personId,
            role = role,
            position = members.size,
        )
    }
}
