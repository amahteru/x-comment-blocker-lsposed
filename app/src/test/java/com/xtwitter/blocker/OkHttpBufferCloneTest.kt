package com.xtwitter.blocker

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpBufferCloneTest {

    @Test
    fun testBufferCloneLeavesOriginalBodyIntact() {
        val originalText = """{"data":{"viewer":{"user":{"id":"123"}}}}"""
        val response = Response.Builder()
            .request(Request.Builder().url("https://api.twitter.com/graphql/Viewer").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(originalText.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()

        val body = response.body!!
        val source = body.source()
        source.request(Long.MAX_VALUE) // Buffer entire body into source's buffer
        val buffer = source.buffer

        // Clone the buffer so we can read it without consuming the original source
        val clonedBuffer = buffer.clone()
        val readString = clonedBuffer.readUtf8()
        assertEquals(originalText, readString)

        // Now verify the original response body can still be read by Twitter!
        val consumerString = response.body?.string()
        assertEquals(originalText, consumerString)
    }
}
