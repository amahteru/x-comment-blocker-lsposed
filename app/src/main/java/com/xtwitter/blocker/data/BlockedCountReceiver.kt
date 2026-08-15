package com.xtwitter.blocker.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BlockedCountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_INCREMENT_BLOCKED) {
            val delta = intent.getIntExtra("count", 1)
            val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getInt(PrefsConstants.KEY_BLOCKED_COUNT, 0)
            val updated = current + delta
            prefs.edit().putInt(PrefsConstants.KEY_BLOCKED_COUNT, updated).commit()
            Log.d("XCommentBlocker-Recv", "Incremented blocked count: $current -> $updated (+${delta})")
        }
    }

    companion object {
        const val ACTION_INCREMENT_BLOCKED = "com.xtwitter.blocker.ACTION_INCREMENT_BLOCKED"
    }
}
