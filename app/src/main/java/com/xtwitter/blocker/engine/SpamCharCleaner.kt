package com.xtwitter.blocker.engine

import java.util.regex.Pattern

object SpamCharCleaner {

    // Matches invisible characters / zero-width characters
    // \u200B (ZWSP), \u200C (ZWNJ), \u200D (ZWJ), \uFEFF (BOM), \u00AD (Soft Hyphen), \u2060-\u206F, \u180E
    private val INVISIBLE_CHARS_REGEX: Pattern = Pattern.compile(
        "[\\u200B-\\u200F\\uFEFF\\u00AD\\u2060-\\u206F\\u180E\\uFFF9-\\uFFFB]"
    )

    // Matches mathematical alphanumeric, phonetic extensions, enclosed alphanumerics, zalgo diacritics
    // Commonly used by spam bots to bypass keyword detection (e.g. 𝑻𝒆𝒔𝒕, 𝕿𝖊𝖘𝖙, ᴛᴇsᴛ, ⓣⓔⓢⓣ, ｔｅｓｔ)
    private val SPAM_CHARS_REGEX: Pattern = Pattern.compile(
        "[\\u02B0-\\u02FF\\u0300-\\u036F\\u0F00-\\u0FFF\\u1D00-\\u1DBF\\u2070-\\u209F\\u2100-\\u214F\\u2460-\\u24FF\\uA980-\\uA9DF\\uAA00-\\uAADF\\uFF01-\\uFF5E\\x{13000}-\\x{1342F}\\x{1D400}-\\x{1D7FF}]"
    )

    // Matches standard Unicode Emojis
    private val EMOJI_REGEX: Pattern = Pattern.compile(
        "[\\x{1F300}-\\x{1FAFF}\\x{1F1E6}-\\x{1F1FF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}\\x{2B50}\\x{2B55}]"
    )

    private val SCREEN_NAME_PATTERN: Pattern = Pattern.compile(
        "(?:^|/|@)([a-zA-Z0-9_]{1,15})(?:/|\\?|$)"
    )

    /**
     * Removes invisible / zero-width code points from input string.
     */
    fun removeInvisibleChars(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return INVISIBLE_CHARS_REGEX.matcher(input).replaceAll("")
    }

    /**
     * Cleans up and normalizes a Twitter handle/screen_name.
     */
    fun extractCleanScreenName(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val cleaned = removeInvisibleChars(input).trim()
        val matcher = SCREEN_NAME_PATTERN.matcher(cleaned)
        if (matcher.find()) {
            return matcher.group(1)?.lowercase() ?: ""
        }
        val firstSegment = cleaned.trimStart('@', '/').split('/', '?').firstOrNull() ?: ""
        return firstSegment.lowercase()
    }

    /**
     * Checks if string contains spam/obfuscated unicode characters.
     */
    fun containsSpamChars(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false
        return SPAM_CHARS_REGEX.matcher(input).find()
    }

    /**
     * Checks if string contains emojis.
     */
    fun containsEmoji(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false
        return EMOJI_REGEX.matcher(input).find()
    }

    /**
     * Normalizes text for keyword matching: strips invisible characters and lowers case.
     */
    fun normalizeText(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return removeInvisibleChars(input).lowercase()
    }
}
