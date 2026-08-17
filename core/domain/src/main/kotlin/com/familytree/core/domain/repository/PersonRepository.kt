package com.familytree.core.domain.repository

import com.familytree.core.model.Event
import com.familytree.core.model.Person
import com.familytree.core.model.PersonDetails
import com.familytree.core.model.PersonName
import com.familytree.core.model.PersonSummary
import kotlinx.coroutines.flow.Flow

interface PersonRepository {

    fun observePeople(treeId: Long): Flow<List<Person>>

    /**
     * Everything the person list renders and sorts by, precomputed once per person so
     * that searching and re-sorting never touch the database or re-parse dates.
     */
    fun observePersonSummaries(treeId: Long): Flow<List<PersonSummary>>

    fun observePerson(personId: Long): Flow<Person?>

    fun observePersonDetails(personId: Long): Flow<PersonDetails?>

    suspend fun getPerson(personId: Long): Person?

    suspend fun getNames(personId: Long): List<PersonName>

    suspend fun getEvents(personId: Long): List<Event>

    suspend fun createPerson(person: Person, names: List<PersonName> = emptyList()): Long

    suspend fun updatePerson(person: Person)

    /** Removes the person along with every attachment and family membership. */
    suspend fun deletePerson(personId: Long)

    suspend fun upsertName(name: PersonName): Long

    suspend fun deleteName(nameId: Long)

}
