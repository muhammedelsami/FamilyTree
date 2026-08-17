package com.familytree.core.data.repository

import androidx.room.withTransaction
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.data.mapper.toDomain
import com.familytree.core.data.mapper.toEntity
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.dao.MaintenanceDao
import com.familytree.core.database.dao.MediaDao
import com.familytree.core.database.entity.MediaLinkEntity
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.model.MediaObject
import com.familytree.core.model.OwnerType
import com.familytree.core.model.RecordType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val database: FamilyTreeDatabase,
    private val mediaDao: MediaDao,
    private val maintenanceDao: MaintenanceDao,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : MediaRepository {

    override fun observeGallery(treeId: Long): Flow<List<MediaObject>> =
        mediaDao.observeAll(treeId).map { list -> list.map { it.toDomain() } }

    override fun observeFor(ownerType: OwnerType, ownerId: Long): Flow<List<MediaObject>> =
        mediaDao.observeFor(ownerType, ownerId).map { list -> list.map { it.toDomain() } }

    override fun observePortraitOf(ownerType: OwnerType, ownerId: Long): Flow<MediaObject?> =
        mediaDao.observePortraitOf(ownerType, ownerId).map { it?.toDomain() }

    override fun observePersonPortraits(treeId: Long): Flow<Map<Long, MediaObject>> =
        combine(
            mediaDao.observePortraitLinks(treeId),
            mediaDao.observeAll(treeId),
        ) { links, media ->
            val byId = media.associateBy { it.id }
            // The query returns every attachment ordered best-first per person, so the
            // first row seen for a person is that person's portrait.
            buildMap {
                links.forEach { link ->
                    if (!containsKey(link.ownerId)) {
                        byId[link.mediaId]?.let { put(link.ownerId, it.toDomain()) }
                    }
                }
            }
        }

    override suspend fun get(mediaId: Long): MediaObject? =
        withContext(ioDispatcher) { mediaDao.get(mediaId)?.toDomain() }

    override suspend fun create(media: MediaObject, ownerType: OwnerType, ownerId: Long): Long =
        withContext(ioDispatcher) {
            database.withTransaction {
                // No cross-reference id: with a single owner the record is written inline
                // under that owner, which is how most GEDCOM files store media.
                val mediaId = mediaDao.insert(media.toEntity().copy(id = 0L, updatedAt = now()))
                mediaDao.insertLink(
                    MediaLinkEntity(
                        treeId = media.treeId,
                        mediaId = mediaId,
                        ownerType = ownerType,
                        ownerId = ownerId,
                        position = mediaDao.linkCount(ownerType, ownerId),
                    ),
                )
                mediaId
            }
        }

    override suspend fun update(media: MediaObject) = withContext(ioDispatcher) {
        mediaDao.update(media.toEntity().copy(updatedAt = now()))
    }

    override suspend fun delete(mediaId: Long) = withContext(ioDispatcher) {
        // Attachments cascade through the foreign key on media_links.
        mediaDao.deleteById(mediaId)
    }

    override suspend fun attach(mediaId: Long, ownerType: OwnerType, ownerId: Long) =
        withContext(ioDispatcher) {
            database.withTransaction {
                val media = mediaDao.get(mediaId) ?: return@withTransaction
                // A second owner makes the record shared, and a shared record must carry
                // an id — that is the only way GEDCOM can express "the same photo".
                if (media.gedcomId == null && mediaDao.referenceCount(mediaId) >= 1) {
                    val prefix = RecordType.MEDIA.idPrefix
                    val number = maintenanceDao.nextMediaNumber(media.treeId, prefix)
                    mediaDao.update(media.copy(gedcomId = "$prefix$number", updatedAt = now()))
                }
                mediaDao.insertLink(
                    MediaLinkEntity(
                        treeId = media.treeId,
                        mediaId = mediaId,
                        ownerType = ownerType,
                        ownerId = ownerId,
                        position = mediaDao.linkCount(ownerType, ownerId),
                    ),
                )
            }
        }

    override suspend fun detach(mediaId: Long, ownerType: OwnerType, ownerId: Long) =
        withContext(ioDispatcher) {
            database.withTransaction {
                mediaDao.unlink(mediaId, ownerType, ownerId)
                // An unattached media record is unreachable — it would be dropped silently
                // on the next export — so removing the last attachment removes the record.
                if (mediaDao.referenceCount(mediaId) == 0) mediaDao.deleteById(mediaId)
            }
        }

    override suspend fun referenceCount(mediaId: Long): Int =
        withContext(ioDispatcher) { mediaDao.referenceCount(mediaId) }

    override suspend fun setPrimary(mediaId: Long, ownerType: OwnerType, ownerId: Long) =
        withContext(ioDispatcher) {
            database.withTransaction {
                // `_PRIM` is a property of the media, not of the link, so making one photo
                // the portrait clears the flag on the others attached to the same owner.
                mediaDao.getFor(ownerType, ownerId).forEach { media ->
                    val shouldBePrimary = media.id == mediaId
                    if (media.isPrimary != shouldBePrimary) {
                        mediaDao.update(media.copy(isPrimary = shouldBePrimary, updatedAt = now()))
                    }
                }
            }
        }

    override suspend fun shortenLink(mediaId: Long, filename: String) = withContext(ioDispatcher) {
        mediaDao.get(mediaId)?.let { media ->
            mediaDao.update(media.copy(file = filename, updatedAt = now()))
        } ?: Unit
    }

    private fun now() = System.currentTimeMillis()
}
