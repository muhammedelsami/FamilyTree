package com.familytree.core.domain.repository

import com.familytree.core.model.Event
import com.familytree.core.model.Family
import com.familytree.core.model.FamilyDetails
import com.familytree.core.model.FamilyMember
import com.familytree.core.model.MemberRole
import kotlinx.coroutines.flow.Flow

interface FamilyRepository {

    fun observeFamilies(treeId: Long): Flow<List<Family>>

    fun observeFamilyDetails(familyId: Long): Flow<FamilyDetails?>

    suspend fun getFamily(familyId: Long): Family?

    suspend fun getMembers(familyId: Long): List<FamilyMember>

    /** Every membership in the tree, for screens that need the whole kinship graph. */
    fun observeAllMemberships(treeId: Long): Flow<List<FamilyMember>>

    /** Every family-owned event in the tree — marriages, divorces and the rest. */
    fun observeFamilyEvents(treeId: Long): Flow<List<Event>>

    suspend fun createFamily(treeId: Long): Long

    suspend fun deleteFamily(familyId: Long)

    suspend fun addMember(familyId: Long, personId: Long, role: MemberRole): Long

    suspend fun updateMember(member: FamilyMember)

    suspend fun removeMember(familyId: Long, personId: Long)

    suspend fun getParentFamilies(personId: Long): List<Family>

    suspend fun getSpouseFamilies(personId: Long): List<Family>

    /**
     * Deletes families left with fewer than two members after an unlink.
     *
     * A family of one carries no information GEDCOM can express, and leaving them
     * behind is how a tree accumulates the orphan records FamilyGem had to hunt for.
     */
    suspend fun pruneUnderpopulatedFamilies(treeId: Long): Int
}
