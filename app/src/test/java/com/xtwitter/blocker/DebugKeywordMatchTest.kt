package com.xtwitter.blocker

import com.xtwitter.blocker.engine.RegexTrie
import com.xtwitter.blocker.engine.SpamCharCleaner
import com.xtwitter.blocker.engine.SpamFilterEngine
import org.junit.Test

class DebugKeywordMatchTest {

    @Test
    fun debugMatch() {
        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isCheckUsername = true

        val defaultKeywords = "应该没人比我玩的开了吧\n我福不黑不信你看\n同城上门\n选妃"

        println("Default keywords length: ${defaultKeywords.length}")
        val parsed = RegexTrie.parseKeywords(defaultKeywords)
        println("Plain keywords count: ${parsed.plainKeywords.size}")
        println("Custom regexes count: ${parsed.customRegexes.size}")

        val text = "应该没人比我玩的开了吧😏🚑我福不黑不信你看"
        val normalized = SpamCharCleaner.normalizeText(text)
        println("Normalized text: $normalized")

        engine.updateKeywords(defaultKeywords, "")

        val match = engine.matchesKeywords(text)
        println("Matches keywords: $match")

        for (plain in parsed.plainKeywords) {
            if (normalized.contains(plain.lowercase())) {
                println("Matched plain keyword: $plain")
            }
        }

        for (regex in parsed.customRegexes) {
            if (regex.matcher(normalized).find()) {
                println("Matched regex: ${regex.pattern()}")
            }
        }
    }
}
