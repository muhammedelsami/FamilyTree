package com.familytree.core.database

import androidx.room.TypeConverter
import com.familytree.core.model.MediaFolder
import com.familytree.core.model.MemberRole
import com.familytree.core.model.OwnerType
import com.familytree.core.model.Pedigree
import com.familytree.core.model.Sex

/**
 * Enums are stored by `name`, not ordinal: reordering an enum then becomes a harmless
 * source change instead of silently reinterpreting every existing row.
 */
class Converters {

    @TypeConverter fun sexToString(value: Sex?): String? = value?.name

    @TypeConverter fun stringToSex(value: String?): Sex? =
        value?.let { runCatching { Sex.valueOf(it) }.getOrDefault(Sex.NONE) }

    @TypeConverter fun ownerTypeToString(value: OwnerType?): String? = value?.name

    @TypeConverter fun stringToOwnerType(value: String?): OwnerType? =
        value?.let { runCatching { OwnerType.valueOf(it) }.getOrNull() }

    @TypeConverter fun memberRoleToString(value: MemberRole?): String? = value?.name

    @TypeConverter fun stringToMemberRole(value: String?): MemberRole? =
        value?.let { runCatching { MemberRole.valueOf(it) }.getOrNull() }

    @TypeConverter fun pedigreeToString(value: Pedigree?): String? = value?.name

    @TypeConverter fun stringToPedigree(value: String?): Pedigree? =
        value?.let { runCatching { Pedigree.valueOf(it) }.getOrNull() }

    @TypeConverter fun folderKindToString(value: MediaFolder.Kind?): String? = value?.name

    @TypeConverter fun stringToFolderKind(value: String?): MediaFolder.Kind? =
        value?.let { runCatching { MediaFolder.Kind.valueOf(it) }.getOrNull() }
}
