package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.GraphQLInterceptor
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TimelineWithoutDataTest {

    @Test
    fun testTimelineRootLevelInstructions() {
        val jsonWithoutData = """
        {
          "timeline": {
            "instructions": [
              {
                "type": "TimelineAddEntries",
                "entries": [
                  {
                    "entryId": "promoted-tweet-123",
                    "content": {
                      "itemContent": {
                        "itemType": "TimelineTweet"
                      }
                    }
                  },
                  {
                    "entryId": "tweet-456",
                    "content": {
                      "itemContent": {
                        "itemType": "TimelineTweet",
                        "tweet_results": {
                          "result": {
                            "legacy": { "full_text": "Keep this tweet" }
                          }
                        }
                      }
                    }
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isBlockPromoted = true

        val filtered = GraphQLInterceptor.filterJsonResponse(jsonWithoutData, engine)
        assertFalse("Should filter promoted tweet even without root 'data' key", filtered.contains("promoted-tweet-123"))
        assertTrue("Should preserve normal tweet", filtered.contains("tweet-456"))
    }
}
