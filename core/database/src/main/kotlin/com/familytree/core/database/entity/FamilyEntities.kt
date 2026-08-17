package com.familytree.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familytree.core.model.MemberRole
import com.familytree.core.model.Pedigree

@Entity(
    tableName = "families",
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
data class FamilyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val sortOrder: Int = 0,
    val uid: String? = null,
    val uidTag: String? = null,
    val rin: String? = null,
    val referenceNumbers: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * The single source of truth for kinship.
 *
 * GEDCOM records every membership twice — once on the family (`HUSB`/`WIFE`/`CHIL`)
 * and once on the person (`FAMS`/`FAMC`) — which is exactly how trees end up with
 * one-sided references that FamilyGem had to scan for and repair. Storing it once and
 * regenerating both directions on export makes that failure mode structurally
 * impossible.
 */
@Entity(
    tableName = "family_members",
    foreignKeys = [
        ForeignKey(
            entity = FamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("familyId"),
        Index("personId"),
        Index(value = ["familyId", "personId", "role"], unique = true),
    ],
)
data class FamilyMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val familyId: Long,
    val personId: Long,
    val role: MemberRole,
    val position: Int = 0,
    val preferred: Boolean = false,
    val pedigree: Pedigree? = null,
    val fatherRelation: Pedigree? = null,
    val motherRelation: Pedigree? = null,
    val isPrimary: Boolean = false,
)
