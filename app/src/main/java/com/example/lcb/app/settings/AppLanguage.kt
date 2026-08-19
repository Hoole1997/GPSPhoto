package com.example.lcb.app.settings

import java.util.Locale

/**
 * Supported app languages (mainstream + populous countries).
 * [code] is a BCP-47 language tag used with AppCompatDelegate locales;
 * empty code means "follow system".
 */
enum class AppLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val flag: String
) {
    SYSTEM("", "System default", "System default", "🌐"),
    ENGLISH("en", "English", "English", "🇬🇧"),
    CHINESE("zh", "Chinese", "简体中文", "🇨🇳"),
    HINDI("hi", "Hindi", "हिन्दी", "🇮🇳"),
    SPANISH("es", "Spanish", "Español", "🇪🇸"),
    ARABIC("ar", "Arabic", "العربية", "🇸🇦"),
    PORTUGUESE("pt", "Portuguese", "Português", "🇧🇷"),
    RUSSIAN("ru", "Russian", "Русский", "🇷🇺"),
    JAPANESE("ja", "Japanese", "日本語", "🇯🇵"),
    GERMAN("de", "German", "Deutsch", "🇩🇪"),
    FRENCH("fr", "French", "Français", "🇫🇷"),
    KOREAN("ko", "Korean", "한국어", "🇰🇷");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code.isNullOrEmpty()) return SYSTEM
            val languageCode = code
                .substringBefore('-')
                .substringBefore('_')
                .lowercase(Locale.ROOT)
            return entries.firstOrNull { it.code == languageCode } ?: SYSTEM
        }
    }
}
