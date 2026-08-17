package com.familytree.core.model

/**
 * Application-wide preferences, persisted in DataStore.
 *
 * Tree *content* lives in Room; this holds only the knobs that apply across trees.
 */
data class AppSettings(
    /** Write edits to the database immediately instead of waiting for an explicit save. */
    val autoSave: Boolean = true,
    /** Reopen the last tree on launch. Cleared automatically after a crash. */
    val loadTreeAtStartup: Boolean = true,
    /** Reveals sources, repositories, submitters, raw tags and ID editing. */
    val expertMode: Boolean = false,
    val openTreeId: Long? = null,
    val diagram: DiagramSettings = DiagramSettings(),
    val birthdayNotifications: Boolean = true,
    /** `HH:mm`, when birthday reminders fire. */
    val notifyTime: String = "09:00",
    val backupEnabled: Boolean = false,
    /** Persisted SAF tree URI of the backup folder. */
    val backupFolderUri: String? = null,
    /** The user has acknowledged that sharing uploads personal data. */
    val shareAgreementAccepted: Boolean = false,
    val premium: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /**
     * Follow the wallpaper palette on Android 12+.
     *
     * Off by default. The brand palette is a designed thing — one evergreen over near-neutral
     * greys — and leaving this on meant almost nobody ever saw it: the app took whatever hue
     * the user's wallpaper happened to extract, so it had no look of its own and no two
     * screenshots matched. Anyone who prefers their wallpaper's colours can still say so.
     */
    val dynamicColor: Boolean = false,
    val font: AppFont = AppFont.ALEXANDRIA,
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }
