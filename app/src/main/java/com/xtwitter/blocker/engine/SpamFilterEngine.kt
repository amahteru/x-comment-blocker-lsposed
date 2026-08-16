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

    fun getMatchingRule(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val withoutInvisible = SpamCharCleaner.removeInvisibleChars(text)
        val normalized = SpamCharCleaner.normalizeText(text)

        for (regex in customRegexes) {
            if (regex.matcher(withoutInvisible).find()) return "CustomRegex: ${regex.pattern()}"
        }
        triePattern?.let { pattern ->
            val m = pattern.matcher(normalized)
            if (m.find()) return "TrieKeyword: [${m.group()}]"
        }
        for (regex in customRegexes) {
            if (regex.matcher(normalized).find()) return "CustomRegexNorm: ${regex.pattern()}"
        }
        if (withoutInvisible != normalized) {
            triePattern?.let { pattern ->
                val m = pattern.matcher(withoutInvisible)
                if (m.find()) return "TrieKeywordRaw: [${m.group()}]"
            }
        }
        val withoutSeparatorsNorm = SpamCharCleaner.removeSeparators(normalized)
        if (withoutSeparatorsNorm.isNotEmpty() && withoutSeparatorsNorm.length != normalized.length) {
            triePattern?.let { pattern ->
                val m = pattern.matcher(withoutSeparatorsNorm)
                if (m.find()) return "TrieKeywordNoSepNorm: [${m.group()}]"
            }
            for (regex in customRegexes) {
                if (regex.matcher(withoutSeparatorsNorm).find()) return "CustomRegexNoSepNorm: ${regex.pattern()}"
            }
        }
        val withoutSeparatorsRaw = SpamCharCleaner.removeSeparators(withoutInvisible)
        if (withoutSeparatorsRaw.isNotEmpty() && withoutSeparatorsRaw != withoutSeparatorsNorm && withoutSeparatorsRaw.length != withoutInvisible.length) {
            triePattern?.let { pattern ->
                val m = pattern.matcher(withoutSeparatorsRaw)
                if (m.find()) return "TrieKeywordNoSepRaw: [${m.group()}]"
            }
            for (regex in customRegexes) {
                if (regex.matcher(withoutSeparatorsRaw).find()) return "CustomRegexNoSepRaw: ${regex.pattern()}"
            }
        }
        return null
    }

    /**
     * Checks if a specific text (tweet text, name, etc.) matches any keyword rule.
     */
    fun matchesKeywords(text: String?): Boolean = getMatchingRule(text) != null

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
