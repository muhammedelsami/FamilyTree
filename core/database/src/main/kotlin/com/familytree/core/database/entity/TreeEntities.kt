package com.familytree.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familytree.core.model.MediaFolder

@Entity(tableName = "trees")
data class TreeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    /**
     * Deliberately not a foreign key: persons reference their tree, so an FK back would
     * make the pair circular and force a specific insert order during import.
     * The repository keeps it consistent instead.
     */
    val rootPersonId: Long? = null,
    val shareRootPersonId: Long? = null,
    val grade: Int = 0,
    val lifeSpan: Int = 110,
    val useCustomDate: Boolean = false,
    val fixedDate: String? = null,
    val personCount: Int = 0,
    val familyCount: Int = 0,
    val mediaCount: Int = 0,
    val generationCount: Int = 0,
    val sortOrder: Int = 0,
    val backupEnabled: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "tree_media_folders",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("treeId")],
)
data class MediaFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val kind: MediaFolder.Kind,
    val value: String,
)

@Entity(
    tableName = "tree_shares",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("treeId"), Index("dateId")],
)
data class TreeShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val dateId: String,
    val submitterId: Long? = null,
)

@Entity(
    tableName = "headers",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HeaderEntity(
    @PrimaryKey val treeId: Long,
    val generatorValue: String? = null,
    val generatorName: String? = null,
    val generatorVersion: String? = null,
    val corporation: String? = null,
    val destination: String? = null,
    val dateTime: String? = null,
    val submitterId: Long? = null,
    val file: String? = null,
    val copyright: String? = null,
    val gedcomVersion: String? = null,
    val gedcomForm: String? = null,
    val characterSet: String? = null,
    val language: String? = null,
)
