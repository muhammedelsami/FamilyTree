package com.familytree.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familytree.core.model.OwnerType

/**
 * Every GEDCOM tag the app does not model natively, kept verbatim.
 *
 * This is the table that makes "lossless round-trip" true rather than aspirational.
 * Self-referencing through [parentExtensionId], so a nested vendor structure of any
 * depth survives import and comes back out byte-comparable on export.
 */
@Entity(
    tableName = "extensions",
    foreignKeys = [
        ForeignKey(
            entity = TreeEntity::class,
            parentColumns = ["id"],
            childColumns = ["treeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExtensionEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentExtensionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("treeId"),
        Index(value = ["ownerType", "ownerId"]),
        Index("parentExtensionId"),
    ],
)
data class ExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val ownerType: OwnerType,
    val ownerId: Long,
    val parentExtensionId: Long? = null,
    val tag: String,
    val ref: String? = null,
    val value: String? = null,
    val position: Int = 0,
)
