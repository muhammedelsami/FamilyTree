package com.familytree.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.familytree.core.database.entity.MediaEntity
import com.familytree.core.database.entity.MediaLinkEntity
import com.familytree.core.database.entity.NoteEntity
import com.familytree.core.database.entity.NoteLinkEntity
import com.familytree.core.database.entity.RepositoryEntity
import com.familytree.core.database.entity.RepositoryRefEntity
import com.familytree.core.database.entity.SourceCitationEntity
import com.familytree.core.database.entity.SourceEntity
import com.familytree.core.database.entity.SubmitterEntity
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE treeId = :treeId ORDER BY id ASC")
    fun observeAll(treeId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE treeId = :treeId AND gedcomId IS NOT NULL ORDER BY id ASC")
    fun observeShared(treeId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun get(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE treeId = :treeId AND gedcomId = :gedcomId")
    suspend fun getByGedcomId(treeId: Long, gedcomId: String): NoteEntity?

    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_links l ON l.noteId = n.id
        WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId
        ORDER BY l.position ASC
        """,
    )
    fun observeFor(ownerType: OwnerType, ownerId: Long): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_links l ON l.noteId = n.id
        WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId
        ORDER BY l.position ASC
        """,
    )
    suspend fun getFor(ownerType: OwnerType, ownerId: Long): List<NoteEntity>

    /** Replaces FamilyGem's `NoteReferences` visitor. */
    @Query("SELECT COUNT(*) FROM note_links WHERE noteId = :noteId")
    suspend fun referenceCount(noteId: Long): Int

    @Insert suspend fun insert(note: NoteEntity): Long

    @Insert suspend fun insertAll(notes: List<NoteEntity>): List<Long>

    @Update suspend fun update(note: NoteEntity)

    @Delete suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    @Insert suspend fun insertLink(link: NoteLinkEntity): Long

    @Insert suspend fun insertLinks(links: List<NoteLinkEntity>): List<Long>

    @Query("SELECT * FROM note_links WHERE treeId = :treeId")
    suspend fun getAllLinks(treeId: Long): List<NoteLinkEntity>

    @Query("DELETE FROM note_links WHERE noteId = :noteId AND ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun unlink(noteId: Long, ownerType: OwnerType, ownerId: Long)

    @Query("DELETE FROM note_links WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun unlinkAllFrom(ownerType: OwnerType, ownerId: Long)
}

@Dao
interface MediaDao {

    @Query("SELECT * FROM media WHERE treeId = :treeId ORDER BY id ASC")
    fun observeAll(treeId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :mediaId")
    suspend fun get(mediaId: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE treeId = :treeId AND gedcomId = :gedcomId")
    suspend fun getByGedcomId(treeId: Long, gedcomId: String): MediaEntity?

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN media_links l ON l.mediaId = m.id
        WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId
        ORDER BY l.position ASC
        """,
    )
    fun observeFor(ownerType: OwnerType, ownerId: Long): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN media_links l ON l.mediaId = m.id
        WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId
        ORDER BY l.position ASC
        """,
    )
    suspend fun getFor(ownerType: OwnerType, ownerId: Long): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media_links WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun linkCount(ownerType: OwnerType, ownerId: Long): Int

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN media_links l ON l.mediaId = m.id
        WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId
        ORDER BY m.isPrimary DESC, l.position ASC
        LIMIT 1
        """,
    )
    fun observePortraitOf(ownerType: OwnerType, ownerId: Long): Flow<MediaEntity?>

    @Query("SELECT COUNT(*) FROM media_links WHERE mediaId = :mediaId")
    suspend fun referenceCount(mediaId: Long): Int

    @Insert suspend fun insert(media: MediaEntity): Long

    @Insert suspend fun insertAll(media: List<MediaEntity>): List<Long>

    @Update suspend fun update(media: MediaEntity)

    @Delete suspend fun delete(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :mediaId")
    suspend fun deleteById(mediaId: Long)

    @Insert suspend fun insertLink(link: MediaLinkEntity): Long

    @Insert suspend fun insertLinks(links: List<MediaLinkEntity>): List<Long>

    @Query("SELECT * FROM media_links WHERE treeId = :treeId")
    suspend fun getAllLinks(treeId: Long): List<MediaLinkEntity>

    @Query("DELETE FROM media_links WHERE mediaId = :mediaId AND ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun unlink(mediaId: Long, ownerType: OwnerType, ownerId: Long)

    @Query("DELETE FROM media_links WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun unlinkAllFrom(ownerType: OwnerType, ownerId: Long)

    /**
     * The media attached to each person, best candidate first.
     *
     * One query for the whole tree: a per-person lookup would be thousands of queries
     * for a list that is rendered in one pass.
     */
    @Query(
        """
        SELECT l.ownerId AS ownerId, l.mediaId AS mediaId FROM media_links l
        INNER JOIN media m ON m.id = l.mediaId
        WHERE l.treeId = :treeId AND l.ownerType = 'PERSON'
        ORDER BY l.ownerId ASC, m.isPrimary DESC, l.position ASC
        """,
    )
    fun observePortraitLinks(treeId: Long): Flow<List<PortraitLink>>

    /** Rewrites the stored path everywhere it appears — used after a rename or a copy-in. */
    @Query("UPDATE media SET file = :newPath WHERE treeId = :treeId AND file = :oldPath")
    suspend fun updateFilePath(treeId: Long, oldPath: String, newPath: String)
}

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources WHERE treeId = :treeId ORDER BY id ASC")
    fun observeAll(treeId: Long): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :sourceId")
    suspend fun get(sourceId: Long): SourceEntity?

    @Query("SELECT * FROM sources WHERE treeId = :treeId AND gedcomId = :gedcomId")
    suspend fun getByGedcomId(treeId: Long, gedcomId: String): SourceEntity?

    /** Replaces FamilyGem's `CountSourceCitations` visitor and its cached `citaz` extension. */
    @Query("SELECT COUNT(*) FROM source_citations WHERE sourceId = :sourceId")
    suspend fun citationCount(sourceId: Long): Int

    @Query(
        "SELECT sourceId AS id, COUNT(*) AS count FROM source_citations " +
            "WHERE treeId = :treeId AND sourceId IS NOT NULL GROUP BY sourceId",
    )
    fun observeCitationCounts(treeId: Long): Flow<List<IdCount>>

    @Insert suspend fun insert(source: SourceEntity): Long

    @Insert suspend fun insertAll(sources: List<SourceEntity>): List<Long>

    @Update suspend fun update(source: SourceEntity)

    @Delete suspend fun delete(source: SourceEntity)

    // --- citations ---

    @Query(
        "SELECT * FROM source_citations WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY position ASC",
    )
    fun observeCitationsFor(ownerType: OwnerType, ownerId: Long): Flow<List<SourceCitationEntity>>

    @Query(
        "SELECT * FROM source_citations WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY position ASC",
    )
    suspend fun getCitationsFor(ownerType: OwnerType, ownerId: Long): List<SourceCitationEntity>

    @Query("SELECT * FROM source_citations WHERE treeId = :treeId")
    suspend fun getAllCitations(treeId: Long): List<SourceCitationEntity>

    @Upsert suspend fun upsertCitation(citation: SourceCitationEntity): Long

    @Insert suspend fun insertCitations(citations: List<SourceCitationEntity>): List<Long>

    @Delete suspend fun deleteCitation(citation: SourceCitationEntity)

    @Query("DELETE FROM source_citations WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteCitationsFor(ownerType: OwnerType, ownerId: Long)
}

/** Row shape for grouped-count queries. */
data class IdCount(val id: Long, val count: Int)

/** A person and one of their media, from the tree-wide portrait query. */
data class PortraitLink(val ownerId: Long, val mediaId: Long)

@Dao
interface RepositoryDao {

    @Query("SELECT * FROM repositories WHERE treeId = :treeId ORDER BY id ASC")
    fun observeAll(treeId: Long): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE id = :repositoryId")
    suspend fun get(repositoryId: Long): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE treeId = :treeId AND gedcomId = :gedcomId")
    suspend fun getByGedcomId(treeId: Long, gedcomId: String): RepositoryEntity?

    @Query("SELECT COUNT(*) FROM repository_refs WHERE repositoryId = :repositoryId")
    suspend fun sourceCount(repositoryId: Long): Int

    @Insert suspend fun insert(repository: RepositoryEntity): Long

    @Insert suspend fun insertAll(repositories: List<RepositoryEntity>): List<Long>

    @Update suspend fun update(repository: RepositoryEntity)

    @Delete suspend fun delete(repository: RepositoryEntity)

    @Upsert suspend fun upsertRef(ref: RepositoryRefEntity): Long

    @Insert suspend fun insertRefs(refs: List<RepositoryRefEntity>): List<Long>

    @Query("SELECT * FROM repository_refs WHERE sourceId = :sourceId")
    suspend fun getRefFor(sourceId: Long): RepositoryRefEntity?

    @Query("SELECT * FROM repository_refs WHERE treeId = :treeId")
    suspend fun getAllRefs(treeId: Long): List<RepositoryRefEntity>
}

@Dao
interface SubmitterDao {

    @Query("SELECT * FROM submitters WHERE treeId = :treeId ORDER BY id ASC")
    fun observeAll(treeId: Long): Flow<List<SubmitterEntity>>

    @Query("SELECT * FROM submitters WHERE treeId = :treeId")
    suspend fun getAll(treeId: Long): List<SubmitterEntity>

    @Query("SELECT * FROM submitters WHERE id = :submitterId")
    suspend fun get(submitterId: Long): SubmitterEntity?

    @Query("SELECT * FROM submitters WHERE treeId = :treeId AND gedcomId = :gedcomId")
    suspend fun getByGedcomId(treeId: Long, gedcomId: String): SubmitterEntity?

    @Insert suspend fun insert(submitter: SubmitterEntity): Long

    @Insert suspend fun insertAll(submitters: List<SubmitterEntity>): List<Long>

    @Update suspend fun update(submitter: SubmitterEntity)

    @Delete suspend fun delete(submitter: SubmitterEntity)

    /** Marks every submitter as handed on, when a shared tree is first saved. */
    @Query("UPDATE submitters SET passed = 1 WHERE treeId = :treeId")
    suspend fun markAllPassed(treeId: Long)
}
