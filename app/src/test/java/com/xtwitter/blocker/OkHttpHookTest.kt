package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.GraphQLInterceptor
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class OkHttpHookTest {

    private fun createProxyInterceptor(interceptorClass: Class<*>, classLoader: ClassLoader): Interceptor {
        val proxy = Proxy.newProxyInstance(
            classLoader,
            arrayOf(interceptorClass),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                    if (method.name == "intercept" && args != null && args.isNotEmpty()) {
                        val chain = args[0] as Interceptor.Chain
                        return handleIntercept(chain, classLoader)
                    }
                    if (method.name == "toString") {
                        return "XCommentBlockerProxyInterceptor"
                    }
                    if (method.name == "hashCode") {
                        return this.hashCode()
                    }
                    if (method.name == "equals" && args != null && args.isNotEmpty()) {
                        return proxy === args[0]
                    }
                    return null
                }
            }
        )
        return proxy as Interceptor
    }

    private fun handleIntercept(chain: Interceptor.Chain, classLoader: ClassLoader): Response {
        val request = chain.request()
        val url = request.url.toString()

        val response = chain.proceed(request)

        if (!url.contains("/graphql/") && !url.contains("/1.1/timeline/")) {
            return response
        }

        val body = response.body ?: return response
        val contentType = body.contentType()
        val mediaTypeString = contentType?.toString() ?: ""

        if (!mediaTypeString.contains("json") && !url.contains("TweetDetail") && !url.contains("ThreadedConversation")) {
            return response
        }

        return try {
            val stream = body.byteStream()
            val rawBytes = stream.readBytes()

            val contentEncoding = response.header("Content-Encoding")
            val isGzip = contentEncoding?.equals("gzip", ignoreCase = true) == true

            val jsonString = if (isGzip) {
                try {
                    GZIPInputStream(ByteArrayInputStream(rawBytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (_: Throwable) {
                    String(rawBytes, Charsets.UTF_8)
                }
            } else {
                String(rawBytes, Charsets.UTF_8)
            }

            val filteredJson = GraphQLInterceptor.filterJsonResponse(
                jsonString,
                SpamFilterEngine.instance
            )

            val newBytes = filteredJson.toByteArray(Charsets.UTF_8)
            val newBody = createResponseBody(newBytes, contentType, classLoader) ?: return response

            val responseBuilder = response.newBuilder()
            if (isGzip) {
                responseBuilder.removeHeader("Content-Encoding")
            }
            responseBuilder.removeHeader("Content-Length")
            responseBuilder.body(newBody as ResponseBody)

            responseBuilder.build()
        } catch (e: Throwable) {
            response
        }
    }

    private fun createResponseBody(bytes: ByteArray, contentType: Any?, classLoader: ClassLoader): Any? {
        val responseBodyClass = classLoader.loadClass("okhttp3.ResponseBody")
        val mediaTypeClass = classLoader.loadClass("okhttp3.MediaType")

        // Try static create(MediaType, byte[])
        try {
            val method = responseBodyClass.methods.firstOrNull {
                it.name == "create" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0].isAssignableFrom(mediaTypeClass) &&
                it.parameterTypes[1] == ByteArray::class.java
            }
            if (method != null) {
                return method.invoke(null, contentType, bytes)
            }

            val methodRev = responseBodyClass.methods.firstOrNull {
                it.name == "create" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0].isAssignableFrom(ByteArray::class.java) &&
                it.parameterTypes[1].isAssignableFrom(mediaTypeClass)
            }
            if (methodRev != null) {
                return methodRev.invoke(null, bytes, contentType)
            }
        } catch (_: Throwable) {}

        // Try Companion object for OkHttp 4
        try {
            val companionField = responseBodyClass.getField("Companion")
            val companionObj = companionField.get(null)
            if (companionObj != null) {
                val method = companionObj.javaClass.methods.firstOrNull {
                    it.name == "create" && it.parameterTypes.size == 2 &&
                    it.parameterTypes.contains(ByteArray::class.java)
                }
                if (method != null) {
                    return if (method.parameterTypes[0] == ByteArray::class.java) {
                        method.invoke(companionObj, bytes, contentType)
                    } else {
                        method.invoke(companionObj, contentType, bytes)
                    }
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    @Test
    fun testOkHttpInterceptorWithGzip() {
        val interceptor = createProxyInterceptor(Interceptor::class.java, javaClass.classLoader)

        // Mock chain
        val rawJson = """{"data":{"home":{"timeline":{"instructions":[]}}}}"""
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(rawJson.toByteArray(Charsets.UTF_8)) }
        val gzipBytes = bos.toByteArray()

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("https://api.twitter.com/graphql/TweetDetail").build()
            override fun proceed(request: Request): Response {
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Encoding", "gzip")
                    .body(gzipBytes.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            override fun call(): Call = throw NotImplementedError()
            override fun connection(): Connection? = null
            override fun connectTimeoutMillis(): Int = 0
            override fun readTimeoutMillis(): Int = 0
            override fun writeTimeoutMillis(): Int = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        }

        val response = interceptor.intercept(mockChain)
        assertNull(response.header("Content-Encoding"))
        val responseText = response.body?.string()
        assertEquals(rawJson, responseText)
    }
}
