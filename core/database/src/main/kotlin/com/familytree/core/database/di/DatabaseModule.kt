package com.familytree.core.database.di

import android.content.Context
import androidx.room.Room
import com.familytree.core.database.FamilyTreeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): FamilyTreeDatabase =
        Room.databaseBuilder(context, FamilyTreeDatabase::class.java, FamilyTreeDatabase.NAME)
            .build()

    @Provides fun providesTreeDao(db: FamilyTreeDatabase) = db.treeDao()

    @Provides fun providesPersonDao(db: FamilyTreeDatabase) = db.personDao()

    @Provides fun providesFamilyDao(db: FamilyTreeDatabase) = db.familyDao()

    @Provides fun providesEventDao(db: FamilyTreeDatabase) = db.eventDao()

    @Provides fun providesNoteDao(db: FamilyTreeDatabase) = db.noteDao()

    @Provides fun providesMediaDao(db: FamilyTreeDatabase) = db.mediaDao()

    @Provides fun providesSourceDao(db: FamilyTreeDatabase) = db.sourceDao()

    @Provides fun providesRepositoryDao(db: FamilyTreeDatabase) = db.repositoryDao()

    @Provides fun providesSubmitterDao(db: FamilyTreeDatabase) = db.submitterDao()

    @Provides fun providesExtensionDao(db: FamilyTreeDatabase) = db.extensionDao()

    @Provides fun providesMaintenanceDao(db: FamilyTreeDatabase) = db.maintenanceDao()
}
