package com.xtwitter.blocker.data

object PrefsConstants {
    const val PREFS_NAME = "x_comment_blocker_prefs"

    const val KEY_ENABLED = "enabled"
    const val KEY_BLOCK_PROMOTED = "block_promoted"
    const val KEY_CHECK_USERNAME = "check_username"
    const val KEY_BLOCK_SPECIAL_CHARS = "block_special_chars"
    const val KEY_BLOCK_EMOJI = "block_emoji"
    const val KEY_BLOCK_GROK = "block_grok"
    const val KEY_CLOUD_SYNC = "cloud_sync"

    const val KEY_USER_KEYWORDS = "user_keywords"
    const val KEY_CLOUD_KEYWORDS = "cloud_keywords"
    const val KEY_DISABLED_CLOUD_KEYWORDS = "disabled_cloud_keywords"
    const val KEY_WHITELIST = "whitelist"
    const val KEY_BLOCKED_COUNT = "blocked_count"
    const val KEY_LAST_SYNC_TIME = "last_sync_time"

    const val AUTHORITY = "com.xtwitter.blocker.config"
    const val METHOD_GET_CONFIG = "getConfig"
    const val METHOD_INCREMENT_BLOCKED = "incrementBlocked"

    const val CLOUD_KEYWORDS_URL = "https://raw.githubusercontent.com/amahteru/x-comment-blocker/main/keywords.txt"
    const val CLOUD_KEYWORDS_CDN_URL = "https://fastly.jsdelivr.net/gh/amahteru/x-comment-blocker@main/keywords.txt"
}
