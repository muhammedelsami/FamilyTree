package com.familytree.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.familytree.core.model.AppFont
import com.familytree.core.model.AppSettings
import com.familytree.core.model.DiagramSettings
import com.familytree.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes [AppSettings].
 *
 * Every write goes through a `copy` of the whole settings object rather than exposing
 * one setter per key, so a caller can never persist a half-updated combination — which
 * matters for [DiagramSettings], whose fields constrain each other.
 */
@Singleton
class SettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toAppSettings())
            prefs.write(updated)
        }
    }

    private fun Preferences.toAppSettings() = AppSettings(
        autoSave = this[Keys.AUTO_SAVE] ?: true,
        loadTreeAtStartup = this[Keys.LOAD_TREE_AT_STARTUP] ?: true,
        expertMode = this[Keys.EXPERT_MODE] ?: false,
        openTreeId = this[Keys.OPEN_TREE_ID]?.takeIf { it > 0L },
        diagram = DiagramSettings(
            ancestors = this[Keys.DIAGRAM_ANCESTORS] ?: 3,
            greatUncles = this[Keys.DIAGRAM_GREAT_UNCLES] ?: 2,
            descendants = this[Keys.DIAGRAM_DESCENDANTS] ?: 3,
            siblingsNephews = this[Keys.DIAGRAM_SIBLINGS] ?: 2,
            unclesCousins = this[Keys.DIAGRAM_COUSINS] ?: 1,
            showSpouses = this[Keys.DIAGRAM_SPOUSES] ?: true,
            showNumbers = this[Keys.DIAGRAM_NUMBERS] ?: true,
            showDuplicateLines = this[Keys.DIAGRAM_DUPLICATES] ?: false,
        ),
        birthdayNotifications = this[Keys.BIRTHDAY_NOTIFICATIONS] ?: true,
        notifyTime = this[Keys.NOTIFY_TIME] ?: "09:00",
        backupEnabled = this[Keys.BACKUP_ENABLED] ?: false,
        backupFolderUri = this[Keys.BACKUP_FOLDER_URI],
        shareAgreementAccepted = this[Keys.SHARE_AGREEMENT] ?: false,
        premium = this[Keys.PREMIUM] ?: false,
        theme = this[Keys.THEME]?.let {
            runCatching { ThemePreference.valueOf(it) }.getOrNull()
        } ?: ThemePreference.SYSTEM,
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        font = this[Keys.FONT]?.let {
            runCatching { AppFont.valueOf(it) }.getOrNull()
        } ?: AppFont.ALEXANDRIA,
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(value: AppSettings) {
        this[Keys.AUTO_SAVE] = value.autoSave
        this[Keys.LOAD_TREE_AT_STARTUP] = value.loadTreeAtStartup
        this[Keys.EXPERT_MODE] = value.expertMode
        this[Keys.OPEN_TREE_ID] = value.openTreeId ?: 0L
        val diagram = value.diagram.normalised()
        this[Keys.DIAGRAM_ANCESTORS] = diagram.ancestors
        this[Keys.DIAGRAM_GREAT_UNCLES] = diagram.greatUncles
        this[Keys.DIAGRAM_DESCENDANTS] = diagram.descendants
        this[Keys.DIAGRAM_SIBLINGS] = diagram.siblingsNephews
        this[Keys.DIAGRAM_COUSINS] = diagram.unclesCousins
        this[Keys.DIAGRAM_SPOUSES] = diagram.showSpouses
        this[Keys.DIAGRAM_NUMBERS] = diagram.showNumbers
        this[Keys.DIAGRAM_DUPLICATES] = diagram.showDuplicateLines
        this[Keys.BIRTHDAY_NOTIFICATIONS] = value.birthdayNotifications
        this[Keys.NOTIFY_TIME] = value.notifyTime
        this[Keys.BACKUP_ENABLED] = value.backupEnabled
        value.backupFolderUri?.let { this[Keys.BACKUP_FOLDER_URI] = it }
            ?: remove(Keys.BACKUP_FOLDER_URI)
        this[Keys.SHARE_AGREEMENT] = value.shareAgreementAccepted
        this[Keys.PREMIUM] = value.premium
        this[Keys.THEME] = value.theme.name
        this[Keys.DYNAMIC_COLOR] = value.dynamicColor
        this[Keys.FONT] = value.font.name
    }

    private object Keys {
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val LOAD_TREE_AT_STARTUP = booleanPreferencesKey("load_tree_at_startup")
        val EXPERT_MODE = booleanPreferencesKey("expert_mode")
        val OPEN_TREE_ID = longPreferencesKey("open_tree_id")
        val DIAGRAM_ANCESTORS = intPreferencesKey("diagram_ancestors")
        val DIAGRAM_GREAT_UNCLES = intPreferencesKey("diagram_great_uncles")
        val DIAGRAM_DESCENDANTS = intPreferencesKey("diagram_descendants")
        val DIAGRAM_SIBLINGS = intPreferencesKey("diagram_siblings")
        val DIAGRAM_COUSINS = intPreferencesKey("diagram_cousins")
        val DIAGRAM_SPOUSES = booleanPreferencesKey("diagram_spouses")
        val DIAGRAM_NUMBERS = booleanPreferencesKey("diagram_numbers")
        val DIAGRAM_DUPLICATES = booleanPreferencesKey("diagram_duplicates")
        val BIRTHDAY_NOTIFICATIONS = booleanPreferencesKey("birthday_notifications")
        val NOTIFY_TIME = stringPreferencesKey("notify_time")
        val BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val SHARE_AGREEMENT = booleanPreferencesKey("share_agreement")
        val PREMIUM = booleanPreferencesKey("premium")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FONT = stringPreferencesKey("font")
    }
}
