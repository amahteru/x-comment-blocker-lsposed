package com.xtwitter.blocker.hook

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.engine.SpamFilterEngine
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Modifier

object TwitterHook {

    private const val TAG = "XCommentBlocker-Hook"
    private var appContext: Context? = null

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
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook Application.onCreate: ${t.message}")
        }

        // 2. Hook OkHttpClient$Builder.build() to add GraphQL Interceptor
        hookOkHttp(classLoader)

        // 3. Fallback: Hook general JSON parsers if needed
        hookMoshiOrJsonParsers(classLoader)
    }

    private fun hookOkHttp(classLoader: ClassLoader) {
        try {
            val builderClass = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient\$Builder", classLoader)
                ?: XposedHelpers.findClassIfExists("com.squareup.okhttp.OkHttpClient\$Builder", classLoader)

            if (builderClass != null) {
                XposedBridge.hookAllMethods(builderClass, "build", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val builder = param.thisObject
                        try {
                            val interceptor = createGraphQLInterceptor()
                            // Call builder.addNetworkInterceptor(interceptor) or addInterceptor
                            XposedHelpers.callMethod(builder, "addInterceptor", interceptor)
                            XposedBridge.log("[$TAG] Successfully injected GraphQL Interceptor into OkHttpClient\$Builder")
                        } catch (t: Throwable) {
                            XposedBridge.log("[$TAG] Failed to add Interceptor: ${t.message}")
                        }
                    }
                })
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook OkHttpClient: ${t.message}")
        }
    }

    private fun createGraphQLInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            val response = chain.proceed(request)

            // Only inspect GraphQL requests or timeline API responses
            if (!url.contains("/graphql/") && !url.contains("/1.1/timeline/")) {
                return@Interceptor response
            }

            val body = response.body ?: return@Interceptor response
            val contentType = body.contentType()
            val mediaTypeString = contentType?.toString() ?: ""

            // Must be JSON
            if (!mediaTypeString.contains("json") && !url.contains("TweetDetail") && !url.contains("ThreadedConversation")) {
                return@Interceptor response
            }

            try {
                // Ensure configuration is up to date
                appContext?.let {
                    ConfigManager.fromContext(it).loadToEngine(SpamFilterEngine.instance, it)
                }

                val originalBodyString = body.string()
                val previousBlockedCount = GraphQLInterceptor.blockedCount.get()

                val filteredJson = GraphQLInterceptor.filterJsonResponse(
                    originalBodyString,
                    SpamFilterEngine.instance
                )

                val newBlockedDelta = GraphQLInterceptor.blockedCount.get() - previousBlockedCount
                if (newBlockedDelta > 0) {
                    syncBlockedCountDelta(newBlockedDelta)
                }

                val newBody = filteredJson.toResponseBody(contentType)
                response.newBuilder().body(newBody).build()
            } catch (e: Throwable) {
                XposedBridge.log("[$TAG] Interceptor error: ${e.message}")
                response
            }
        }
    }

    private fun syncBlockedCountDelta(delta: Int) {
        val context = appContext ?: return
        try {
            val uri = Uri.parse("content://${PrefsConstants.AUTHORITY}")
            val bundle = Bundle().apply { putInt("count", delta) }
            context.contentResolver.call(uri, PrefsConstants.METHOD_INCREMENT_BLOCKED, null, bundle)
        } catch (_: Throwable) {}
    }

    private fun hookMoshiOrJsonParsers(classLoader: ClassLoader) {
        // Additional hooks can be placed here if needed for direct model parsing
    }
}
