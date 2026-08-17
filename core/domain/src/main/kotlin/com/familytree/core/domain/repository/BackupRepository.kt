package com.familytree.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** One saved archive on this device. */
data class BackupFile(
    val id: String,
    val treeId: Long,
    val title: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val personCount: Int,
    val mediaCount: Int,
)

/** Saving and restoring whole trees. */
interface BackupRepository {

    /** Automatic backups held on this device, newest first. */
    fun observeBackups(treeId: Long): Flow<List<BackupFile>>

    /**
     * Saves an archive into the app's own storage and prunes older ones.
     *
     * Kept on the device rather than asked for a location each time, because a backup
     * that needs a decision is a backup nobody takes.
     */
    suspend fun backUp(treeId: Long): Result<BackupFile>

    /** Writes an archive to a location the user chose, for keeping off the device. */
    suspend fun exportTo(treeId: Long, uri: String): Result<Unit>

    suspend fun restore(backupId: String): Result<Long>

    suspend fun restoreFrom(uri: String, fallbackTitle: String): Result<Long>

    suspend fun delete(backupId: String): Result<Unit>

    /** Backs up every tree that has automatic backups switched on. */
    suspend fun backUpEnabledTrees(): Result<Int>

    suspend fun setAutomaticBackup(treeId: Long, enabled: Boolean)

    /** How many archives are kept per tree before the oldest is dropped. */
    val keptPerTree: Int
}
