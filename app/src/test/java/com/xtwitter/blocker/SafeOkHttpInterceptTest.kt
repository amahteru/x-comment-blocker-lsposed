package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.GraphQLInterceptor
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class SafeOkHttpInterceptTest {

    @Test
    fun testNonGzipTimelineFilteredSafely() {
        val originalJson = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    {
                      "entryId": "tweet-normal",
                      "content": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "legacy": { "full_text": "Normal tweet" }
                            }
                          }
                        }
                      }
                    },
                    {
                      "entryId": "promoted-123",
                      "content": {
                        "itemContent": {
                          "itemType": "TimelineTweet"
                        }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isBlockPromoted = true

        val request = Request.Builder().url("https://api.twitter.com/graphql/abc/TweetDetail").build()
        val originalResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(originalJson.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        // Intercept logic:
        val body = originalResponse.body!!
        val source = body.source()
        source.request(Long.MAX_VALUE)
        val buffer = source.buffer

        val jsonString = buffer.clone().readUtf8()
        val filtered = GraphQLInterceptor.filterJsonResponse(jsonString, engine)

        assertNotEquals(jsonString, filtered)
        assertFalse(filtered.contains("promoted-123"))
        assertTrue(filtered.contains("tweet-normal"))

        // Create new body
        val newBody = filtered.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val newResponse = originalResponse.newBuilder()
            .body(newBody)
            .build()

        val resultString = newResponse.body?.string()
        assertEquals(filtered, resultString)
    }

    @Test
    fun testUnmodifiedResponsePreservesOriginalBody() {
        val originalJson = """{"data":{"viewer":{"id":"12345"}}}"""
        val engine = SpamFilterEngine.instance

        val request = Request.Builder().url("https://api.twitter.com/graphql/abc/Viewer").build()
        val originalResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(originalJson.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        val body = originalResponse.body!!
        val source = body.source()
        source.request(Long.MAX_VALUE)
        val buffer = source.buffer

        val jsonString = buffer.clone().readUtf8()
        val filtered = GraphQLInterceptor.filterJsonResponse(jsonString, engine)

        // Filter did not modify anything!
        assertEquals(jsonString, filtered)

        // We return originalResponse directly, and its body is STILL 100% readable!
        val consumerString = originalResponse.body?.string()
        assertEquals(originalJson, consumerString)
    }

    @Test
    fun testGzipCompressedResponseDecompressAndFilter() {
        val originalJson = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    {
                      "entryId": "promoted-ad",
                      "content": {
                        "itemContent": { "itemType": "TimelineTweet" }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(originalJson.toByteArray(Charsets.UTF_8)) }
        val gzipBytes = bos.toByteArray()

        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isBlockPromoted = true

        val request = Request.Builder().url("https://api.twitter.com/graphql/abc/TweetDetail").build()
        val originalResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Encoding", "gzip")
            .body(gzipBytes.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        val body = originalResponse.body!!
        val source = body.source()
        source.request(Long.MAX_VALUE)

        val isGzip = originalResponse.header("Content-Encoding")?.equals("gzip", ignoreCase = true) == true
        val jsonString = if (isGzip) {
            val gzipBuffer = Buffer()
            GzipSource(source.buffer.clone()).use { gzipSource ->
                gzipBuffer.writeAll(gzipSource)
            }
            gzipBuffer.readUtf8()
        } else {
            source.buffer.clone().readUtf8()
        }

        val filtered = GraphQLInterceptor.filterJsonResponse(jsonString, engine)
        assertFalse(filtered.contains("promoted-ad"))

        val newResponse = originalResponse.newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(filtered.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        assertEquals(filtered, newResponse.body?.string())
    }
}
