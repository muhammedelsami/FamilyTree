package com.familytree.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.familytree.core.database.entity.ExtensionEntity
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {

    @Query(
        "SELECT * FROM extensions WHERE ownerType = :ownerType AND ownerId = :ownerId " +
            "AND parentExtensionId IS NULL ORDER BY position ASC",
    )
    fun observeRootsFor(ownerType: OwnerType, ownerId: Long): Flow<List<ExtensionEntity>>

    @Query(
        "SELECT * FROM extensions WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY position ASC",
    )
    suspend fun getFor(ownerType: OwnerType, ownerId: Long): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE parentExtensionId = :parentId ORDER BY position ASC")
    suspend fun getChildren(parentId: Long): List<ExtensionEntity>

    /** Everything in the tree, for the export pass that rebuilds the tag hierarchy. */
    @Query("SELECT * FROM extensions WHERE treeId = :treeId ORDER BY position ASC")
    suspend fun getAll(treeId: Long): List<ExtensionEntity>

    /** Header-level extensions such as Family Historian's `_ROOT` and Ahnenblatt's `_HOME`. */
    @Query(
        "SELECT * FROM extensions WHERE treeId = :treeId AND ownerType = 'HEADER' AND tag = :tag LIMIT 1",
    )
    suspend fun findHeaderTag(treeId: Long, tag: String): ExtensionEntity?

    @Insert suspend fun insert(extension: ExtensionEntity): Long

    @Insert suspend fun insertAll(extensions: List<ExtensionEntity>): List<Long>

    @Update suspend fun update(extension: ExtensionEntity)

    @Delete suspend fun delete(extension: ExtensionEntity)

    @Query("DELETE FROM extensions WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteFor(ownerType: OwnerType, ownerId: Long)
}
