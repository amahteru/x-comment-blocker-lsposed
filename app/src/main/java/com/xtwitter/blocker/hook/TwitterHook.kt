package com.xtwitter.blocker.hook

import android.app.Application
import android.content.Context
import android.content.Intent
import com.xtwitter.blocker.data.BlockedCountReceiver
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.engine.SpamFilterEngine
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

object TwitterHook {

    private const val TAG = "XCommentBlocker-Hook"
    private const val HEADER_PROCESSED = "X-Blocker-Processed"
    private var appContext: Context? = null
    private var lastConfigLoadTime = 0L
    private const val CONFIG_RELOAD_INTERVAL_MS = 15_000L
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        appContext = app
                        XposedBridge.log("[$TAG] Target Application initialized: ${app.packageName}")

                        val appClassLoader = app.classLoader
                        hookOkHttpSafe(appClassLoader)

                        backgroundExecutor.execute {
                            try {
                                ConfigManager.fromContext(app).loadToEngine(SpamFilterEngine.instance, app)
                                lastConfigLoadTime = System.currentTimeMillis()
                                XposedBridge.log("[$TAG] Initial config loaded: isEnabled=${SpamFilterEngine.instance.isEnabled}, hasKeywords=${SpamFilterEngine.instance.hasKeywords()}")
                            } catch (e: Throwable) {
                                XposedBridge.log("[$TAG] Error loading config: ${e.message}")
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook Application.onCreate: ${t.message}")
        }

        hookOkHttpSafe(classLoader)
    }

    private fun hookOkHttpSafe(classLoader: ClassLoader) {
        try {
            val realInterceptorChainClass = XposedHelpers.findClassIfExists("okhttp3.internal.http.RealInterceptorChain", classLoader)
                ?: XposedHelpers.findClassIfExists("okhttp3.internal.connection.RealCall\$RealInterceptorChain", classLoader)

            if (realInterceptorChainClass != null) {
                XposedBridge.hookAllMethods(realInterceptorChainClass, "proceed", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.hasThrowable()) return
                        val response = param.result ?: return

                        try {
                            val isProcessed = XposedHelpers.callMethod(response, "header", HEADER_PROCESSED) as? String
                            if (isProcessed != null) return

                            val request = XposedHelpers.callMethod(response, "request") ?: return
                            val httpUrl = XposedHelpers.callMethod(request, "url") ?: return
                            val url = httpUrl.toString()

                            val newResponse = safeFilterOkHttpResponse(response, url, classLoader)
                            if (newResponse != null) {
                                param.result = newResponse
                            }
                        } catch (e: Throwable) {
                            XposedBridge.log("[$TAG] OkHttp proceed hook error: ${e.message}")
                        }
                    }
                })
                XposedBridge.log("[$TAG] Successfully hooked OkHttp RealInterceptorChain.proceed safely")
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Safe OkHttp hook exception: ${t.message}")
        }
    }

    private fun safeFilterOkHttpResponse(response: Any, url: String, classLoader: ClassLoader): Any? {
        val code = (XposedHelpers.callMethod(response, "code") as? Int) ?: 200
        if (code !in 200..299) return null

        val body = XposedHelpers.callMethod(response, "body") ?: return null
        val contentType = XposedHelpers.callMethod(body, "contentType")

        val inputStream = XposedHelpers.callMethod(body, "byteStream") as? InputStream ?: return null
        val rawBytes = try {
            inputStream.readBytes()
        } catch (_: Throwable) {
            return null
        }

        if (rawBytes.isEmpty()) return null

        val contentEncoding = XposedHelpers.callMethod(response, "header", "Content-Encoding") as? String
        val isGzip = contentEncoding?.equals("gzip", ignoreCase = true) == true

        val jsonString = if (isGzip) {
            try {
                GZIPInputStream(ByteArrayInputStream(rawBytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Throwable) {
                null
            }
        } else {
            try {
                String(rawBytes, Charsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }

        val responseBuilder = XposedHelpers.callMethod(response, "newBuilder") ?: return null
        XposedHelpers.callMethod(responseBuilder, "header", HEADER_PROCESSED, "1")

        if (jsonString.isNullOrBlank() || !isPotentialTimelineJson(jsonString)) {
            val unmodBody = createResponseBody(rawBytes, contentType, classLoader) ?: return null
            XposedHelpers.callMethod(responseBuilder, "body", unmodBody)
            return XposedHelpers.callMethod(responseBuilder, "build")
        }

        // Ensure engine is loaded before filtering
        val context = appContext
        if (context != null) {
            try {
                ConfigManager.fromContext(context).loadToEngine(SpamFilterEngine.instance, context)
            } catch (_: Throwable) {}
        }

        val previousBlockedCount = GraphQLInterceptor.blockedCount.get()
        val filteredJson = GraphQLInterceptor.filterJsonResponse(jsonString, SpamFilterEngine.instance)
        val delta = GraphQLInterceptor.blockedCount.get() - previousBlockedCount

        if (filteredJson != jsonString) {
            XposedBridge.log("[$TAG] OkHttp filtered JSON successfully for $url! blockedCount delta: $delta")
            if (delta > 0) syncBlockedCountDelta(delta)

            val newBytes = filteredJson.toByteArray(Charsets.UTF_8)
            val newBody = createResponseBody(newBytes, contentType, classLoader) ?: return null
            if (isGzip) {
                try { XposedHelpers.callMethod(responseBuilder, "removeHeader", "Content-Encoding") } catch (_: Throwable) {}
            }
            try { XposedHelpers.callMethod(responseBuilder, "removeHeader", "Content-Length") } catch (_: Throwable) {}
            XposedHelpers.callMethod(responseBuilder, "body", newBody)
            return XposedHelpers.callMethod(responseBuilder, "build")
        } else {
            val unmodBody = createResponseBody(rawBytes, contentType, classLoader) ?: return null
            XposedHelpers.callMethod(responseBuilder, "body", unmodBody)
            return XposedHelpers.callMethod(responseBuilder, "build")
        }
    }

    private fun isPotentialTimelineJson(json: String): Boolean {
        val trimmed = json.trimStart()
        if (!trimmed.startsWith("{")) return false
        return trimmed.contains("\"instructions\"") ||
                trimmed.contains("\"itemContent\"") ||
                trimmed.contains("\"tweet_results\"") ||
                trimmed.contains("\"threaded_conversation\"") ||
                trimmed.contains("\"home_timeline_urt\"") ||
                trimmed.contains("\"timelineResponse\"") ||
                trimmed.contains("\"timeline\"")
    }

    private fun createResponseBody(bytes: ByteArray, contentType: Any?, classLoader: ClassLoader): Any? {
        val responseBodyClass = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", classLoader)
            ?: XposedHelpers.findClassIfExists("com.squareup.okhttp.ResponseBody", classLoader)
            ?: return null

        val mediaTypeClass = XposedHelpers.findClassIfExists("okhttp3.MediaType", classLoader)
            ?: XposedHelpers.findClassIfExists("com.squareup.okhttp.MediaType", classLoader)

        // Method 1: Companion.create(byte[], MediaType?)
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

        // Method 2: Static ResponseBody.create(MediaType?, byte[])
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

    private fun syncBlockedCountDelta(delta: Int) {
        val context = appContext ?: return
        backgroundExecutor.execute {
            try {
                val intent = Intent(BlockedCountReceiver.ACTION_INCREMENT_BLOCKED).apply {
                    setClassName("com.xtwitter.blocker", "com.xtwitter.blocker.data.BlockedCountReceiver")
                    putExtra("count", delta)
                }
                context.sendBroadcast(intent)
                XposedBridge.log("[$TAG] Sent explicit blocked count broadcast delta +$delta to com.xtwitter.blocker")
            } catch (e: Throwable) {
                XposedBridge.log("[$TAG] Failed to send broadcast: ${e.message}")
            }
        }
    }
}
