package com.xtwitter.blocker.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import com.xtwitter.blocker.engine.SpamFilterEngine
import de.robv.android.xposed.XSharedPreferences

class ConfigManager private constructor(private val context: Context?) {

    private var xPrefs: XSharedPreferences? = null
    private var normalPrefs: SharedPreferences? = null

    init {
        if (context != null) {
            normalPrefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Initializes for Xposed context inside hooked target application.
     */
    constructor() : this(null) {
        try {
            xPrefs = XSharedPreferences("com.xtwitter.blocker", PrefsConstants.PREFS_NAME)
            xPrefs?.makeWorldReadable()
        } catch (_: Throwable) {}
    }

    fun loadToEngine(engine: SpamFilterEngine = SpamFilterEngine.instance, targetContext: Context? = null) {
        // Priority 1: ContentProvider from hooked process
        if (targetContext != null) {
            try {
                val uri = Uri.parse("content://${PrefsConstants.AUTHORITY}")
                val bundle: Bundle? = targetContext.contentResolver.call(uri, PrefsConstants.METHOD_GET_CONFIG, null, null)
                if (bundle != null) {
                    engine.isEnabled = bundle.getBoolean(PrefsConstants.KEY_ENABLED, true)
                    engine.isBlockPromoted = bundle.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true)
                    engine.isCheckUsername = bundle.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true)
                    engine.isBlockSpecialChars = bundle.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false)
                    engine.isBlockEmoji = bundle.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false)
                    engine.isBlockGrok = bundle.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false)

                    val userKws = bundle.getString(PrefsConstants.KEY_USER_KEYWORDS, "")
                    val cloudKws = bundle.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "")
                    val disabled = bundle.getStringArrayList(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS)?.toSet() ?: emptySet()
                    val whitelist = bundle.getString(PrefsConstants.KEY_WHITELIST, "")

                    engine.updateKeywords(cloudKws, userKws, disabled)
                    engine.updateWhitelist(whitelist?.lines())
                    return
                }
            } catch (_: Throwable) {}
        }

        // Priority 2: XSharedPreferences
        xPrefs?.reload()
        val prefs = normalPrefs

        val isEnabled = prefs?.getBoolean(PrefsConstants.KEY_ENABLED, true)
            ?: xPrefs?.getBoolean(PrefsConstants.KEY_ENABLED, true) ?: true
        val isBlockPromoted = prefs?.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true)
            ?: xPrefs?.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true) ?: true
        val isCheckUsername = prefs?.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true)
            ?: xPrefs?.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true) ?: true
        val isBlockSpecialChars = prefs?.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false)
            ?: xPrefs?.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false) ?: false
        val isBlockEmoji = prefs?.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false)
            ?: xPrefs?.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false) ?: false
        val isBlockGrok = prefs?.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false)
            ?: xPrefs?.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false) ?: false

        val userKeywords = prefs?.getString(PrefsConstants.KEY_USER_KEYWORDS, "")
            ?: xPrefs?.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""
        val cloudKeywords = prefs?.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "")
            ?: xPrefs?.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
        val disabledSet = prefs?.getStringSet(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, emptySet())
            ?: xPrefs?.getStringSet(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, emptySet()) ?: emptySet()
        val whitelist = prefs?.getString(PrefsConstants.KEY_WHITELIST, "")
            ?: xPrefs?.getString(PrefsConstants.KEY_WHITELIST, "") ?: ""

        engine.isEnabled = isEnabled
        engine.isBlockPromoted = isBlockPromoted
        engine.isCheckUsername = isCheckUsername
        engine.isBlockSpecialChars = isBlockSpecialChars
        engine.isBlockEmoji = isBlockEmoji
        engine.isBlockGrok = isBlockGrok

        engine.updateKeywords(cloudKeywords, userKeywords, disabledSet)
        engine.updateWhitelist(whitelist.lines())
    }

    companion object {
        fun fromContext(context: Context): ConfigManager = ConfigManager(context)
        fun forXposed(): ConfigManager = ConfigManager()
    }
}
