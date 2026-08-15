package com.xtwitter.blocker.hook

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.engine.SpamFilterEngine
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.zip.GZIPInputStream

object TwitterHook {

    private const val TAG = "XCommentBlocker-Hook"
    private var appContext: Context? = null
    private var lastConfigLoadTime = 0L
    private const val CONFIG_RELOAD_INTERVAL_MS = 10_000L

    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        // 1. Hook Application onCreate to get Application Context & reload preferences
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        appContext = app
                        XposedBridge.log("[$TAG] Target Application initialized: ${app.packageName}")
                        ConfigManager.fromContext(app).loadToEngine(SpamFilterEngine.instance, app)
                        lastConfigLoadTime = System.currentTimeMillis()
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook Application.onCreate: ${t.message}")
        }

        // 2. Hook OkHttpClient$Builder.build() using Dynamic Proxy across ClassLoaders
        hookOkHttp(classLoader)
    }

    private fun hookOkHttp(classLoader: ClassLoader) {
        try {
            val builderClass = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient\$Builder", classLoader)
                ?: XposedHelpers.findClassIfExists("com.squareup.okhttp.OkHttpClient\$Builder", classLoader)

            val interceptorClass = XposedHelpers.findClassIfExists("okhttp3.Interceptor", classLoader)
                ?: XposedHelpers.findClassIfExists("com.squareup.okhttp.Interceptor", classLoader)

            if (builderClass != null && interceptorClass != null) {
                XposedBridge.hookAllMethods(builderClass, "build", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val builder = param.thisObject
                        try {
                            val proxyInterceptor = createProxyInterceptor(interceptorClass, classLoader)
                            XposedHelpers.callMethod(builder, "addInterceptor", proxyInterceptor)
                            XposedBridge.log("[$TAG] Successfully injected dynamic GraphQL Interceptor into OkHttpClient\$Builder")
                        } catch (t: Throwable) {
                            XposedBridge.log("[$TAG] Failed to add Interceptor proxy: ${t.message}")
                        }
                    }
                })
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook OkHttpClient: ${t.message}")
        }
    }

    /**
     * Creates a java.lang.reflect.Proxy implementing target's okhttp3.Interceptor.
     * This avoids ClassLoader mismatch between the module and Twitter APK.
     */
    private fun createProxyInterceptor(interceptorClass: Class<*>, classLoader: ClassLoader): Any {
        return Proxy.newProxyInstance(
            classLoader,
            arrayOf(interceptorClass),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                    if (method.name == "intercept" && args != null && args.isNotEmpty()) {
                        val chain = args[0]
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
    }

    private fun handleIntercept(chain: Any, classLoader: ClassLoader): Any {
        val request = XposedHelpers.callMethod(chain, "request")
        val httpUrl = XposedHelpers.callMethod(request, "url")
        val url = httpUrl?.toString() ?: ""

        val response = XposedHelpers.callMethod(chain, "proceed", request)

        // Only inspect GraphQL requests or timeline API responses
        if (!url.contains("/graphql/") && !url.contains("/1.1/timeline/")) {
            return response
        }

        val body = XposedHelpers.callMethod(response, "body") ?: return response
        val contentType = XposedHelpers.callMethod(body, "contentType")
        val mediaTypeString = contentType?.toString() ?: ""

        // Must be JSON or tweet conversation endpoint
        if (!mediaTypeString.contains("json") && !url.contains("TweetDetail") && !url.contains("ThreadedConversation")) {
            return response
        }

        return try {
            checkAndReloadConfigThrottled()

            // Read raw bytes from ResponseBody
            val stream = XposedHelpers.callMethod(body, "byteStream") as? InputStream
            val rawBytes = stream?.readBytes()
                ?: (XposedHelpers.callMethod(body, "bytes") as? ByteArray)
                ?: return response

            val contentEncoding = XposedHelpers.callMethod(response, "header", "Content-Encoding") as? String
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

            val previousBlockedCount = GraphQLInterceptor.blockedCount.get()
            val filteredJson = GraphQLInterceptor.filterJsonResponse(
                jsonString,
                SpamFilterEngine.instance
            )

            val newBlockedDelta = GraphQLInterceptor.blockedCount.get() - previousBlockedCount
            if (newBlockedDelta > 0) {
                syncBlockedCountDelta(newBlockedDelta)
            }

            val newBytes = filteredJson.toByteArray(Charsets.UTF_8)
            val newBody = createResponseBody(newBytes, contentType, classLoader) ?: return response

            val responseBuilder = XposedHelpers.callMethod(response, "newBuilder")
            if (isGzip) {
                XposedHelpers.callMethod(responseBuilder, "removeHeader", "Content-Encoding")
            }
            XposedHelpers.callMethod(responseBuilder, "removeHeader", "Content-Length")
            XposedHelpers.callMethod(responseBuilder, "body", newBody)

            XposedHelpers.callMethod(responseBuilder, "build") ?: response
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Interceptor processing error: ${e.message}")
            response
        }
    }

    private fun checkAndReloadConfigThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastConfigLoadTime > CONFIG_RELOAD_INTERVAL_MS) {
            lastConfigLoadTime = now
            appContext?.let {
                ConfigManager.fromContext(it).loadToEngine(SpamFilterEngine.instance, it)
            }
        }
    }

    private fun createResponseBody(bytes: ByteArray, contentType: Any?, classLoader: ClassLoader): Any? {
        val responseBodyClass = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", classLoader)
            ?: XposedHelpers.findClassIfExists("com.squareup.okhttp.ResponseBody", classLoader)
            ?: return null

        val mediaTypeClass = XposedHelpers.findClassIfExists("okhttp3.MediaType", classLoader)
            ?: XposedHelpers.findClassIfExists("com.squareup.okhttp.MediaType", classLoader)

        // Try static create(MediaType, byte[])
        try {
            if (mediaTypeClass != null) {
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
                    it.parameterTypes[0] == ByteArray::class.java &&
                    it.parameterTypes[1].isAssignableFrom(mediaTypeClass)
                }
                if (methodRev != null) {
                    return methodRev.invoke(null, bytes, contentType)
                }
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

    private fun syncBlockedCountDelta(delta: Int) {
        val context = appContext ?: return
        try {
            val uri = Uri.parse("content://${PrefsConstants.AUTHORITY}")
            val bundle = Bundle().apply { putInt("count", delta) }
            context.contentResolver.call(uri, PrefsConstants.METHOD_INCREMENT_BLOCKED, null, bundle)
        } catch (_: Throwable) {}
    }
}
