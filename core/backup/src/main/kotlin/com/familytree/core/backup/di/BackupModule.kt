package com.familytree.core.backup.di

import com.familytree.core.backup.BackupRepositoryImpl
import com.familytree.core.backup.ShareRepositoryImpl
import com.familytree.core.domain.repository.BackupRepository
import com.familytree.core.domain.repository.ShareRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindsBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindsShareRepository(impl: ShareRepositoryImpl): ShareRepository
}
