package com.xtwitter.blocker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object CloudSyncManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun syncKeywords(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var content: String? = null

            try {
                val request = Request.Builder()
                    .url(PrefsConstants.CLOUD_KEYWORDS_URL)
                    .header("User-Agent", "XCommentBlocker-Android")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        content = response.body?.string()
                    }
                }
            } catch (_: Exception) {}

            if (content.isNullOrEmpty()) {
                val request = Request.Builder()
                    .url("${PrefsConstants.CLOUD_KEYWORDS_CDN_URL}?t=${System.currentTimeMillis()}")
                    .header("User-Agent", "XCommentBlocker-Android")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        content = response.body?.string()
                    }
                }
            }

            if (!content.isNullOrEmpty()) {
                val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                val lines = content!!.lines().filter { it.isNotBlank() }
                prefs.edit()
                    .putString(PrefsConstants.KEY_CLOUD_KEYWORDS, content)
                    .putLong(PrefsConstants.KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                    .apply()
                Result.success(lines.size)
            } else {
                Result.failure(Exception("Failed to download keywords from both GitHub and CDN"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
