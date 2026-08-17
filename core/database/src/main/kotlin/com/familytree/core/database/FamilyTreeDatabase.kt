package com.familytree.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.familytree.core.database.dao.EventDao
import com.familytree.core.database.dao.ExtensionDao
import com.familytree.core.database.dao.FamilyDao
import com.familytree.core.database.dao.MaintenanceDao
import com.familytree.core.database.dao.MediaDao
import com.familytree.core.database.dao.NoteDao
import com.familytree.core.database.dao.PersonDao
import com.familytree.core.database.dao.RepositoryDao
import com.familytree.core.database.dao.SourceDao
import com.familytree.core.database.dao.SubmitterDao
import com.familytree.core.database.dao.TreeDao
import com.familytree.core.database.entity.AddressEntity
import com.familytree.core.database.entity.EventEntity
import com.familytree.core.database.entity.ExtensionEntity
import com.familytree.core.database.entity.FamilyEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.database.entity.HeaderEntity
import com.familytree.core.database.entity.MediaEntity
import com.familytree.core.database.entity.MediaFolderEntity
import com.familytree.core.database.entity.MediaLinkEntity
import com.familytree.core.database.entity.NoteEntity
import com.familytree.core.database.entity.NoteLinkEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.PersonNameEntity
import com.familytree.core.database.entity.RepositoryEntity
import com.familytree.core.database.entity.RepositoryRefEntity
import com.familytree.core.database.entity.SourceCitationEntity
import com.familytree.core.database.entity.SourceEntity
import com.familytree.core.database.entity.SubmitterEntity
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.database.entity.TreeShareEntity

@Database(
    entities = [
        TreeEntity::class,
        MediaFolderEntity::class,
        TreeShareEntity::class,
        HeaderEntity::class,
        PersonEntity::class,
        PersonNameEntity::class,
        FamilyEntity::class,
        FamilyMemberEntity::class,
        EventEntity::class,
        AddressEntity::class,
        NoteEntity::class,
        NoteLinkEntity::class,
        MediaEntity::class,
        MediaLinkEntity::class,
        SourceEntity::class,
        SourceCitationEntity::class,
        RepositoryEntity::class,
        RepositoryRefEntity::class,
        SubmitterEntity::class,
        ExtensionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FamilyTreeDatabase : RoomDatabase() {
    abstract fun treeDao(): TreeDao
    abstract fun personDao(): PersonDao
    abstract fun familyDao(): FamilyDao
    abstract fun eventDao(): EventDao
    abstract fun noteDao(): NoteDao
    abstract fun mediaDao(): MediaDao
    abstract fun sourceDao(): SourceDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun submitterDao(): SubmitterDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val NAME = "familytree.db"
    }
}
