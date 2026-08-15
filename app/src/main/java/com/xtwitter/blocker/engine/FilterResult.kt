package com.xtwitter.blocker.engine

sealed class FilterResult {
    data object Pass : FilterResult()
    data class Blocked(val reason: BlockReason, val matchedRule: String? = null) : FilterResult()

    enum class BlockReason {
        PROMOTED_AD,
        KEYWORD_MATCH,
        USERNAME_MATCH,
        SPECIAL_CHARS,
        EMOJI_SPAM,
        GROK_CARD
    }
}
