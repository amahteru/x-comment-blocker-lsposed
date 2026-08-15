package com.xtwitter.blocker.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class ConfigProvider : ContentProvider() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(): Boolean {
        context?.let { ctx ->
            prefs = ConfigManager.getPreferences(ctx)
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val bundle = Bundle()
        when (method) {
            PrefsConstants.METHOD_GET_CONFIG -> {
                bundle.putBoolean(PrefsConstants.KEY_ENABLED, prefs.getBoolean(PrefsConstants.KEY_ENABLED, true))
                bundle.putBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, prefs.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true))
                bundle.putBoolean(PrefsConstants.KEY_CHECK_USERNAME, prefs.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true))
                bundle.putBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, prefs.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false))
                bundle.putBoolean(PrefsConstants.KEY_BLOCK_EMOJI, prefs.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false))
                bundle.putBoolean(PrefsConstants.KEY_BLOCK_GROK, prefs.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false))
                bundle.putString(PrefsConstants.KEY_USER_KEYWORDS, prefs.getString(PrefsConstants.KEY_USER_KEYWORDS, ""))
                bundle.putString(PrefsConstants.KEY_CLOUD_KEYWORDS, prefs.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: "")
                bundle.putString(PrefsConstants.KEY_WHITELIST, prefs.getString(PrefsConstants.KEY_WHITELIST, ""))
                val disabledSet = prefs.getStringSet(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, emptySet()) ?: emptySet()
                bundle.putStringArrayList(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, ArrayList(disabledSet))
            }
            PrefsConstants.METHOD_INCREMENT_BLOCKED -> {
                val current = prefs.getInt(PrefsConstants.KEY_BLOCKED_COUNT, 0)
                val countToAdd = extras?.getInt("count", 1) ?: 1
                prefs.edit().putInt(PrefsConstants.KEY_BLOCKED_COUNT, current + countToAdd).apply()
            }
        }
        return bundle
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
