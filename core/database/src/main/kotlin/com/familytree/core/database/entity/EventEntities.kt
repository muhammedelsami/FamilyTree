package com.familytree.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familytree.core.model.OwnerType

/**
 * `ownerType`/`ownerId` is a polymorphic link and therefore cannot carry a foreign key.
 * Deletion is handled by the repository, which removes an owner's attachments in the
 * same transaction as the owner itself.
 */
@Entity(
    tableName = "events",
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
        Index(value = ["ownerType", "ownerId"]),
        Index(value = ["treeId", "tag"]),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val ownerType: OwnerType,
    val ownerId: Long,
    val tag: String,
    val value: String? = null,
    val type: String? = null,
    val date: String? = null,
    val place: String? = null,
    val cause: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val www: String? = null,
    val email: String? = null,
    val rin: String? = null,
    val uid: String? = null,
    val uidTag: String? = null,
    val emailTag: String? = null,
    val wwwTag: String? = null,
    val position: Int = 0,
)

@Entity(
    tableName = "addresses",
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
data class AddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val value: String? = null,
    val line1: String? = null,
    val line2: String? = null,
    val line3: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val name: String? = null,
)
