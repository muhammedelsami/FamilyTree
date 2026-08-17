package com.familytree.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.familytree.core.database.entity.FamilyEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.model.MemberRole
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {

    @Query("SELECT * FROM families WHERE treeId = :treeId ORDER BY sortOrder ASC, id ASC")
    fun observeAll(treeId: Long): Flow<List<FamilyEntity>>

    @Query("SELECT * FROM families WHERE id = :familyId")
    suspend fun get(familyId: Long): FamilyEntity?

    @Query("SELECT * FROM families WHERE treeId = :treeId AND gedcomId = :gedcomId")
    suspend fun getByGedcomId(treeId: Long, gedcomId: String): FamilyEntity?

    @Query("SELECT * FROM families WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<FamilyEntity>

    @Insert
    suspend fun insert(family: FamilyEntity): Long

    @Insert
    suspend fun insertAll(families: List<FamilyEntity>): List<Long>

    @Update
    suspend fun update(family: FamilyEntity)

    @Delete
    suspend fun delete(family: FamilyEntity)

    @Query("DELETE FROM families WHERE id = :familyId")
    suspend fun deleteById(familyId: Long)

    // --- membership ---

    @Query("SELECT * FROM family_members WHERE familyId = :familyId ORDER BY position ASC")
    fun observeMembers(familyId: Long): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE familyId = :familyId ORDER BY position ASC")
    suspend fun getMembers(familyId: Long): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE personId = :personId")
    suspend fun getMembershipsOf(personId: Long): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE personId = :personId")
    fun observeMembershipsOf(personId: Long): Flow<List<FamilyMemberEntity>>

    @Query(
        """
        SELECT fm.* FROM family_members fm
        INNER JOIN families f ON f.id = fm.familyId
        WHERE f.treeId = :treeId
        """,
    )
    suspend fun getAllMemberships(treeId: Long): List<FamilyMemberEntity>

    @Query(
        """
        SELECT fm.* FROM family_members fm
        INNER JOIN families f ON f.id = fm.familyId
        WHERE f.treeId = :treeId
        """,
    )
    fun observeAllMemberships(treeId: Long): Flow<List<FamilyMemberEntity>>

    /** The families a person belongs to as a child — their families of origin. */
    @Query(
        """
        SELECT f.* FROM families f
        INNER JOIN family_members fm ON fm.familyId = f.id
        WHERE fm.personId = :personId AND fm.role = 'CHILD'
        ORDER BY fm.position ASC
        """,
    )
    suspend fun getParentFamilies(personId: Long): List<FamilyEntity>

    /** The families a person belongs to as a spouse. */
    @Query(
        """
        SELECT f.* FROM families f
        INNER JOIN family_members fm ON fm.familyId = f.id
        WHERE fm.personId = :personId AND fm.role IN ('HUSBAND', 'WIFE')
        ORDER BY fm.position ASC
        """,
    )
    suspend fun getSpouseFamilies(personId: Long): List<FamilyEntity>

    @Query(
        """
        SELECT p.* FROM persons p
        INNER JOIN family_members fm ON fm.personId = p.id
        WHERE fm.familyId = :familyId AND fm.role = :role
        ORDER BY fm.position ASC
        """,
    )
    suspend fun getPersonsInRole(familyId: Long, role: MemberRole): List<PersonEntity>

    @Insert
    suspend fun insertMember(member: FamilyMemberEntity): Long

    @Insert
    suspend fun insertMembers(members: List<FamilyMemberEntity>): List<Long>

    @Update
    suspend fun updateMember(member: FamilyMemberEntity)

    @Delete
    suspend fun deleteMember(member: FamilyMemberEntity)

    /** Clears a family's whole membership, for rebuilding it from an incoming copy. */
    @Query("DELETE FROM family_members WHERE familyId = :familyId")
    suspend fun deleteMembersOf(familyId: Long)

    @Query("DELETE FROM family_members WHERE familyId = :familyId AND personId = :personId")
    suspend fun unlink(familyId: Long, personId: Long)

    /** Families left with fewer than two members after an unlink, ready to be pruned. */
    @Query(
        """
        SELECT f.* FROM families f
        WHERE f.treeId = :treeId
          AND (SELECT COUNT(*) FROM family_members fm WHERE fm.familyId = f.id) < 2
        """,
    )
    suspend fun findUnderpopulatedFamilies(treeId: Long): List<FamilyEntity>
}
