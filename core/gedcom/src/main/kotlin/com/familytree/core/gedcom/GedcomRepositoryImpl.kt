package com.familytree.core.gedcom

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.domain.repository.GedcomRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class GedcomRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: GedcomImporter,
    private val exporter: GedcomExporter,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : GedcomRepository {

    override suspend fun importFrom(uri: String, title: String): Long = withContext(ioDispatcher) {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw InvalidGedcomException("This file could not be opened.")
        stream.use { importer.import(it, title) }
    }

    override suspend fun exportTo(treeId: Long, uri: String) = withContext(ioDispatcher) {
        val stream = context.contentResolver.openOutputStream(Uri.parse(uri))
            ?: throw IllegalStateException("This location could not be opened for writing.")
        stream.use { exporter.export(treeId, it) }
    }

    /**
     * Reads the display name the provider reports, falling back to the last path
     * segment for `file://` and other providers that do not implement the query.
     */
    override fun suggestTitle(uri: String): String? {
        val parsed = Uri.parse(uri)
        val displayName = runCatching {
            context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: parsed.lastPathSegment

        return displayName
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GedcomModule {
    @Binds
    @Singleton
    abstract fun bindsGedcomRepository(impl: GedcomRepositoryImpl): GedcomRepository
}
