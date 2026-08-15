package com.xtwitter.blocker.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class ConfigQueryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_GET_CONFIG) {
            val prefs = ConfigManager.getPreferences(context)
            var cloudKeywords = prefs.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
            var userKeywords = prefs.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""

            if (cloudKeywords.isEmpty() && userKeywords.isEmpty()) {
                val candidateFiles = listOf(
                    java.io.File("/data/user_de/0/com.xtwitter.blocker/shared_prefs/${PrefsConstants.PREFS_NAME}.xml"),
                    java.io.File("/data/data/com.xtwitter.blocker/shared_prefs/${PrefsConstants.PREFS_NAME}.xml")
                )
                for (file in candidateFiles) {
                    if (file.exists() && file.canRead()) {
                        try {
                            val parsed = ConfigManager.parsePrefsXml(file)
                            val ck = parsed[PrefsConstants.KEY_CLOUD_KEYWORDS] as? String ?: ""
                            val uk = parsed[PrefsConstants.KEY_USER_KEYWORDS] as? String ?: ""
                            if (ck.isNotEmpty() || uk.isNotEmpty()) {
                                cloudKeywords = ck
                                userKeywords = uk
                                prefs.edit()
                                    .putString(PrefsConstants.KEY_CLOUD_KEYWORDS, cloudKeywords)
                                    .putString(PrefsConstants.KEY_USER_KEYWORDS, userKeywords)
                                    .apply()
                                break
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }

            val bundle = Bundle().apply {
                putBoolean(PrefsConstants.KEY_ENABLED, prefs.getBoolean(PrefsConstants.KEY_ENABLED, true))
                putBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, prefs.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true))
                putBoolean(PrefsConstants.KEY_CHECK_USERNAME, prefs.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true))
                putBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, prefs.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false))
                putBoolean(PrefsConstants.KEY_BLOCK_EMOJI, prefs.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false))
                putBoolean(PrefsConstants.KEY_BLOCK_GROK, prefs.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false))
                putString(PrefsConstants.KEY_USER_KEYWORDS, userKeywords)
                putString(PrefsConstants.KEY_CLOUD_KEYWORDS, cloudKeywords)
                putString(PrefsConstants.KEY_WHITELIST, prefs.getString(PrefsConstants.KEY_WHITELIST, "") ?: "")
                val disabled = prefs.getStringSet(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, emptySet()) ?: emptySet()
                putStringArrayList(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, ArrayList(disabled))
            }
            setResultExtras(bundle)
        }
    }

    companion object {
        const val ACTION_GET_CONFIG = "com.xtwitter.blocker.ACTION_GET_CONFIG"
    }
}
