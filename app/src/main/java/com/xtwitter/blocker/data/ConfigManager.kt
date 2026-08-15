package com.xtwitter.blocker.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.xtwitter.blocker.engine.SpamFilterEngine
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream

class ConfigManager private constructor(private val isXposedMode: Boolean) {

    private var xPrefs: XSharedPreferences? = null

    init {
        if (isXposedMode) {
            try {
                xPrefs = XSharedPreferences(PACKAGE_NAME, PrefsConstants.PREFS_NAME)
                xPrefs?.makeWorldReadable()
            } catch (t: Throwable) {
                XposedBridge.log("[$TAG] Failed to initialize XSharedPreferences: ${t.message}")
            }
        }
    }

    /**
     * Loads configuration into the SpamFilterEngine.
     * When running inside a hooked process (Xposed mode):
     * 1. Tries XSharedPreferences.reload()
     * 2. If XSharedPreferences returns empty/fails, tries reading direct XML from /data/user_de/0/com.xtwitter.blocker/shared_prefs/
     * 3. Defense-in-depth: If the loaded keyword string is empty but the engine already has valid rules, preserves existing rules.
     */
    fun loadToEngine(engine: SpamFilterEngine = SpamFilterEngine.instance) {
        if (!isXposedMode) {
            return
        }

        var isEnabled = true
        var isBlockPromoted = true
        var isCheckUsername = true
        var isBlockSpecialChars = false
        var isBlockEmoji = false
        var isBlockGrok = false
        var userKeywords = ""
        var cloudKeywords = ""
        var disabledSet = emptySet<String>()
        var whitelist = ""
        var loadedSuccessfully = false

        // Attempt 1: Standard XSharedPreferences by package name
        try {
            if (xPrefs == null) {
                xPrefs = XSharedPreferences(PACKAGE_NAME, PrefsConstants.PREFS_NAME)
                xPrefs?.makeWorldReadable()
            }
            xPrefs?.reload()
            val hasKeys = xPrefs != null && (xPrefs!!.contains(PrefsConstants.KEY_ENABLED) || xPrefs!!.contains(PrefsConstants.KEY_CLOUD_KEYWORDS) || xPrefs!!.contains(PrefsConstants.KEY_USER_KEYWORDS))
            if (hasKeys) {
                isEnabled = xPrefs!!.getBoolean(PrefsConstants.KEY_ENABLED, true)
                isBlockPromoted = xPrefs!!.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true)
                isCheckUsername = xPrefs!!.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true)
                isBlockSpecialChars = xPrefs!!.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false)
                isBlockEmoji = xPrefs!!.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false)
                isBlockGrok = xPrefs!!.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false)
                userKeywords = xPrefs!!.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""
                cloudKeywords = xPrefs!!.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
                disabledSet = xPrefs!!.getStringSet(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, emptySet()) ?: emptySet()
                whitelist = xPrefs!!.getString(PrefsConstants.KEY_WHITELIST, "") ?: ""
                loadedSuccessfully = true
                XposedBridge.log("[$TAG] Loaded via XSharedPreferences(packageName): cloudLen=${cloudKeywords.length}, userLen=${userKeywords.length}")
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] XSharedPreferences(packageName) error: ${t.message}")
        }

        // Attempt 2: XSharedPreferences by direct File
        if (!loadedSuccessfully || (cloudKeywords.isEmpty() && userKeywords.isEmpty())) {
            val candidateFiles = listOf(
                File("/data/user_de/0/$PACKAGE_NAME/shared_prefs/${PrefsConstants.PREFS_NAME}.xml"),
                File("/data/data/$PACKAGE_NAME/shared_prefs/${PrefsConstants.PREFS_NAME}.xml")
            )
            for (file in candidateFiles) {
                try {
                    val fileXsp = XSharedPreferences(file)
                    fileXsp.makeWorldReadable()
                    fileXsp.reload()
                    if (fileXsp.contains(PrefsConstants.KEY_ENABLED) || fileXsp.contains(PrefsConstants.KEY_CLOUD_KEYWORDS) || fileXsp.contains(PrefsConstants.KEY_USER_KEYWORDS)) {
                        isEnabled = fileXsp.getBoolean(PrefsConstants.KEY_ENABLED, true)
                        isBlockPromoted = fileXsp.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true)
                        isCheckUsername = fileXsp.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true)
                        isBlockSpecialChars = fileXsp.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false)
                        isBlockEmoji = fileXsp.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false)
                        isBlockGrok = fileXsp.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false)
                        userKeywords = fileXsp.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""
                        cloudKeywords = fileXsp.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
                        disabledSet = fileXsp.getStringSet(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS, emptySet()) ?: emptySet()
                        whitelist = fileXsp.getString(PrefsConstants.KEY_WHITELIST, "") ?: ""
                        loadedSuccessfully = true
                        XposedBridge.log("[$TAG] Loaded via XSharedPreferences(File=${file.path}): cloudLen=${cloudKeywords.length}, userLen=${userKeywords.length}")
                        break
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("[$TAG] XSharedPreferences(File=${file.path}) error: ${t.message}")
                }
            }
        }

        // Attempt 3: Direct XML parsing from file
        if (!loadedSuccessfully || (cloudKeywords.isEmpty() && userKeywords.isEmpty())) {
            val candidateFiles = listOf(
                File("/data/user_de/0/$PACKAGE_NAME/shared_prefs/${PrefsConstants.PREFS_NAME}.xml"),
                File("/data/data/$PACKAGE_NAME/shared_prefs/${PrefsConstants.PREFS_NAME}.xml")
            )
            for (file in candidateFiles) {
                if (file.exists() && file.canRead()) {
                    try {
                        val parsedMap = parsePrefsXml(file)
                        if (parsedMap.isNotEmpty()) {
                            isEnabled = parsedMap[PrefsConstants.KEY_ENABLED] as? Boolean ?: isEnabled
                            isBlockPromoted = parsedMap[PrefsConstants.KEY_BLOCK_PROMOTED] as? Boolean ?: isBlockPromoted
                            isCheckUsername = parsedMap[PrefsConstants.KEY_CHECK_USERNAME] as? Boolean ?: isCheckUsername
                            isBlockSpecialChars = parsedMap[PrefsConstants.KEY_BLOCK_SPECIAL_CHARS] as? Boolean ?: isBlockSpecialChars
                            isBlockEmoji = parsedMap[PrefsConstants.KEY_BLOCK_EMOJI] as? Boolean ?: isBlockEmoji
                            isBlockGrok = parsedMap[PrefsConstants.KEY_BLOCK_GROK] as? Boolean ?: isBlockGrok
                            userKeywords = parsedMap[PrefsConstants.KEY_USER_KEYWORDS] as? String ?: userKeywords
                            cloudKeywords = parsedMap[PrefsConstants.KEY_CLOUD_KEYWORDS] as? String ?: cloudKeywords
                            @Suppress("UNCHECKED_CAST")
                            disabledSet = (parsedMap[PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS] as? Set<String>) ?: disabledSet
                            whitelist = parsedMap[PrefsConstants.KEY_WHITELIST] as? String ?: whitelist
                            loadedSuccessfully = true
                            XposedBridge.log("[$TAG] Loaded via parsePrefsXml(${file.path}): cloudLen=${cloudKeywords.length}, userLen=${userKeywords.length}")
                            break
                        }
                    } catch (t: Throwable) {
                        XposedBridge.log("[$TAG] Failed to parse xml from ${file.absolutePath}: ${t.message}")
                    }
                }
            }
        }

        engine.isEnabled = isEnabled
        engine.isBlockPromoted = isBlockPromoted
        engine.isCheckUsername = isCheckUsername
        engine.isBlockSpecialChars = isBlockSpecialChars
        engine.isBlockEmoji = isBlockEmoji
        engine.isBlockGrok = isBlockGrok

        // Defense-in-depth: if keywords are completely empty and engine already has keywords, do NOT overwrite with empty
        if (cloudKeywords.isNotEmpty() || userKeywords.isNotEmpty() || !engine.hasKeywords()) {
            engine.updateKeywords(cloudKeywords, userKeywords, disabledSet)
        }
        if (whitelist.isNotEmpty()) {
            engine.updateWhitelist(whitelist.lines())
        }

        XposedBridge.log("[$TAG] loadToEngine finished: isEnabled=${engine.isEnabled}, hasKeywords=${engine.hasKeywords()}")
    }

    companion object {
        const val TAG = "XCommentBlocker-Config"
        const val PACKAGE_NAME = "com.xtwitter.blocker"

        /**
         * Gets SharedPreferences for module UI app.
         * Ensures Device Protected Storage is used and auto-migrates old preferences.
         */
        fun getPreferences(context: Context): SharedPreferences {
            val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            val prefs = storageContext.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

            // Make the dataDir, shared_prefs dir, and prefs file accessible on disk
            try {
                val dataDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) storageContext.dataDir else storageContext.filesDir.parentFile
                dataDir?.setReadable(true, false)
                dataDir?.setExecutable(true, false)

                val prefsDir = File(dataDir, "shared_prefs")
                prefsDir.setReadable(true, false)
                prefsDir.setExecutable(true, false)

                val prefsFile = File(prefsDir, "${PrefsConstants.PREFS_NAME}.xml")
                if (prefsFile.exists()) {
                    prefsFile.setReadable(true, false)
                }
            } catch (_: Throwable) {}

            return prefs
        }

        /**
         * Creates a ConfigManager for Xposed / Hooked process.
         */
        fun forXposed(): ConfigManager = ConfigManager(isXposedMode = true)

        /**
         * Applies a Bundle received from IPC / Broadcast directly to the SpamFilterEngine.
         */
        fun applyBundleToEngine(bundle: android.os.Bundle, engine: SpamFilterEngine = SpamFilterEngine.instance) {
            val isEnabled = bundle.getBoolean(PrefsConstants.KEY_ENABLED, true)
            val isBlockPromoted = bundle.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true)
            val isCheckUsername = bundle.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true)
            val isBlockSpecialChars = bundle.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false)
            val isBlockEmoji = bundle.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false)
            val isBlockGrok = bundle.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false)
            val userKeywords = bundle.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""
            val cloudKeywords = bundle.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
            val disabled = bundle.getStringArrayList(PrefsConstants.KEY_DISABLED_CLOUD_KEYWORDS)?.toSet() ?: emptySet()
            val whitelist = bundle.getString(PrefsConstants.KEY_WHITELIST, "") ?: ""

            engine.isEnabled = isEnabled
            engine.isBlockPromoted = isBlockPromoted
            engine.isCheckUsername = isCheckUsername
            engine.isBlockSpecialChars = isBlockSpecialChars
            engine.isBlockEmoji = isBlockEmoji
            engine.isBlockGrok = isBlockGrok

            if (cloudKeywords.isNotEmpty() || userKeywords.isNotEmpty() || !engine.hasKeywords()) {
                engine.updateKeywords(cloudKeywords, userKeywords, disabled)
            }
            if (whitelist.isNotEmpty()) {
                engine.updateWhitelist(whitelist.lines())
            }
        }

        /**
         * Direct XML parser for Android SharedPreferences XML files.
         */
        fun parsePrefsXml(file: File): Map<String, Any> {
            val result = mutableMapOf<String, Any>()
            if (!file.exists() || file.length() == 0L) return result

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            FileInputStream(file).use { fis ->
                parser.setInput(fis, "UTF-8")
                var eventType = parser.eventType
                var currentTag = ""
                var currentKey = ""
                val currentSet = mutableSetOf<String>()
                val stringBuffer = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            when (currentTag) {
                                "boolean" -> {
                                    val name = parser.getAttributeValue(null, "name")
                                    val value = parser.getAttributeValue(null, "value")?.toBoolean() ?: false
                                    if (name != null) result[name] = value
                                }
                                "int" -> {
                                    val name = parser.getAttributeValue(null, "name")
                                    val value = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                                    if (name != null) result[name] = value
                                }
                                "long" -> {
                                    val name = parser.getAttributeValue(null, "name")
                                    val value = parser.getAttributeValue(null, "value")?.toLongOrNull() ?: 0L
                                    if (name != null) result[name] = value
                                }
                                "string" -> {
                                    currentKey = parser.getAttributeValue(null, "name") ?: ""
                                    stringBuffer.clear()
                                }
                                "set" -> {
                                    currentKey = parser.getAttributeValue(null, "name") ?: ""
                                    currentSet.clear()
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text
                            if (currentTag == "string" && currentKey.isNotEmpty()) {
                                if (text != null) stringBuffer.append(text)
                            } else if (currentTag == "set" && text != null && text.isNotBlank()) {
                                currentSet.add(text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "string") {
                                if (currentKey.isNotEmpty()) {
                                    result[currentKey] = stringBuffer.toString()
                                }
                                currentKey = ""
                                stringBuffer.clear()
                            } else if (parser.name == "set") {
                                if (currentKey.isNotEmpty()) {
                                    result[currentKey] = currentSet.toSet()
                                }
                                currentKey = ""
                            }
                            currentTag = ""
                        }
                    }
                    eventType = parser.next()
                }
            }
            return result
        }
    }
}
