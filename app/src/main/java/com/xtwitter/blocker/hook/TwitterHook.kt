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
    private val configManager = ConfigManager.forXposed()

    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        try {
            configManager.loadToEngine(SpamFilterEngine.instance)
            lastConfigLoadTime = System.currentTimeMillis()
            XposedBridge.log("[$TAG] Synchronous initHook config loaded: hasKeywords=${SpamFilterEngine.instance.hasKeywords()}")
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        appContext = app
                        XposedBridge.log("[$TAG] Target Application initialized: ${app.packageName}")

                        // Synchronously load config via ContentProvider right in onCreate
                        try {
                            ConfigManager.loadFromContentProvider(app, SpamFilterEngine.instance)
                            lastConfigLoadTime = System.currentTimeMillis()
                            XposedBridge.log("[$TAG] App onCreate ContentProvider config loaded: isEnabled=${SpamFilterEngine.instance.isEnabled}, hasKeywords=${SpamFilterEngine.instance.hasKeywords()}")
                        } catch (t: Throwable) {
                            XposedBridge.log("[$TAG] Error loading config from ContentProvider in onCreate: ${t.message}")
                        }

                        val appClassLoader = app.classLoader
                        hookOkHttpSafe(appClassLoader)

                        backgroundExecutor.execute {
                            if (!SpamFilterEngine.instance.hasKeywords()) {
                                try {
                                    configManager.loadToEngine(SpamFilterEngine.instance)
                                    lastConfigLoadTime = System.currentTimeMillis()
                                    XposedBridge.log("[$TAG] Background config loaded: isEnabled=${SpamFilterEngine.instance.isEnabled}, hasKeywords=${SpamFilterEngine.instance.hasKeywords()}")
                                } catch (e: Throwable) {
                                    XposedBridge.log("[$TAG] Error loading background config: ${e.message}")
                                }
                            }

                            // Always also request via Ordered Broadcast for reliable cross-process sync
                            requestConfigViaBroadcast(app)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook Application.onCreate: ${t.message}")
        }

        hookOkHttpSafe(classLoader)
    }

    private fun requestConfigViaBroadcast(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            val intent = Intent("com.xtwitter.blocker.ACTION_GET_CONFIG").apply {
                setClassName("com.xtwitter.blocker", "com.xtwitter.blocker.data.ConfigQueryReceiver")
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendOrderedBroadcast(
                intent,
                null,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: Context, it: Intent) {
                        val bundle = getResultExtras(false)
                        if (bundle != null && (bundle.containsKey(com.xtwitter.blocker.data.PrefsConstants.KEY_ENABLED) || bundle.containsKey(com.xtwitter.blocker.data.PrefsConstants.KEY_CLOUD_KEYWORDS))) {
                            ConfigManager.applyBundleToEngine(bundle, SpamFilterEngine.instance)
                            lastConfigLoadTime = System.currentTimeMillis()
                            XposedBridge.log("[$TAG] Config loaded via Ordered Broadcast: isEnabled=${SpamFilterEngine.instance.isEnabled}, hasKeywords=${SpamFilterEngine.instance.hasKeywords()}")
                            onComplete?.invoke(true)
                        } else {
                            onComplete?.invoke(false)
                        }
                    }
                },
                null,
                android.app.Activity.RESULT_OK,
                null,
                null
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to sendOrderedBroadcast for config: ${t.message}")
            onComplete?.invoke(false)
        }
    }

    private fun requestConfigViaBroadcastSync(context: Context, timeoutMs: Long = 1000) {
        val latch = java.util.concurrent.CountDownLatch(1)
        requestConfigViaBroadcast(context) {
            latch.countDown()
        }
        try {
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {}
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

                            android.util.Log.i(TAG, "OkHttp response url: $url")

                            val newResponse = safeFilterOkHttpResponse(response, url, classLoader)
                            if (newResponse != null) {
                                param.result = newResponse
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e(TAG, "OkHttp proceed hook error: ${e.message}", e)
                            XposedBridge.log("[$TAG] OkHttp proceed hook error: ${e.message}")
                        }
                    }
                })
                XposedBridge.log("[$TAG] Successfully hooked OkHttp RealInterceptorChain.proceed safely")
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Safe OkHttp hook exception: ${t.message}", t)
            XposedBridge.log("[$TAG] Safe OkHttp hook exception: ${t.message}")
        }
    }

    private fun safeFilterOkHttpResponse(response: Any, url: String, classLoader: ClassLoader): Any? {
        if (isIgnoredEndpoint(url)) {
            return null
        }

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

        // Ensure engine is loaded with keywords
        val now = System.currentTimeMillis()
        if (!SpamFilterEngine.instance.hasKeywords()) {
            try {
                if (appContext != null) {
                    if (!ConfigManager.loadFromContentProvider(appContext!!, SpamFilterEngine.instance)) {
                        requestConfigViaBroadcastSync(appContext!!, 1000)
                    }
                }
                if (!SpamFilterEngine.instance.hasKeywords()) {
                    configManager.loadToEngine(SpamFilterEngine.instance)
                }
                lastConfigLoadTime = now
            } catch (_: Throwable) {}
        } else if (now - lastConfigLoadTime > CONFIG_RELOAD_INTERVAL_MS) {
            lastConfigLoadTime = now
            backgroundExecutor.execute {
                try {
                    if (appContext != null) {
                        if (!ConfigManager.loadFromContentProvider(appContext!!, SpamFilterEngine.instance)) {
                            requestConfigViaBroadcast(appContext!!)
                        }
                    } else {
                        configManager.loadToEngine(SpamFilterEngine.instance)
                    }
                } catch (_: Throwable) {}
            }
        }


        val previousBlockedCount = GraphQLInterceptor.blockedCount.get()
        val engine = SpamFilterEngine.instance
        android.util.Log.i(TAG, "safeFilter checking: url=$url, hasKeywords=${engine.hasKeywords()}, isEnabled=${engine.isEnabled}")
        val filteredJson = GraphQLInterceptor.filterJsonResponse(jsonString, engine, url)
        val delta = GraphQLInterceptor.blockedCount.get() - previousBlockedCount
        android.util.Log.i(TAG, "safeFilter result: delta=$delta, modified=${filteredJson != jsonString}")

        if (filteredJson != jsonString) {
            android.util.Log.i(TAG, "OkHttp filtered JSON successfully for $url! blockedCount delta: $delta")
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

    private fun isIgnoredEndpoint(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.contains("typeahead") ||
                path.contains("search_box") ||
                path.contains("suggestions") ||
                path.contains("/guide") ||
                path.contains("settings") ||
                path.contains("feature_switch") ||
                path.contains("notifications") ||
                path.contains("/dm/") ||
                path.contains("direct_messages") ||
                path.contains("badge") ||
                path.contains("auth") ||
                path.contains("login") ||
                path.contains("live_pipeline") ||
                path.contains("media/upload") ||
                path.contains("/jot/")
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
