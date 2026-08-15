package com.xtwitter.blocker

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RobustResponseBodyTest {

    private fun createResponseBody(bytes: ByteArray, contentType: Any?, classLoader: ClassLoader): Any? {
        val responseBodyClass = try {
            classLoader.loadClass("okhttp3.ResponseBody")
        } catch (_: Throwable) {
            try { classLoader.loadClass("com.squareup.okhttp.ResponseBody") } catch (_: Throwable) { null }
        } ?: return null

        val mediaTypeClass = try {
            classLoader.loadClass("okhttp3.MediaType")
        } catch (_: Throwable) {
            try { classLoader.loadClass("com.squareup.okhttp.MediaType") } catch (_: Throwable) { null }
        }

        // Method 1: Companion.create(byte[], MediaType?) - OkHttp 4 / 5 Kotlin standard
        try {
            val companionField = responseBodyClass.getField("Companion")
            val companionObj = companionField.get(null)
            if (companionObj != null) {
                for (m in companionObj.javaClass.methods) {
                    if (m.name == "create" && m.parameterTypes.size == 2) {
                        if (m.parameterTypes[0] == ByteArray::class.java) {
                            return m.invoke(companionObj, bytes, contentType)
                        } else if (m.parameterTypes[1] == ByteArray::class.java) {
                            return m.invoke(companionObj, contentType, bytes)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // Method 2: Static ResponseBody.create(MediaType?, byte[]) - OkHttp 3 / OkHttp 4 @JvmStatic
        try {
            for (m in responseBodyClass.methods) {
                if (m.name == "create" && m.parameterTypes.size == 2) {
                    if (mediaTypeClass != null && m.parameterTypes[0].isAssignableFrom(mediaTypeClass) && m.parameterTypes[1] == ByteArray::class.java) {
                        return m.invoke(null, contentType, bytes)
                    } else if (mediaTypeClass != null && m.parameterTypes[0] == ByteArray::class.java && m.parameterTypes[1].isAssignableFrom(mediaTypeClass)) {
                        return m.invoke(null, bytes, contentType)
                    }
                }
            }
        } catch (_: Throwable) {}

        // Method 3: Companion.create(String, MediaType?)
        try {
            val companionField = responseBodyClass.getField("Companion")
            val companionObj = companionField.get(null)
            if (companionObj != null) {
                val str = String(bytes, Charsets.UTF_8)
                for (m in companionObj.javaClass.methods) {
                    if (m.name == "create" && m.parameterTypes.size == 2 && m.parameterTypes[0] == String::class.java) {
                        return m.invoke(companionObj, str, contentType)
                    }
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    @Test
    fun testRobustResponseBodyCreation() {
        val testContent = """{"success":true,"message":"Hello"}"""
        val bytes = testContent.toByteArray(Charsets.UTF_8)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

        val body = createResponseBody(bytes, mediaType, javaClass.classLoader) as? ResponseBody
        assertNotNull("ResponseBody must be created successfully", body)
        assertEquals(testContent, body!!.string())
    }
}
