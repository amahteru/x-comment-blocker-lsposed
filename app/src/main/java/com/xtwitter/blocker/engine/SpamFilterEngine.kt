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

        return false
    }

    /**
     * Core filter method evaluating all criteria.
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

        if (isWhitelisted(screenName)) {
            return FilterResult.Pass
        }

        if (hasGrokCard && isBlockGrok) {
            return FilterResult.Blocked(FilterResult.BlockReason.GROK_CARD)
        }

        if (isBlockSpecialChars) {
            if (SpamCharCleaner.containsSpamChars(fullText) ||
                (isCheckUsername && SpamCharCleaner.containsSpamChars(name))
            ) {
                return FilterResult.Blocked(FilterResult.BlockReason.SPECIAL_CHARS)
            }
        }

        if (isBlockEmoji && !fullText.isNullOrEmpty()) {
            if (SpamCharCleaner.containsEmoji(fullText)) {
                return FilterResult.Blocked(FilterResult.BlockReason.EMOJI_SPAM)
            }
        }

        if (isCheckUsername) {
            if (matchesKeywords(name)) {
                return FilterResult.Blocked(FilterResult.BlockReason.USERNAME_MATCH, name)
            }
            if (matchesKeywords(screenName)) {
                return FilterResult.Blocked(FilterResult.BlockReason.USERNAME_MATCH, screenName)
            }
        }

        if (matchesKeywords(fullText)) {
            return FilterResult.Blocked(FilterResult.BlockReason.KEYWORD_MATCH)
        }

        return FilterResult.Pass
    }

    companion object {
        val instance by lazy { SpamFilterEngine() }
    }
}
