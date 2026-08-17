package com.familytree.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.familytree.core.common.di.ApplicationScope
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val FILE_NAME = "settings"

    @Provides
    @Singleton
    fun providesPreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(FtDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // A corrupted preferences file should reset the settings, not brick the app —
        // every value here has a sane default.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope + ioDispatcher,
    ) {
        context.preferencesDataStoreFile(FILE_NAME)
    }
}
