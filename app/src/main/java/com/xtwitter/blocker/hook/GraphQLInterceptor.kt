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

            val entries = instruction.optJSONArray("entries")
            if (entries != null) {
                if (filterEntriesArray(entries, engine)) {
                    modified = true
                }
            }

            val moduleItems = instruction.optJSONArray("moduleItems")
            if (moduleItems != null) {
                if (filterItemsArray(moduleItems, engine)) {
                    modified = true
                }
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

            if (entryId.startsWith("promoted-") || entryId.startsWith("promotedTweet-")) {
                if (engine.isBlockPromoted) {
                    toRemoveIndices.add(i)
                    blockedCount.incrementAndGet()
                    modified = true
                    continue
                }
            }

            val content = entry.optJSONObject("content") ?: continue

            val itemContent = content.optJSONObject("itemContent")
            if (itemContent != null) {
                if (shouldFilterItemContent(itemContent, engine)) {
                    toRemoveIndices.add(i)
                    blockedCount.incrementAndGet()
                    modified = true
                    continue
                }
            }

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
        val promotedMetadata = itemContent.optJSONObject("promotedMetadata")
        val isPromoted = promotedMetadata != null

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
        var fullText = legacy?.optString("full_text", "") ?: ""

        // Long tweet / Note tweet support (X Premium / >280 characters)
        val noteTweet = result.optJSONObject("note_tweet")
            ?.optJSONObject("note_tweet_results")
            ?.optJSONObject("result")
        val noteText = noteTweet?.optString("text", "") ?: ""
        if (noteText.isNotEmpty()) {
            fullText = if (fullText.isEmpty()) noteText else "$fullText $noteText"
        }

        // Quoted tweet support
        val quotedResult = result.optJSONObject("quoted_status_result")?.optJSONObject("result")
        var quotedTweet = quotedResult
        if (quotedTweet != null && quotedTweet.optString("__typename") == "TweetWithVisibilityResults") {
            quotedTweet = quotedTweet.optJSONObject("tweet") ?: quotedTweet
        }
        val quotedText = quotedTweet?.optJSONObject("legacy")?.optString("full_text", "") ?: ""
        if (quotedText.isNotEmpty()) {
            fullText = if (fullText.isEmpty()) quotedText else "$fullText $quotedText"
        }

        val core = result.optJSONObject("core")
        var userResult = core?.optJSONObject("user_results")?.optJSONObject("result")
        if (userResult != null && userResult.optString("__typename") == "UserWithVisibilityResults") {
            userResult = userResult.optJSONObject("user") ?: userResult
        }
        val userLegacy = userResult?.optJSONObject("legacy")
        var screenName = userLegacy?.optString("screen_name", "") ?: ""
        if (screenName.isEmpty()) {
            screenName = userResult?.optJSONObject("core")?.optString("screen_name", "") ?: ""
        }
        var name = userLegacy?.optString("name", "") ?: ""
        if (name.isEmpty()) {
            name = userResult?.optJSONObject("core")?.optString("name", "") ?: ""
        }

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
