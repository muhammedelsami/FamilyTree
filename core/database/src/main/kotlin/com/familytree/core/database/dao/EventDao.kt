package com.familytree.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.familytree.core.database.entity.AddressEntity
import com.familytree.core.database.entity.EventEntity
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query(
        "SELECT * FROM events WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY position ASC",
    )
    fun observeFor(ownerType: OwnerType, ownerId: Long): Flow<List<EventEntity>>

    @Query(
        "SELECT * FROM events WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY position ASC",
    )
    suspend fun getFor(ownerType: OwnerType, ownerId: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE treeId = :treeId ORDER BY ownerId ASC, position ASC")
    fun observeAllForTree(treeId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE treeId = :treeId AND tag IN (:tags)")
    suspend fun getByTags(treeId: Long, tags: List<String>): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun get(eventId: Long): EventEntity?

    /** Distinct place names already used in the tree — the local autocomplete source. */
    @Query(
        """
        SELECT DISTINCT place FROM events
        WHERE treeId = :treeId AND place IS NOT NULL AND place != ''
        ORDER BY place ASC
        """,
    )
    fun observeKnownPlaces(treeId: Long): Flow<List<String>>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Insert
    suspend fun insertAll(events: List<EventEntity>): List<Long>

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteFor(ownerType: OwnerType, ownerId: Long)

    // --- addresses ---

    @Upsert
    suspend fun upsertAddress(address: AddressEntity): Long

    @Query("SELECT * FROM addresses WHERE id = :addressId")
    suspend fun getAddress(addressId: Long): AddressEntity?

    @Query("SELECT * FROM addresses WHERE treeId = :treeId")
    suspend fun getAllAddresses(treeId: Long): List<AddressEntity>

    @Query("DELETE FROM addresses WHERE id = :addressId")
    suspend fun deleteAddress(addressId: Long)
}
