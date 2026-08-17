package com.familytree.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {

    /**
     * The case this exists for: what comes back from the platform is a *resolved* locale,
     * not the tag that was set. Asking for `en` can return `en-US`, and a device set to
     * `ar-EG` still means Arabic — matching the whole tag would report "system" for a
     * language the user explicitly chose, and the picker would show the wrong radio.
     */
    @Test
    fun `a region-qualified tag matches its language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.TURKISH, AppLanguage.fromTag("tr-TR"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar-EG"))
    }

    @Test
    fun `a bare tag matches, whatever its case`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN"))
        assertEquals(AppLanguage.TURKISH, AppLanguage.fromTag("Tr"))
    }

    /** No preference, an empty tag and a language the app does not ship all mean "follow the device". */
    @Test
    fun `anything unrecognised falls back to the system language`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de-DE"))
    }

    /**
     * Locks the shipped set against `app/src/main/res/xml/locales_config.xml` and the
     * `resourceConfigurations` in the app's build file. If the three ever disagree, the
     * in-app picker and the Android 13 system picker offer different languages.
     */
    @Test
    fun `the shipped languages are exactly those with resources`() {
        val tags = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(listOf("en", "tr", "ar"), tags)
    }

    /** SYSTEM is labelled with a translated string; every real language names itself. */
    @Test
    fun `each language carries its own name`() {
        assertEquals("", AppLanguage.SYSTEM.endonym)
        assertTrue(AppLanguage.entries.filter { it.tag != null }.all { it.endonym.isNotBlank() })
    }
}
