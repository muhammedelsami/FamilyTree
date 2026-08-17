package com.familytree.core.data.repository

import com.familytree.core.datastore.SettingsDataSource
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataSource: SettingsDataSource,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataSource.settings

    override suspend fun update(transform: (AppSettings) -> AppSettings) =
        dataSource.update(transform)
}
