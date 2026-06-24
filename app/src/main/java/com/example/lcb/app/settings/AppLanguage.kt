package com.example.lcb.app.settings

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
    INDONESIAN("in", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
    KOREAN("ko", "Korean", "한국어", "🇰🇷");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code.isNullOrEmpty()) return SYSTEM
            return entries.firstOrNull { it.code == code } ?: SYSTEM
        }
    }
}
