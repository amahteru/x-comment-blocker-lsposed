package com.xtwitter.blocker

import com.xtwitter.blocker.engine.RegexTrie
import com.xtwitter.blocker.engine.SpamCharCleaner
import com.xtwitter.blocker.engine.SpamFilterEngine
import org.junit.Test

import org.junit.Assert.*

class DebugKeywordMatchTest {

    @Test
    fun testBug1_FullwidthAndSpecialUnicodeMatching() {
        val engine = SpamFilterEngine()
        engine.isEnabled = true
        engine.isCheckUsername = true

        // Keyword has full-width bracket
        val keywords = "（接任务\n今夜🈲️不归"
        engine.updateKeywords(keywords, "")

        // 1. Full-width bracket match
        val res1 = engine.shouldBlockTweet(
            fullText = "🤐",
            screenName = "oriwen368540",
            name = "豆豆母狗（接任务"
        )
        assertTrue("豆豆母狗（接任务 should be blocked by （接任务", res1 is com.xtwitter.blocker.engine.FilterResult.Blocked)

        // 2. Compatibility character match
        val res2 = engine.shouldBlockTweet(
            fullText = "😍",
            screenName = "seanwhite147409",
            name = "今夜🈲️不归"
        )
        assertTrue("今夜🈲️不归 should be blocked by 今夜🈲️不归", res2 is com.xtwitter.blocker.engine.FilterResult.Blocked)
    }

    @Test
    fun testBug2_EmojiRegexOnRawAndNormalized() {
        val engine = SpamFilterEngine()
        engine.isEnabled = true
        engine.isCheckUsername = true

        val regex = "/(?=.*(?:🍑|🈷️|❤️))(?=.*(?:今夜|今晚|不归))/"
        engine.updateKeywords(regex, "")

        // 1. 今夜🈷️不归
        val res1 = engine.shouldBlockTweet(
            fullText = "😍",
            screenName = "user1",
            name = "今夜🈷️不归"
        )
        assertTrue("今夜🈷️不归 should be blocked by emoji regex", res1 is com.xtwitter.blocker.engine.FilterResult.Blocked)

        // 2. 今晚🈷️不归
        val res2 = engine.shouldBlockTweet(
            fullText = "😍",
            screenName = "user2",
            name = "今晚🈷️不归"
        )
        assertTrue("今晚🈷️不归 should be blocked by emoji regex", res2 is com.xtwitter.blocker.engine.FilterResult.Blocked)
    }

    @Test
    fun testWithCompleteKeywordsTxt() {
        val engine = SpamFilterEngine()
        engine.isEnabled = true
        engine.isCheckUsername = true

        val file = java.io.File("../x-comment-blocker/keywords.txt").let {
            if (it.exists()) it else java.io.File("../../x-comment-blocker/keywords.txt")
        }
        val keywordsTxt = file.readText()
        engine.updateKeywords(keywordsTxt, "")

        // 1. 豆豆母狗（接任务
        val res1 = engine.shouldBlockTweet(
            fullText = "🤐",
            screenName = "oriwen368540",
            name = "豆豆母狗（接任务"
        )
        assertTrue("豆豆母狗（接任务 must be blocked by keywords.txt", res1 is com.xtwitter.blocker.engine.FilterResult.Blocked)

        // 2. 今夜🈷️不归
        val res2 = engine.shouldBlockTweet(
            fullText = "😍",
            screenName = "seanwhite147409",
            name = "今夜🈷️不归"
        )
        assertTrue("今夜🈷️不归 must be blocked by keywords.txt", res2 is com.xtwitter.blocker.engine.FilterResult.Blocked)

        // 3. Normal tweet must pass
        val resNormal = engine.shouldBlockTweet(
            fullText = "今天天气真好，出去散步！",
            screenName = "normal_user",
            name = "张三"
        )
        assertEquals(com.xtwitter.blocker.engine.FilterResult.Pass, resNormal)
    }
}
