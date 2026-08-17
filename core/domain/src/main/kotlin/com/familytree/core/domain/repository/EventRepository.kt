package com.familytree.core.domain.repository

import com.familytree.core.model.Event
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.flow.Flow

/**
 * Events, wherever they hang.
 *
 * The same table serves people, families and anything else that can carry a fact, so
 * events get their own repository rather than being reached through whichever record
 * happens to own them.
 */
interface EventRepository {

    fun observeFor(ownerType: OwnerType, ownerId: Long): Flow<List<Event>>

    suspend fun getFor(ownerType: OwnerType, ownerId: Long): List<Event>

    suspend fun upsert(event: Event): Long

    suspend fun delete(eventId: Long)

    /**
     * Place names already used in this tree.
     *
     * Offered as suggestions while typing, which keeps spellings consistent — and
     * consistent spelling is what lets places group in searches and reports.
     */
    fun observeKnownPlaces(treeId: Long): Flow<List<String>>
}
