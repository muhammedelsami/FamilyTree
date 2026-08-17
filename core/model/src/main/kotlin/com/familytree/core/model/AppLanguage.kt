package com.familytree.core.model

/**
 * A language the application ships with.
 *
 * The list mirrors `app/src/main/res/xml/locales_config.xml`, which is what Android 13+
 * reads to build its own per-app language picker — the two have to agree, or the in-app
 * chooser would offer something the system does not, or vice versa.
 *
 * @property tag the BCP 47 tag, or null for [SYSTEM], where "no preference" is the value:
 *   an empty locale list means the app follows the device, and that is different from
 *   pinning it to whatever the device happens to be set to today.
 * @property endonym the language's name in that language. A language picker is the one
 *   screen a user may reach *because* they cannot read the current language, so the
 *   options must not be translated — someone stranded in Arabic still recognises
 *   "Türkçe".
 */
enum class AppLanguage(val tag: String?, val endonym: String) {
    SYSTEM(null, ""),
    ENGLISH("en", "English"),
    TURKISH("tr", "Türkçe"),
    ARABIC("ar", "العربية"),
    ;

    companion object {
        /**
         * The entry matching [tag], or [SYSTEM] when nothing does.
         *
         * Matches on the language subtag alone, because the value coming back from the
         * platform is a resolved locale rather than the tag that was set: asking for `en`
         * can return `en-US`, and a device set to `ar-EG` still means Arabic here.
         */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.lowercase()?.takeIf { it.isNotEmpty() }
                ?: return SYSTEM
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
