package com.familytree.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.PersonNameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons WHERE treeId = :treeId ORDER BY sortOrder ASC, id ASC")
    fun observeAll(treeId: Long): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :personId")
    fun observe(personId: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM persons WHERE id = :personId")
    suspend fun get(personId: Long): PersonEntity?

    @Query("SELECT * FROM persons WHERE treeId = :treeId AND gedcomId = :gedcomId")
    suspend fun getByGedcomId(treeId: Long, gedcomId: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<PersonEntity>

    @Query("SELECT COUNT(*) FROM persons WHERE treeId = :treeId")
    suspend fun count(treeId: Long): Int

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Insert
    suspend fun insertAll(persons: List<PersonEntity>): List<Long>

    @Update
    suspend fun update(person: PersonEntity)

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("DELETE FROM persons WHERE id = :personId")
    suspend fun deleteById(personId: Long)

    /**
     * The lowest numeric GEDCOM id in the tree — FamilyGem's third fallback when
     * picking a root person for a file that does not declare one.
     */
    @Query(
        """
        SELECT * FROM persons WHERE treeId = :treeId AND gedcomId IS NOT NULL
        ORDER BY CAST(REPLACE(gedcomId, 'I', '') AS INTEGER) ASC LIMIT 1
        """,
    )
    suspend fun findLowestNumberedPerson(treeId: Long): PersonEntity?

    // --- names ---

    @Query("SELECT * FROM person_names WHERE personId = :personId ORDER BY position ASC")
    fun observeNames(personId: Long): Flow<List<PersonNameEntity>>

    @Query("SELECT * FROM person_names WHERE personId = :personId ORDER BY position ASC")
    suspend fun getNames(personId: Long): List<PersonNameEntity>

    @Query(
        """
        SELECT n.* FROM person_names n
        INNER JOIN persons p ON p.id = n.personId
        WHERE p.treeId = :treeId
        ORDER BY n.personId ASC, n.position ASC
        """,
    )
    suspend fun getAllNames(treeId: Long): List<PersonNameEntity>

    @Query(
        """
        SELECT n.* FROM person_names n
        INNER JOIN persons p ON p.id = n.personId
        WHERE p.treeId = :treeId
        ORDER BY n.personId ASC, n.position ASC
        """,
    )
    fun observeAllNames(treeId: Long): Flow<List<PersonNameEntity>>

    @Upsert
    suspend fun upsertName(name: PersonNameEntity): Long

    @Insert
    suspend fun insertNames(names: List<PersonNameEntity>): List<Long>

    @Delete
    suspend fun deleteName(name: PersonNameEntity)

    @Query("DELETE FROM person_names WHERE id = :nameId")
    suspend fun deleteNameById(nameId: Long)

    @Query("DELETE FROM person_names WHERE personId = :personId")
    suspend fun deleteNamesOf(personId: Long)
}
