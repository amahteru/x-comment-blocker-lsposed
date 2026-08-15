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
        val normalized = SpamCharCleaner.normalizeText(text)

        triePattern?.let { pattern ->
            if (pattern.matcher(normalized).find()) return true
        }

        for (regex in customRegexes) {
            if (regex.matcher(normalized).find()) return true
        }

        // Secondary check: text without whitespace and common delimiter characters (catches "找.萢友", "微 信", "同-城-约")
        // Aligned with desktop extension: userName.replaceAll(/[\s_.\-]+/gv, '')
        val withoutSeparators = SpamCharCleaner.removeSeparators(normalized)
        if (withoutSeparators.isNotEmpty() && withoutSeparators.length != normalized.length) {
            triePattern?.let { pattern ->
                if (pattern.matcher(withoutSeparators).find()) return true
            }
            for (regex in customRegexes) {
                if (regex.matcher(withoutSeparators).find()) return true
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
