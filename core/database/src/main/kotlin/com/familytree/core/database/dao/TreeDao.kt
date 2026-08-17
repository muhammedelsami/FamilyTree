package com.familytree.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.familytree.core.database.entity.HeaderEntity
import com.familytree.core.database.entity.MediaFolderEntity
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.database.entity.TreeShareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreeDao {

    @Query("SELECT * FROM trees ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TreeEntity>>

    @Query("SELECT * FROM trees WHERE id = :treeId")
    fun observe(treeId: Long): Flow<TreeEntity?>

    @Query("SELECT * FROM trees WHERE id = :treeId")
    suspend fun get(treeId: Long): TreeEntity?

    @Query("SELECT * FROM trees ORDER BY sortOrder ASC")
    suspend fun getAll(): List<TreeEntity>

    @Insert
    suspend fun insert(tree: TreeEntity): Long

    @Update
    suspend fun update(tree: TreeEntity)

    @Delete
    suspend fun delete(tree: TreeEntity)

    @Query("DELETE FROM trees WHERE id = :treeId")
    suspend fun deleteById(treeId: Long)

    @Query("UPDATE trees SET title = :title, updatedAt = :now WHERE id = :treeId")
    suspend fun rename(treeId: Long, title: String, now: Long)

    @Query("UPDATE trees SET sortOrder = :sortOrder WHERE id = :treeId")
    suspend fun setSortOrder(treeId: Long, sortOrder: Int)

    @Query("UPDATE trees SET rootPersonId = :personId WHERE id = :treeId")
    suspend fun setRootPerson(treeId: Long, personId: Long?)

    @Query("UPDATE trees SET grade = :grade WHERE id = :treeId")
    suspend fun setGrade(treeId: Long, grade: Int)

    /** Refreshes the denormalised counters shown in the tree list. */
    @Query(
        """
        UPDATE trees SET
            personCount = (SELECT COUNT(*) FROM persons WHERE treeId = :treeId),
            familyCount = (SELECT COUNT(*) FROM families WHERE treeId = :treeId),
            mediaCount  = (SELECT COUNT(*) FROM media WHERE treeId = :treeId),
            generationCount = :generations,
            updatedAt = :now
        WHERE id = :treeId
        """,
    )
    suspend fun refreshCounters(treeId: Long, generations: Int, now: Long)

    // --- header ---

    @Upsert
    suspend fun upsertHeader(header: HeaderEntity)

    @Query("SELECT * FROM headers WHERE treeId = :treeId")
    suspend fun getHeader(treeId: Long): HeaderEntity?

    @Query("SELECT * FROM headers WHERE treeId = :treeId")
    fun observeHeader(treeId: Long): Flow<HeaderEntity?>

    // --- media folders ---

    @Query("SELECT * FROM tree_media_folders WHERE treeId = :treeId")
    fun observeMediaFolders(treeId: Long): Flow<List<MediaFolderEntity>>

    @Query("SELECT * FROM tree_media_folders WHERE treeId = :treeId")
    suspend fun getMediaFolders(treeId: Long): List<MediaFolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaFolder(folder: MediaFolderEntity): Long

    @Delete
    suspend fun deleteMediaFolder(folder: MediaFolderEntity)

    @Query("DELETE FROM tree_media_folders WHERE id = :folderId")
    suspend fun deleteMediaFolderById(folderId: Long)

    // --- shares ---

    @Query("SELECT * FROM tree_shares WHERE treeId = :treeId")
    suspend fun getShares(treeId: Long): List<TreeShareEntity>

    @Insert
    suspend fun insertShare(share: TreeShareEntity): Long

    /** Finds trees that already carry a given share id — how a returning tree is matched. */
    @Query("SELECT treeId FROM tree_shares WHERE dateId = :dateId")
    suspend fun findTreesByShareId(dateId: String): List<Long>
}
