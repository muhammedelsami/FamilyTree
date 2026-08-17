package com.familytree.core.domain.repository

import com.familytree.core.model.MediaObject
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.flow.Flow

/**
 * Media records and what they are attached to.
 *
 * A media record and its attachment are separate things in GEDCOM: one photo of a wedding
 * can hang off both spouses and the family, so [attach] and [detach] are distinct from
 * creating and deleting the record itself.
 */
interface MediaRepository {

    /** Every media record in the tree, for the gallery. */
    fun observeGallery(treeId: Long): Flow<List<MediaObject>>

    fun observeFor(ownerType: OwnerType, ownerId: Long): Flow<List<MediaObject>>

    /** The image that represents a record: the one marked primary, else the first. */
    fun observePortraitOf(ownerType: OwnerType, ownerId: Long): Flow<MediaObject?>

    /** Portraits for a whole tree at once, keyed by person row id, for list screens. */
    fun observePersonPortraits(treeId: Long): Flow<Map<Long, MediaObject>>

    suspend fun get(mediaId: Long): MediaObject?

    /**
     * Creates a media record and attaches it.
     *
     * A record attached to exactly one owner stays inline (no cross-reference id), which
     * is how most files appear in real GEDCOM. It is promoted to a shared record with an
     * id the moment a second owner is attached.
     */
    suspend fun create(media: MediaObject, ownerType: OwnerType, ownerId: Long): Long

    suspend fun update(media: MediaObject)

    /** Deletes the record and every attachment. Does not touch the file on disk. */
    suspend fun delete(mediaId: Long)

    suspend fun attach(mediaId: Long, ownerType: OwnerType, ownerId: Long)

    /** Removes one attachment, and the record too if that was the last one. */
    suspend fun detach(mediaId: Long, ownerType: OwnerType, ownerId: Long)

    /** How many records point at this media — what makes deleting it safe or not. */
    suspend fun referenceCount(mediaId: Long): Int

    /** Marks one media as the portrait of its owner, clearing the previous one. */
    suspend fun setPrimary(mediaId: Long, ownerType: OwnerType, ownerId: Long)

    /**
     * Replaces every stored link that resolves only by filename with the filename alone.
     *
     * A tree exported from a desktop program is full of paths like `C:\Users\anna\…`
     * that mean nothing on any other device. Shortening them to `nonna.jpg` is what makes
     * the tree portable, and it is safe precisely because the file was already found.
     */
    suspend fun shortenLink(mediaId: Long, filename: String)
}
