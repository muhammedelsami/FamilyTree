package com.familytree.core.data.mapper

import com.familytree.core.database.entity.AddressEntity
import com.familytree.core.database.entity.EventEntity
import com.familytree.core.database.entity.ExtensionEntity
import com.familytree.core.database.entity.FamilyEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.database.entity.HeaderEntity
import com.familytree.core.database.entity.MediaEntity
import com.familytree.core.database.entity.MediaFolderEntity
import com.familytree.core.database.entity.NoteEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.PersonNameEntity
import com.familytree.core.database.entity.RepositoryEntity
import com.familytree.core.database.entity.SourceCitationEntity
import com.familytree.core.database.entity.SourceEntity
import com.familytree.core.database.entity.SubmitterEntity
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.model.Address
import com.familytree.core.model.Event
import com.familytree.core.model.Family
import com.familytree.core.model.FamilyMember
import com.familytree.core.model.GedcomExtension
import com.familytree.core.model.Header
import com.familytree.core.model.MediaFolder
import com.familytree.core.model.MediaObject
import com.familytree.core.model.Note
import com.familytree.core.model.Person
import com.familytree.core.model.PersonName
import com.familytree.core.model.Repository
import com.familytree.core.model.Source
import com.familytree.core.model.SourceCitation
import com.familytree.core.model.Submitter
import com.familytree.core.model.Tree
import com.familytree.core.model.TreeGrade
import com.familytree.core.model.TreeSettings

/*
 * Entity <-> domain mapping.
 *
 * The two shapes are nearly identical today, and keeping them separate anyway is the
 * point: `core:database` can change its column layout for a migration without the
 * change rippling into every screen.
 */

// --- Tree ---

fun TreeEntity.toDomain() = Tree(
    id = id,
    title = title,
    rootPersonId = rootPersonId,
    shareRootPersonId = shareRootPersonId,
    grade = TreeGrade.fromValue(grade),
    settings = TreeSettings(
        lifeSpan = lifeSpan,
        useCustomDate = useCustomDate,
        fixedDate = fixedDate,
    ),
    personCount = personCount,
    familyCount = familyCount,
    mediaCount = mediaCount,
    generationCount = generationCount,
    sortOrder = sortOrder,
    backupEnabled = backupEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Tree.toEntity() = TreeEntity(
    id = id,
    title = title,
    rootPersonId = rootPersonId,
    shareRootPersonId = shareRootPersonId,
    grade = grade.value,
    lifeSpan = settings.lifeSpan,
    useCustomDate = settings.useCustomDate,
    fixedDate = settings.fixedDate,
    personCount = personCount,
    familyCount = familyCount,
    mediaCount = mediaCount,
    generationCount = generationCount,
    sortOrder = sortOrder,
    backupEnabled = backupEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MediaFolderEntity.toDomain() = MediaFolder(id = id, treeId = treeId, kind = kind, value = value)

fun MediaFolder.toEntity() = MediaFolderEntity(id = id, treeId = treeId, kind = kind, value = value)

fun HeaderEntity.toDomain() = Header(
    treeId = treeId,
    generatorValue = generatorValue,
    generatorName = generatorName,
    generatorVersion = generatorVersion,
    corporation = corporation,
    destination = destination,
    dateTime = dateTime,
    submitterId = submitterId,
    file = file,
    copyright = copyright,
    gedcomVersion = gedcomVersion,
    gedcomForm = gedcomForm,
    characterSet = characterSet,
    language = language,
)

fun Header.toEntity() = HeaderEntity(
    treeId = treeId,
    generatorValue = generatorValue,
    generatorName = generatorName,
    generatorVersion = generatorVersion,
    corporation = corporation,
    destination = destination,
    dateTime = dateTime,
    submitterId = submitterId,
    file = file,
    copyright = copyright,
    gedcomVersion = gedcomVersion,
    gedcomForm = gedcomForm,
    characterSet = characterSet,
    language = language,
)

// --- Person ---

fun PersonEntity.toDomain() = Person(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    uid = uid,
    uidTag = uidTag,
    rin = rin,
    referenceNumbers = referenceNumbers,
    sex = sex,
    addressId = addressId,
    phone = phone,
    fax = fax,
    email = email,
    emailTag = emailTag,
    www = www,
    wwwTag = wwwTag,
    sortOrder = sortOrder,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun Person.toEntity() = PersonEntity(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    uid = uid,
    uidTag = uidTag,
    rin = rin,
    referenceNumbers = referenceNumbers,
    sex = sex,
    addressId = addressId,
    phone = phone,
    fax = fax,
    email = email,
    emailTag = emailTag,
    www = www,
    wwwTag = wwwTag,
    sortOrder = sortOrder,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun PersonNameEntity.toDomain() = PersonName(
    id = id,
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
    marriedName = marriedName,
    typeTag = typeTag,
    marriedNameTag = marriedNameTag,
    alsoKnownAsTag = alsoKnownAsTag,
    alsoKnownAs = alsoKnownAs,
    phonetic = phonetic,
    romanised = romanised,
)

fun PersonName.toEntity() = PersonNameEntity(
    id = id,
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
    marriedName = marriedName,
    typeTag = typeTag,
    marriedNameTag = marriedNameTag,
    alsoKnownAsTag = alsoKnownAsTag,
    alsoKnownAs = alsoKnownAs,
    phonetic = phonetic,
    romanised = romanised,
)

// --- Family ---

fun FamilyEntity.toDomain() = Family(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    uid = uid,
    uidTag = uidTag,
    rin = rin,
    referenceNumbers = referenceNumbers,
    sortOrder = sortOrder,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun Family.toEntity() = FamilyEntity(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    uid = uid,
    uidTag = uidTag,
    rin = rin,
    referenceNumbers = referenceNumbers,
    sortOrder = sortOrder,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun FamilyMemberEntity.toDomain() = FamilyMember(
    id = id,
    familyId = familyId,
    personId = personId,
    role = role,
    position = position,
    preferred = preferred,
    pedigree = pedigree,
    fatherRelation = fatherRelation,
    motherRelation = motherRelation,
    isPrimary = isPrimary,
)

fun FamilyMember.toEntity() = FamilyMemberEntity(
    id = id,
    familyId = familyId,
    personId = personId,
    role = role,
    position = position,
    preferred = preferred,
    pedigree = pedigree,
    fatherRelation = fatherRelation,
    motherRelation = motherRelation,
    isPrimary = isPrimary,
)

// --- Event & address ---

fun EventEntity.toDomain() = Event(
    id = id,
    treeId = treeId,
    ownerType = ownerType,
    ownerId = ownerId,
    tag = tag,
    value = value,
    type = type,
    date = date,
    place = place,
    cause = cause,
    addressId = addressId,
    phone = phone,
    fax = fax,
    www = www,
    email = email,
    rin = rin,
    uid = uid,
    position = position,
    uidTag = uidTag,
    emailTag = emailTag,
    wwwTag = wwwTag,
)

fun Event.toEntity() = EventEntity(
    id = id,
    treeId = treeId,
    ownerType = ownerType,
    ownerId = ownerId,
    tag = tag,
    value = value,
    type = type,
    date = date,
    place = place,
    cause = cause,
    addressId = addressId,
    phone = phone,
    fax = fax,
    www = www,
    email = email,
    rin = rin,
    uid = uid,
    position = position,
    uidTag = uidTag,
    emailTag = emailTag,
    wwwTag = wwwTag,
)

fun AddressEntity.toDomain() = Address(
    id = id,
    treeId = treeId,
    value = value,
    line1 = line1,
    line2 = line2,
    line3 = line3,
    city = city,
    state = state,
    postalCode = postalCode,
    country = country,
    name = name,
)

fun Address.toEntity() = AddressEntity(
    id = id,
    treeId = treeId,
    value = value,
    line1 = line1,
    line2 = line2,
    line3 = line3,
    city = city,
    state = state,
    postalCode = postalCode,
    country = country,
    name = name,
)

// --- Other records ---

fun NoteEntity.toDomain() = Note(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    value = value,
    rin = rin,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun Note.toEntity() = NoteEntity(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    value = value,
    rin = rin,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun MediaEntity.toDomain() = MediaObject(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    file = file,
    title = title,
    format = format,
    mediaType = mediaType,
    fileTag = fileTag,
    isPrimary = isPrimary,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun MediaObject.toEntity() = MediaEntity(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    file = file,
    title = title,
    format = format,
    mediaType = mediaType,
    fileTag = fileTag,
    isPrimary = isPrimary,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun SourceEntity.toDomain() = Source(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    title = title,
    author = author,
    abbreviation = abbreviation,
    publication = publication,
    text = text,
    date = date,
    callNumber = callNumber,
    mediaType = mediaType,
    referenceNumber = referenceNumber,
    typeTag = typeTag,
    uidTag = uidTag,
    rin = rin,
    uid = uid,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun Source.toEntity() = SourceEntity(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    title = title,
    author = author,
    abbreviation = abbreviation,
    publication = publication,
    text = text,
    date = date,
    callNumber = callNumber,
    mediaType = mediaType,
    referenceNumber = referenceNumber,
    typeTag = typeTag,
    uidTag = uidTag,
    rin = rin,
    uid = uid,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun SourceCitationEntity.toDomain() = SourceCitation(
    id = id,
    treeId = treeId,
    sourceId = sourceId,
    ownerType = ownerType,
    ownerId = ownerId,
    value = value,
    page = page,
    date = date,
    text = text,
    quality = quality,
    position = position,
)

fun SourceCitation.toEntity() = SourceCitationEntity(
    id = id,
    treeId = treeId,
    sourceId = sourceId,
    ownerType = ownerType,
    ownerId = ownerId,
    value = value,
    page = page,
    date = date,
    text = text,
    quality = quality,
    position = position,
)

fun RepositoryEntity.toDomain() = Repository(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    name = name,
    value = value,
    addressId = addressId,
    phone = phone,
    fax = fax,
    www = www,
    email = email,
    rin = rin,
    changeDate = changeDate,
    wwwTag = wwwTag,
    emailTag = emailTag,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun Repository.toEntity() = RepositoryEntity(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    name = name,
    value = value,
    addressId = addressId,
    phone = phone,
    fax = fax,
    www = www,
    email = email,
    rin = rin,
    changeDate = changeDate,
    wwwTag = wwwTag,
    emailTag = emailTag,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun SubmitterEntity.toDomain() = Submitter(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    name = name,
    value = value,
    addressId = addressId,
    phone = phone,
    fax = fax,
    www = www,
    email = email,
    language = language,
    wwwTag = wwwTag,
    emailTag = emailTag,
    rin = rin,
    passed = passed,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun Submitter.toEntity() = SubmitterEntity(
    id = id,
    treeId = treeId,
    gedcomId = gedcomId,
    name = name,
    value = value,
    addressId = addressId,
    phone = phone,
    fax = fax,
    www = www,
    email = email,
    language = language,
    wwwTag = wwwTag,
    emailTag = emailTag,
    rin = rin,
    passed = passed,
    changeDate = changeDate,
    changeZone = changeZone,
    updatedAt = updatedAt,
)

fun ExtensionEntity.toDomain() = GedcomExtension(
    id = id,
    treeId = treeId,
    ownerType = ownerType,
    ownerId = ownerId,
    parentExtensionId = parentExtensionId,
    tag = tag,
    ref = ref,
    value = value,
    position = position,
)

fun GedcomExtension.toEntity() = ExtensionEntity(
    id = id,
    treeId = treeId,
    ownerType = ownerType,
    ownerId = ownerId,
    parentExtensionId = parentExtensionId,
    tag = tag,
    ref = ref,
    value = value,
    position = position,
)
