package com.familytree.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Integrity checks and repairs.
 *
 * Kept apart from the record DAOs because these queries cut across tables and are only
 * ever run on demand, never on the path that renders a screen.
 */
@Dao
interface MaintenanceDao {

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM persons WHERE treeId = :treeId AND gedcomId IS NULL) +
            (SELECT COUNT(*) FROM families WHERE treeId = :treeId AND gedcomId IS NULL) +
            (SELECT COUNT(*) FROM sources WHERE treeId = :treeId AND gedcomId IS NULL) +
            (SELECT COUNT(*) FROM repositories WHERE treeId = :treeId AND gedcomId IS NULL) +
            (SELECT COUNT(*) FROM submitters WHERE treeId = :treeId AND gedcomId IS NULL)
        """,
    )
    suspend fun countRecordsWithoutId(treeId: Long): Int

    /** A root that names nobody, or names somebody who is not in this tree. */
    @Query(
        """
        SELECT COUNT(*) FROM trees
        WHERE id = :treeId
          AND (SELECT COUNT(*) FROM persons WHERE treeId = :treeId) > 0
          AND (
              rootPersonId IS NULL
              OR rootPersonId NOT IN (SELECT id FROM persons WHERE treeId = :treeId)
          )
        """,
    )
    suspend fun countMissingRoot(treeId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM families f
        WHERE f.treeId = :treeId
          AND (SELECT COUNT(*) FROM family_members m WHERE m.familyId = f.id) < 2
        """,
    )
    suspend fun countUnderpopulatedFamilies(treeId: Long): Int

    @Query("SELECT COUNT(*) FROM media WHERE treeId = :treeId AND (file IS NULL OR file = '')")
    suspend fun countMediaWithoutFile(treeId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM persons p
        WHERE p.treeId = :treeId
          AND (SELECT COUNT(*) FROM person_names n WHERE n.personId = p.id) = 0
        """,
    )
    suspend fun countPeopleWithoutName(treeId: Long): Int

    /**
     * Links whose owner is gone.
     *
     * Polymorphic links cannot carry a foreign key, so a deletion path that forgets to
     * clear them leaves these behind; this is the safety net for that class of bug.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM note_links l WHERE l.treeId = :treeId
                AND ((l.ownerType = 'PERSON' AND l.ownerId NOT IN (SELECT id FROM persons))
                  OR (l.ownerType = 'FAMILY' AND l.ownerId NOT IN (SELECT id FROM families))
                  OR (l.ownerType = 'EVENT' AND l.ownerId NOT IN (SELECT id FROM events)))) +
            (SELECT COUNT(*) FROM media_links l WHERE l.treeId = :treeId
                AND ((l.ownerType = 'PERSON' AND l.ownerId NOT IN (SELECT id FROM persons))
                  OR (l.ownerType = 'FAMILY' AND l.ownerId NOT IN (SELECT id FROM families))
                  OR (l.ownerType = 'EVENT' AND l.ownerId NOT IN (SELECT id FROM events)))) +
            (SELECT COUNT(*) FROM events e WHERE e.treeId = :treeId
                AND ((e.ownerType = 'PERSON' AND e.ownerId NOT IN (SELECT id FROM persons))
                  OR (e.ownerType = 'FAMILY' AND e.ownerId NOT IN (SELECT id FROM families))))
        """,
    )
    suspend fun countOrphanedLinks(treeId: Long): Int

    // --- repairs ---

    @Query(
        """
        DELETE FROM note_links WHERE treeId = :treeId
            AND ((ownerType = 'PERSON' AND ownerId NOT IN (SELECT id FROM persons))
              OR (ownerType = 'FAMILY' AND ownerId NOT IN (SELECT id FROM families))
              OR (ownerType = 'EVENT' AND ownerId NOT IN (SELECT id FROM events)))
        """,
    )
    suspend fun deleteOrphanedNoteLinks(treeId: Long): Int

    @Query(
        """
        DELETE FROM media_links WHERE treeId = :treeId
            AND ((ownerType = 'PERSON' AND ownerId NOT IN (SELECT id FROM persons))
              OR (ownerType = 'FAMILY' AND ownerId NOT IN (SELECT id FROM families))
              OR (ownerType = 'EVENT' AND ownerId NOT IN (SELECT id FROM events)))
        """,
    )
    suspend fun deleteOrphanedMediaLinks(treeId: Long): Int

    @Query(
        """
        DELETE FROM events WHERE treeId = :treeId
            AND ((ownerType = 'PERSON' AND ownerId NOT IN (SELECT id FROM persons))
              OR (ownerType = 'FAMILY' AND ownerId NOT IN (SELECT id FROM families)))
        """,
    )
    suspend fun deleteOrphanedEvents(treeId: Long): Int

    @Query(
        """
        DELETE FROM families WHERE treeId = :treeId
            AND (SELECT COUNT(*) FROM family_members m WHERE m.familyId = families.id) < 2
        """,
    )
    suspend fun deleteUnderpopulatedFamilies(treeId: Long): Int

    /** The next free numeric suffix for a record type, so generated ids never collide. */
    @Query(
        "SELECT COALESCE(MAX(CAST(REPLACE(gedcomId, :prefix, '') AS INTEGER)), 0) + 1 " +
            "FROM persons WHERE treeId = :treeId AND gedcomId LIKE :prefix || '%'",
    )
    suspend fun nextPersonNumber(treeId: Long, prefix: String): Int

    @Query("SELECT id FROM persons WHERE treeId = :treeId AND gedcomId IS NULL")
    suspend fun personsWithoutId(treeId: Long): List<Long>

    @Query("UPDATE persons SET gedcomId = :gedcomId WHERE id = :personId")
    suspend fun setPersonGedcomId(personId: Long, gedcomId: String)

    @Query(
        "SELECT COALESCE(MAX(CAST(REPLACE(gedcomId, :prefix, '') AS INTEGER)), 0) + 1 " +
            "FROM families WHERE treeId = :treeId AND gedcomId LIKE :prefix || '%'",
    )
    suspend fun nextFamilyNumber(treeId: Long, prefix: String): Int

    @Query("SELECT id FROM families WHERE treeId = :treeId AND gedcomId IS NULL")
    suspend fun familiesWithoutId(treeId: Long): List<Long>

    @Query("UPDATE families SET gedcomId = :gedcomId WHERE id = :familyId")
    suspend fun setFamilyGedcomId(familyId: Long, gedcomId: String)

    @Query(
        "SELECT COALESCE(MAX(CAST(REPLACE(gedcomId, :prefix, '') AS INTEGER)), 0) + 1 " +
            "FROM media WHERE treeId = :treeId AND gedcomId LIKE :prefix || '%'",
    )
    suspend fun nextMediaNumber(treeId: Long, prefix: String): Int
}
