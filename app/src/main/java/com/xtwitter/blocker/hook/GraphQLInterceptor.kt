package com.xtwitter.blocker.hook

import com.xtwitter.blocker.engine.FilterResult
import com.xtwitter.blocker.engine.SpamFilterEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

object GraphQLInterceptor {

    val blockedCount = AtomicInteger(0)

    fun resetCount() {
        blockedCount.set(0)
    }

    /**
     * Filters GraphQL JSON string and returns modified JSON string.
     * Supports both modern snake_case / details format and legacy camelCase / legacy format.
     */
    fun filterJsonResponse(json: String, engine: SpamFilterEngine): String {
        if (!engine.isEnabled) {
            return json
        }

        return try {
            val root = JSONObject(json)
            val data = root.optJSONObject("data") ?: root

            var modified = false

            // Try all possible timeline root containers
            val timelineCandidates = listOf(
                data.optJSONObject("threaded_conversation_with_injections_v2"),
                data.optJSONObject("timelineResponse"),
                data.optJSONObject("timeline_response"),
                data.optJSONObject("timeline"),
                data.optJSONObject("home")?.optJSONObject("home_timeline_urt"),
                data.optJSONObject("search_by_raw_query")?.optJSONObject("search_timeline"),
                data.optJSONObject("user")?.optJSONObject("result")?.optJSONObject("timeline")?.optJSONObject("timeline"),
                data.optJSONObject("user")?.optJSONObject("result")?.optJSONObject("timeline_response"),
                data
            )

            for (timeline in timelineCandidates) {
                if (timeline == null) continue
                if (processTimelineContainer(timeline, engine)) {
                    modified = true
                }
            }

            if (modified) root.toString() else json
        } catch (_: Throwable) {
            json
        }
    }

    private fun processTimelineContainer(
        container: JSONObject,
        engine: SpamFilterEngine
    ): Boolean {
        var modified = false
        val instructions = container.optJSONArray("instructions") ?: return false

        for (i in 0 until instructions.length()) {
            val instruction = instructions.optJSONObject(i) ?: continue

            // TimelineAddEntries / TimelineAddToModule
            val entries = instruction.optJSONArray("entries")
            if (entries != null) {
                if (filterEntriesArray(entries, engine)) {
                    modified = true
                }
            }

            val moduleItems = instruction.optJSONArray("moduleItems")
                ?: instruction.optJSONArray("module_items")
                ?: instruction.optJSONArray("items")
            if (moduleItems != null) {
                if (filterItemsArray(moduleItems, engine)) {
                    modified = true
                }
            }

            // TimelinePinEntry / item / entry
            val singleEntry = instruction.optJSONObject("entry")
            if (singleEntry != null) {
                val content = extractItemContent(singleEntry)
                if (content != null && shouldFilterItemContent(content, engine)) {
                    instruction.remove("entry")
                    blockedCount.incrementAndGet()
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
            val entryId = entry.optString("entryId", "").ifEmpty {
                entry.optString("entry_id", "")
            }

            // Never remove timeline pagination cursors
            if (entryId.startsWith("cursor-") || entryId.startsWith("cursor_")) {
                continue
            }

            if (entryId.startsWith("promoted-") || entryId.startsWith("promotedTweet-") || entryId.startsWith("promoted_")) {
                if (engine.isBlockPromoted) {
                    toRemoveIndices.add(i)
                    blockedCount.incrementAndGet()
                    modified = true
                    continue
                }
            }

            val content = entry.optJSONObject("content") ?: entry

            // Check client_event_info component for promoted
            val clientEventInfo = content.optJSONObject("client_event_info")
                ?: entry.optJSONObject("client_event_info")
            val component = clientEventInfo?.optString("component", "") ?: ""
            if (component.contains("promoted", ignoreCase = true) && engine.isBlockPromoted) {
                toRemoveIndices.add(i)
                blockedCount.incrementAndGet()
                modified = true
                continue
            }

            // Check items array (modules / conversations / related tweets)
            val items = content.optJSONArray("items")
                ?: entry.optJSONArray("items")
            if (items != null) {
                if (filterItemsArray(items, engine)) {
                    modified = true
                }
                // If all items in this module were removed, remove the whole module entry
                if (items.length() == 0) {
                    toRemoveIndices.add(i)
                }
                continue
            }

            // Check direct itemContent
            val itemContent = extractItemContent(content) ?: extractItemContent(entry)
            if (itemContent != null) {
                if (shouldFilterItemContent(itemContent, engine)) {
                    toRemoveIndices.add(i)
                    blockedCount.incrementAndGet()
                    modified = true
                }
            }
        }

        for (idx in toRemoveIndices.distinct().sortedDescending()) {
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
            val entryId = itemObj.optString("entry_id", "").ifEmpty {
                itemObj.optString("entryId", "")
            }

            if (entryId.startsWith("promoted-") || entryId.startsWith("promoted_")) {
                if (engine.isBlockPromoted) {
                    toRemoveIndices.add(i)
                    blockedCount.incrementAndGet()
                    modified = true
                    continue
                }
            }

            val itemContent = extractItemContent(itemObj)
            if (itemContent != null && shouldFilterItemContent(itemContent, engine)) {
                toRemoveIndices.add(i)
                blockedCount.incrementAndGet()
                modified = true
            }
        }

        for (idx in toRemoveIndices.distinct().sortedDescending()) {
            items.remove(idx)
        }

        return modified
    }

    /**
     * Flexibly extracts the TimelineTweet or itemContent JSONObject from various nesting patterns.
     */
    private fun extractItemContent(obj: JSONObject): JSONObject? {
        val item = obj.optJSONObject("item")
        if (item != null) {
            val itemContent = item.optJSONObject("itemContent")
                ?: item.optJSONObject("item_content")
                ?: item.optJSONObject("content")
            if (itemContent != null) return itemContent
            if (item.has("tweet_results") || item.has("tweetResults")) return item
        }

        val content = obj.optJSONObject("content")
        if (content != null) {
            val innerItem = content.optJSONObject("item")
            if (innerItem != null) {
                val innerContent = innerItem.optJSONObject("content")
                    ?: innerItem.optJSONObject("itemContent")
                    ?: innerItem.optJSONObject("item_content")
                if (innerContent != null) return innerContent
            }
            val innerContent = content.optJSONObject("itemContent")
                ?: content.optJSONObject("item_content")
            if (innerContent != null) return innerContent
            if (content.has("tweet_results") || content.has("tweetResults")) return content
        }

        val directContent = obj.optJSONObject("itemContent") ?: obj.optJSONObject("item_content")
        if (directContent != null) return directContent

        if (obj.has("tweet_results") || obj.has("tweetResults")) return obj

        return null
    }

    private fun shouldFilterItemContent(
        itemContent: JSONObject,
        engine: SpamFilterEngine
    ): Boolean {
        val promotedMetadata = itemContent.optJSONObject("promotedMetadata")
            ?: itemContent.optJSONObject("promoted_metadata")
        val isPromoted = promotedMetadata != null

        if (isPromoted && engine.isBlockPromoted) {
            return true
        }

        val tweetResults = itemContent.optJSONObject("tweet_results")
            ?: itemContent.optJSONObject("tweetResults")
        var result = tweetResults?.optJSONObject("result")

        // In some responses, result is wrapped in TweetWithVisibilityResults
        if (result != null && result.optString("__typename") == "TweetWithVisibilityResults") {
            result = result.optJSONObject("tweet") ?: result
        }

        if (result == null) {
            return isPromoted && engine.isBlockPromoted
        }

        // Extract full_text: check details (modern) and legacy (classic)
        val details = result.optJSONObject("details")
        val legacy = result.optJSONObject("legacy")

        var fullText = details?.optString("full_text", "") ?: ""
        if (fullText.isEmpty()) {
            fullText = legacy?.optString("full_text", "") ?: ""
        }
        if (fullText.isEmpty()) {
            fullText = details?.optString("text", "") ?: legacy?.optString("text", "") ?: ""
        }

        // Long tweet / Note tweet support (X Premium / >280 characters)
        val noteTweet = result.optJSONObject("note_tweet")
            ?.optJSONObject("note_tweet_results")
            ?.optJSONObject("result")
        val noteText = noteTweet?.optString("text", "") ?: ""
        if (noteText.isNotEmpty()) {
            fullText = if (fullText.isEmpty()) noteText else "$fullText $noteText"
        }

        // Quoted tweet support
        val quotedResult = result.optJSONObject("quotedPostResults")?.optJSONObject("result")
            ?: result.optJSONObject("quoted_status_result")?.optJSONObject("result")
            ?: result.optJSONObject("quoted_tweet_results")?.optJSONObject("result")

        var quotedTweet = quotedResult
        if (quotedTweet != null && quotedTweet.optString("__typename") == "TweetWithVisibilityResults") {
            quotedTweet = quotedTweet.optJSONObject("tweet") ?: quotedTweet
        }
        val quotedDetails = quotedTweet?.optJSONObject("details")
        val quotedLegacy = quotedTweet?.optJSONObject("legacy")
        val quotedText = quotedDetails?.optString("full_text", "")
            ?.ifEmpty { quotedLegacy?.optString("full_text", "") } ?: ""
        if (quotedText.isNotEmpty()) {
            fullText = if (fullText.isEmpty()) quotedText else "$fullText $quotedText"
        }

        // Extract user display name and screen_name
        val core = result.optJSONObject("core")
        var userResult = core?.optJSONObject("user_results")?.optJSONObject("result")
            ?: core?.optJSONObject("userResults")?.optJSONObject("result")
        if (userResult != null && userResult.optString("__typename") == "UserWithVisibilityResults") {
            userResult = userResult.optJSONObject("user") ?: userResult
        }

        val userCore = userResult?.optJSONObject("core")
        val userLegacy = userResult?.optJSONObject("legacy")

        var screenName = userCore?.optString("screen_name", "") ?: ""
        if (screenName.isEmpty()) {
            screenName = userLegacy?.optString("screen_name", "") ?: ""
        }

        var name = userCore?.optString("name", "") ?: ""
        if (name.isEmpty()) {
            name = userLegacy?.optString("name", "") ?: ""
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
