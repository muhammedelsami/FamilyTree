package com.familytree.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familytree.core.model.OwnerType

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("treeId"),
        Index(value = ["treeId", "gedcomId"], unique = true),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    /** Null for an inline note owned by a single record; set for a shared note. */
    val gedcomId: String? = null,
    val value: String = "",
    val rin: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * Attaches a note to any record.
 *
 * This one table replaces FamilyGem's `NoteContainers`, `NoteContainersGuarded`,
 * `NoteReferences` and `NoteList` visitors: counting references becomes a `COUNT(*)`
 * and re-pointing them becomes an `UPDATE`.
 */
@Entity(
    tableName = "note_links",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("noteId"),
        Index(value = ["ownerType", "ownerId"]),
        Index("treeId"),
    ],
)
data class NoteLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val noteId: Long,
    val ownerType: OwnerType,
    val ownerId: Long,
    val position: Int = 0,
)

@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("treeId"),
        Index(value = ["treeId", "gedcomId"], unique = true),
    ],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val file: String? = null,
    val title: String? = null,
    val format: String? = null,
    val fileTag: String? = null,
    val mediaType: String? = null,
    val isPrimary: Boolean = false,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "media_links",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("mediaId"),
        Index(value = ["ownerType", "ownerId"]),
        Index("treeId"),
    ],
)
data class MediaLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val mediaId: Long,
    val ownerType: OwnerType,
    val ownerId: Long,
    val position: Int = 0,
)

@Entity(
    tableName = "sources",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("treeId"),
        Index(value = ["treeId", "gedcomId"], unique = true),
    ],
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val title: String? = null,
    val author: String? = null,
    val abbreviation: String? = null,
    val publication: String? = null,
    val text: String? = null,
    val date: String? = null,
    val callNumber: String? = null,
    val mediaType: String? = null,
    val typeTag: String? = null,
    val uidTag: String? = null,
    val referenceNumber: String? = null,
    val rin: String? = null,
    val uid: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "source_citations",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("treeId"),
        Index("sourceId"),
        Index(value = ["ownerType", "ownerId"]),
    ],
)
data class SourceCitationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    /** Null for a "source note" — free-text provenance with no source record behind it. */
    val sourceId: Long? = null,
    val ownerType: OwnerType,
    val ownerId: Long,
    val value: String? = null,
    val page: String? = null,
    val date: String? = null,
    val text: String? = null,
    val quality: String? = null,
    val position: Int = 0,
)

@Entity(
    tableName = "repositories",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("treeId"),
        Index(value = ["treeId", "gedcomId"], unique = true),
    ],
)
data class RepositoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val name: String? = null,
    val value: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val www: String? = null,
    val wwwTag: String? = null,
    val email: String? = null,
    val emailTag: String? = null,
    val rin: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "repository_refs",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RepositoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["repositoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("sourceId"), Index("repositoryId"), Index("treeId")],
)
data class RepositoryRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val sourceId: Long,
    val repositoryId: Long? = null,
    val value: String? = null,
    val callNumber: String? = null,
    val mediaType: String? = null,
)

@Entity(
    tableName = "submitters",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("treeId"),
        Index(value = ["treeId", "gedcomId"], unique = true),
    ],
)
data class SubmitterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val name: String? = null,
    val value: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val www: String? = null,
    val wwwTag: String? = null,
    val email: String? = null,
    val emailTag: String? = null,
    val language: String? = null,
    val rin: String? = null,
    /**
     * FamilyGem stored this as a `passed` GEDCOM extension; here it is a real column,
     * so the share lifecycle is queryable instead of hidden in an extension bag.
     */
    val passed: Boolean = false,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)
