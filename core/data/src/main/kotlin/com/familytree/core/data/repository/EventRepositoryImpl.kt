package com.familytree.core.data.repository

import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.data.mapper.toDomain
import com.familytree.core.data.mapper.toEntity
import com.familytree.core.database.dao.EventDao
import com.familytree.core.domain.repository.EventRepository
import com.familytree.core.model.Event
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EventRepository {

    override fun observeFor(ownerType: OwnerType, ownerId: Long): Flow<List<Event>> =
        eventDao.observeFor(ownerType, ownerId).map { list -> list.map { it.toDomain() } }

    override suspend fun getFor(ownerType: OwnerType, ownerId: Long): List<Event> =
        withContext(ioDispatcher) { eventDao.getFor(ownerType, ownerId).map { it.toDomain() } }

    override suspend fun upsert(event: Event): Long = withContext(ioDispatcher) {
        if (event.id == 0L) {
            eventDao.insert(event.toEntity())
        } else {
            eventDao.update(event.toEntity())
            event.id
        }
    }

    override suspend fun delete(eventId: Long) = withContext(ioDispatcher) {
        eventDao.get(eventId)?.let { eventDao.delete(it) } ?: Unit
    }

    override fun observeKnownPlaces(treeId: Long): Flow<List<String>> =
        eventDao.observeKnownPlaces(treeId)
}
