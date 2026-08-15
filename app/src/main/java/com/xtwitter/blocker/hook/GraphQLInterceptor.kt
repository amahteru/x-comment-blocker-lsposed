package com.xtwitter.blocker.hook

import com.xtwitter.blocker.engine.FilterResult
import com.xtwitter.blocker.engine.SpamFilterEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

object GraphQLInterceptor {

    val blockedCount = AtomicInteger(0)

    /**
     * Inspects and filters a GraphQL JSON response string.
     * Returns the cleaned JSON string, or original if unchanged / invalid.
     */
    fun filterJsonResponse(
        jsonString: String,
        engine: SpamFilterEngine = SpamFilterEngine.instance
    ): String {
        if (!engine.isEnabled) return jsonString

        return try {
            val root = JSONObject(jsonString)
            if (!root.has("data")) return jsonString

            var modified = false
            val data = root.optJSONObject("data") ?: return jsonString

            // Iterate over all possible root timeline containers in data
            // e.g. threaded_conversation_with_injections_v2, home, user, bookmark, etc.
            val containerKeys = data.keys()
            while (containerKeys.hasNext()) {
                val key = containerKeys.next()
                val container = data.optJSONObject(key) ?: continue

                // Check timeline instructions
                val instructions = container.optJSONArray("instructions")
                    ?: container.optJSONObject("timeline")?.optJSONArray("instructions")

                if (instructions != null) {
                    if (filterInstructions(instructions, engine)) {
                        modified = true
                    }
                }
            }

            if (modified) {
                root.toString()
            } else {
                jsonString
            }
        } catch (e: Exception) {
            // In case of unexpected JSON structure or parse failure, return raw string to prevent app crash
            jsonString
        }
    }

    private fun filterInstructions(
        instructions: JSONArray,
        engine: SpamFilterEngine
    ): Boolean {
        var modified = false
        for (i in 0 until instructions.length()) {
            val instruction = instructions.optJSONObject(i) ?: continue

            // TimelineAddEntries
            val entries = instruction.optJSONArray("entries")
            if (entries != null) {
                if (filterEntriesArray(entries, engine)) {
                    modified = true
                }
            }

            // TimelineAddToModule (some replies/threads are in modules)
            val moduleItems = instruction.optJSONObject("moduleItems")
            if (moduleItems != null) {
                // If needed
            }
        }
        return modified
    }

    private fun filterEntriesArray(
        entries: JSONArray,
        engine: SpamFilterEngine
    ): Boolean {
        var modified = false
        val toRemoveIndices = mutableListOf<Int>()

        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val entryId = entry.optString("entryId", "")

            // Check if entire entry is promoted
            if (entryId.startsWith("promoted-") || entryId.startsWith("promotedTweet-")) {
                if (engine.isBlockPromoted) {
                    toRemoveIndices.add(i)
                    blockedCount.incrementAndGet()
                    modified = true
                    continue
                }
            }

            val content = entry.optJSONObject("content") ?: continue

            // Case A: content is a single Item
            val itemContent = content.optJSONObject("itemContent")
            if (itemContent != null) {
                if (shouldFilterItemContent(itemContent, engine)) {
                    toRemoveIndices.add(i)
                    blockedCount.incrementAndGet()
                    modified = true
                    continue
                }
            }

            // Case B: content has items (threaded conversation list)
            val items = content.optJSONArray("items")
            if (items != null) {
                if (filterItemsArray(items, engine)) {
                    modified = true
                }
                // If all items in this thread module were removed, remove the whole thread entry
                if (items.length() == 0) {
                    toRemoveIndices.add(i)
                }
            }
        }

        // Remove from highest index to lowest
        for (idx in toRemoveIndices.asReversed()) {
            entries.remove(idx)
        }

        return modified
    }

    private fun filterItemsArray(
        items: JSONArray,
        engine: SpamFilterEngine
    ): Boolean {
        var modified = false
        val toRemoveIndices = mutableListOf<Int>()

        for (i in 0 until items.length()) {
            val itemObj = items.optJSONObject(i) ?: continue
            val item = itemObj.optJSONObject("item") ?: itemObj
            val itemContent = item.optJSONObject("itemContent") ?: continue

            if (shouldFilterItemContent(itemContent, engine)) {
                toRemoveIndices.add(i)
                blockedCount.incrementAndGet()
                modified = true
            }
        }

        for (idx in toRemoveIndices.asReversed()) {
            items.remove(idx)
        }

        return modified
    }

    private fun shouldFilterItemContent(
        itemContent: JSONObject,
        engine: SpamFilterEngine
    ): Boolean {
        // 1. Promoted check
        val promotedMetadata = itemContent.optJSONObject("promotedMetadata")
        val isPromoted = promotedMetadata != null

        // 2. Tweet Results
        val tweetResults = itemContent.optJSONObject("tweet_results")
        var result = tweetResults?.optJSONObject("result")

        // In some responses, result is wrapped in TweetWithVisibilityResults
        if (result != null && result.optString("__typename") == "TweetWithVisibilityResults") {
            result = result.optJSONObject("tweet") ?: result
        }

        if (result == null) {
            return isPromoted && engine.isBlockPromoted
        }

        val legacy = result.optJSONObject("legacy")
        val fullText = legacy?.optString("full_text", "") ?: ""

        // User info
        val core = result.optJSONObject("core")
        var userResult = core?.optJSONObject("user_results")?.optJSONObject("result")
        if (userResult != null && userResult.optString("__typename") == "UserWithVisibilityResults") {
            userResult = userResult.optJSONObject("user") ?: userResult
        }
        val userLegacy = userResult?.optJSONObject("legacy")
        val screenName = userLegacy?.optString("screen_name", "") ?: ""
        val name = userLegacy?.optString("name", "") ?: ""

        // Grok card check
        var hasGrokCard = false
        val card = result.optJSONObject("card")
        if (card != null) {
            val cardUrl = card.optString("url", "")
            if (cardUrl.contains("grok.com/share") || cardUrl.contains("x.com/i/grok")) {
                hasGrokCard = true
            }
        }

        val filterResult = engine.shouldBlockTweet(
            fullText = fullText,
            screenName = screenName,
            name = name,
            isPromoted = isPromoted,
            hasGrokCard = hasGrokCard
        )

        return filterResult is FilterResult.Blocked
    }
}
