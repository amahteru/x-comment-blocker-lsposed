package com.xtwitter.blocker.engine

import java.util.regex.Pattern

class SpamFilterEngine {

    var isEnabled: Boolean = true
    var isBlockPromoted: Boolean = true
    var isCheckUsername: Boolean = true
    var isBlockSpecialChars: Boolean = false
    var isBlockEmoji: Boolean = false
    var isBlockGrok: Boolean = false

    private var whitelistSet: Set<String> = emptySet()
    private var triePattern: Pattern? = null
    private var customRegexes: List<Pattern> = emptyList()

    fun updateWhitelist(whitelist: Collection<String>?) {
        whitelistSet = whitelist?.map { SpamCharCleaner.extractCleanScreenName(it) }
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()
    }

    fun isWhitelisted(screenName: String?): Boolean {
        if (screenName.isNullOrEmpty()) return false
        val clean = SpamCharCleaner.extractCleanScreenName(screenName)
        return whitelistSet.contains(clean)
    }

    fun hasKeywords(): Boolean = triePattern != null || customRegexes.isNotEmpty()

    fun updateKeywords(
        cloudKeywords: String?,
        userKeywords: String?,
        disabledCloudKeywords: Set<String> = emptySet()
    ) {
        val parsedCloud = RegexTrie.parseKeywords(cloudKeywords)
        val parsedUser = RegexTrie.parseKeywords(userKeywords)

        // Filter out disabled cloud keywords
        val effectiveCloudPlain = parsedCloud.plainKeywords.filterNot { disabledCloudKeywords.contains(it) }
        val allPlainKeywords = (effectiveCloudPlain + parsedUser.plainKeywords).distinct()

        triePattern = RegexTrie.buildTriePattern(allPlainKeywords)
        customRegexes = (parsedCloud.customRegexes + parsedUser.customRegexes).distinct()
    }

    /**
     * Checks if a specific text (tweet text, name, etc.) matches any keyword rule.
     */
    fun matchesKeywords(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val withoutInvisible = SpamCharCleaner.removeInvisibleChars(text)
        val normalized = SpamCharCleaner.normalizeText(text)

        // 1. Check custom regexes on raw text (preserves original emojis like 🈷️, 🍑, ❤️ from being converted to Chinese chars by NFKC)
        for (regex in customRegexes) {
            if (regex.matcher(withoutInvisible).find()) return true
        }

        // 2. Check Trie pattern and custom regexes on NFKC normalized text
        triePattern?.let { pattern ->
            if (pattern.matcher(normalized).find()) return true
        }
        for (regex in customRegexes) {
            if (regex.matcher(normalized).find()) return true
        }

        // 3. If raw text differs from normalized, also check Trie pattern on raw text
        if (withoutInvisible != normalized) {
            triePattern?.let { pattern ->
                if (pattern.matcher(withoutInvisible).find()) return true
            }
        }

        // 4. Secondary check: text without whitespace and common delimiter characters (catches "找.萢友", "微 信", "同-城-约", "母狗（接任务")
        // Aligned with desktop extension: userName.replaceAll(/[\s_.\-]+/gv, '')
        val withoutSeparatorsNorm = SpamCharCleaner.removeSeparators(normalized)
        if (withoutSeparatorsNorm.isNotEmpty() && withoutSeparatorsNorm.length != normalized.length) {
            triePattern?.let { pattern ->
                if (pattern.matcher(withoutSeparatorsNorm).find()) return true
            }
            for (regex in customRegexes) {
                if (regex.matcher(withoutSeparatorsNorm).find()) return true
            }
        }

        val withoutSeparatorsRaw = SpamCharCleaner.removeSeparators(withoutInvisible)
        if (withoutSeparatorsRaw.isNotEmpty() && withoutSeparatorsRaw != withoutSeparatorsNorm && withoutSeparatorsRaw.length != withoutInvisible.length) {
            triePattern?.let { pattern ->
                if (pattern.matcher(withoutSeparatorsRaw).find()) return true
            }
            for (regex in customRegexes) {
                if (regex.matcher(withoutSeparatorsRaw).find()) return true
            }
        }

        return false
    }

    /**
     * Evaluates a tweet/comment against all configured rules.
     * Returns FilterResult.Blocked or FilterResult.Pass.
     */
    fun shouldBlockTweet(
        fullText: String?,
        screenName: String?,
        name: String?,
        isPromoted: Boolean = false,
        hasGrokCard: Boolean = false
    ): FilterResult {
        if (!isEnabled) return FilterResult.Pass
        if (isPromoted && isBlockPromoted) {
            return FilterResult.Blocked(FilterResult.BlockReason.PROMOTED_AD)
        }
        if (isWhitelisted(screenName)) return FilterResult.Pass

        if (isBlockGrok && hasGrokCard) {
            return FilterResult.Blocked(FilterResult.BlockReason.GROK_CARD)
        }

        if (matchesKeywords(fullText)) {
            return FilterResult.Blocked(FilterResult.BlockReason.KEYWORD_MATCH)
        }

        if (isCheckUsername) {
            if (matchesKeywords(name) || matchesKeywords(screenName)) {
                return FilterResult.Blocked(FilterResult.BlockReason.USERNAME_MATCH)
            }
        }

        if (isBlockSpecialChars) {
            if (SpamCharCleaner.containsSpamChars(fullText) ||
                (isCheckUsername && SpamCharCleaner.containsSpamChars(name))) {
                return FilterResult.Blocked(FilterResult.BlockReason.SPECIAL_CHARS)
            }
        }

        if (isBlockEmoji) {
            if (SpamCharCleaner.containsEmoji(fullText) ||
                (isCheckUsername && SpamCharCleaner.containsEmoji(name))) {
                return FilterResult.Blocked(FilterResult.BlockReason.EMOJI_SPAM)
            }
        }

        return FilterResult.Pass
    }

    companion object {
        val instance by lazy { SpamFilterEngine() }
    }
}
