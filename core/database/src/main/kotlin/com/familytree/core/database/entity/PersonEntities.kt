package com.familytree.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familytree.core.model.Sex

/**
 * Unique index on `(treeId, gedcomId)`: SQLite treats NULLs as distinct in a unique
 * index, so any number of records may still be waiting for an id while real ids stay
 * unique within their tree.
 */
@Entity(
    tableName = "persons",
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
        Index(value = ["treeId", "sortOrder"]),
    ],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val sex: Sex = Sex.NONE,
    val sortOrder: Int = 0,
    /** `_UID`/`UID` — a stable identifier other programs use to match records. */
    val uid: String? = null,
    /** Which spelling the source file used, so export reproduces it exactly. */
    val uidTag: String? = null,
    val rin: String? = null,
    /** `REFN` values, newline separated; a record may carry several. */
    val referenceNumbers: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val email: String? = null,
    /** `EMAIL` or the legacy `_EMAIL`; kept so export reproduces the source spelling. */
    val emailTag: String? = null,
    val www: String? = null,
    val wwwTag: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "person_names",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class PersonNameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val personId: Long,
    val position: Int = 0,
    val value: String? = null,
    val prefix: String? = null,
    val given: String? = null,
    val surnamePrefix: String? = null,
    val surname: String? = null,
    val suffix: String? = null,
    val nickname: String? = null,
    val type: String? = null,
    val typeTag: String? = null,
    val marriedName: String? = null,
    val marriedNameTag: String? = null,
    val alsoKnownAsTag: String? = null,
    val alsoKnownAs: String? = null,
    val phonetic: String? = null,
    val romanised: String? = null,
)
