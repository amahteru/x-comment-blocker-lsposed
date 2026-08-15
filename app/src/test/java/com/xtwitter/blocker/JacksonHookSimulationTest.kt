package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.GraphQLInterceptor
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class JacksonHookSimulationTest {

    private fun hookCreateParserByteArray(
        bytes: ByteArray,
        offset: Int,
        len: Int,
        engine: SpamFilterEngine
    ): Triple<ByteArray, Int, Int> {
        try {
            // Quick check without full decode
            val jsonString = String(bytes, offset, len, Charsets.UTF_8)
            if (!jsonString.contains("\"data\"") || !jsonString.contains("\"instructions\"")) {
                return Triple(bytes, offset, len)
            }

            val filtered = GraphQLInterceptor.filterJsonResponse(jsonString, engine)
            if (filtered == jsonString) {
                return Triple(bytes, offset, len)
            }

            val newBytes = filtered.toByteArray(Charsets.UTF_8)
            return Triple(newBytes, 0, newBytes.size)
        } catch (_: Throwable) {
            return Triple(bytes, offset, len)
        }
    }

    private fun hookCreateParserString(content: String, engine: SpamFilterEngine): String {
        try {
            if (!content.contains("\"data\"") || !content.contains("\"instructions\"")) {
                return content
            }
            return GraphQLInterceptor.filterJsonResponse(content, engine)
        } catch (_: Throwable) {
            return content
        }
    }

    private fun hookCreateParserInputStream(inputStream: InputStream, engine: SpamFilterEngine): InputStream {
        try {
            val bytes = inputStream.readBytes()
            val jsonString = String(bytes, Charsets.UTF_8)
            if (!jsonString.contains("\"data\"") || !jsonString.contains("\"instructions\"")) {
                return ByteArrayInputStream(bytes)
            }
            val filtered = GraphQLInterceptor.filterJsonResponse(jsonString, engine)
            if (filtered == jsonString) {
                return ByteArrayInputStream(bytes)
            }
            return ByteArrayInputStream(filtered.toByteArray(Charsets.UTF_8))
        } catch (_: Throwable) {
            return inputStream
        }
    }

    @Test
    fun testJacksonByteArrayFilter() {
        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isBlockPromoted = true

        val rawJson = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    {
                      "entryId": "tweet-1",
                      "content": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "tweet_results": {
                            "result": { "legacy": { "full_text": "Clean Tweet" } }
                          }
                        }
                      }
                    },
                    {
                      "entryId": "promoted-ad",
                      "content": { "itemContent": { "itemType": "TimelineTweet" } }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val rawBytes = rawJson.toByteArray(Charsets.UTF_8)
        val (newBytes, offset, len) = hookCreateParserByteArray(rawBytes, 0, rawBytes.size, engine)

        val filteredString = String(newBytes, offset, len, Charsets.UTF_8)
        assertFalse(filteredString.contains("promoted-ad"))
        assertTrue(filteredString.contains("Clean Tweet"))
    }

    @Test
    fun testJacksonStringFilter() {
        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isBlockPromoted = true

        val rawJson = """
        {
          "data": {
            "home": {
              "home_timeline_urt": {
                "instructions": [
                  {
                    "type": "TimelineAddEntries",
                    "entries": [
                      {
                        "entryId": "promoted-home-ad",
                        "content": { "itemContent": { "itemType": "TimelineTweet" } }
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """.trimIndent()

        val filtered = hookCreateParserString(rawJson, engine)
        assertFalse(filtered.contains("promoted-home-ad"))
    }
}
