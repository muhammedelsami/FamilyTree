package com.familytree.core.data.di

import com.familytree.core.data.repository.EventRepositoryImpl
import com.familytree.core.data.repository.FamilyRepositoryImpl
import com.familytree.core.data.repository.MediaRepositoryImpl
import com.familytree.core.data.repository.PersonRepositoryImpl
import com.familytree.core.data.repository.SettingsRepositoryImpl
import com.familytree.core.data.repository.TreeRepositoryImpl
import com.familytree.core.domain.repository.EventRepository
import com.familytree.core.domain.repository.FamilyRepository
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.domain.repository.TreeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The only place implementations are named.
 *
 * Feature modules depend on `core:domain` alone, so nothing above this line can reach
 * into Room or DataStore even by accident.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindsTreeRepository(impl: TreeRepositoryImpl): TreeRepository

    @Binds
    @Singleton
    abstract fun bindsPersonRepository(impl: PersonRepositoryImpl): PersonRepository

    @Binds
    @Singleton
    abstract fun bindsFamilyRepository(impl: FamilyRepositoryImpl): FamilyRepository

    @Binds
    @Singleton
    abstract fun bindsEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindsMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindsSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
