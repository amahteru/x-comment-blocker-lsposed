package com.xtwitter.blocker

import com.xtwitter.blocker.engine.FilterResult
import com.xtwitter.blocker.engine.SpamFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpamFilterEngineTest {

    private lateinit var engine: SpamFilterEngine

    @Before
    fun setup() {
        engine = SpamFilterEngine()
        engine.isEnabled = true
        engine.isBlockPromoted = true
        engine.isCheckUsername = true
        engine.isBlockSpecialChars = false
        engine.isBlockEmoji = false
        engine.isBlockGrok = false

        engine.updateKeywords(
            cloudKeywords = "万达广场\n同城约\n无偿",
            userKeywords = "自定义广告\n/tg[:：]\\s*@[a-z0-9]+/i"
        )
        engine.updateWhitelist(listOf("friendly_friend", "official_news"))
    }

    @Test
    fun testPassNormalTweet() {
        val result = engine.shouldBlockTweet(
            fullText = "今天讨论一下人工智能的发展历程",
            screenName = "tech_blogger",
            name = "Tech Blogger"
        )
        assertEquals(FilterResult.Pass, result)
    }

    @Test
    fun testBlockCloudKeyword() {
        val result = engine.shouldBlockTweet(
            fullText = "万\u200B达广场附近有没有单男弟弟",
            screenName = "some_bot",
            name = "小甜甜"
        )
        assertTrue(result is FilterResult.Blocked)
        assertEquals(FilterResult.BlockReason.KEYWORD_MATCH, (result as FilterResult.Blocked).reason)
    }

    @Test
    fun testBlockUserCustomRegex() {
        val result = engine.shouldBlockTweet(
            fullText = "获取最新福利请联系 TG: @best_channel_123",
            screenName = "channel_bot",
            name = "福利君"
        )
        assertTrue(result is FilterResult.Blocked)
        assertEquals(FilterResult.BlockReason.KEYWORD_MATCH, (result as FilterResult.Blocked).reason)
    }

    @Test
    fun testWhitelistBypass() {
        // Even if contains keyword "无偿", whitelist user is permitted
        val result = engine.shouldBlockTweet(
            fullText = "我们是一个无偿开源组织，欢迎贡献代码",
            screenName = "@official_news",
            name = "开源资讯"
        )
        assertEquals(FilterResult.Pass, result)
    }

    @Test
    fun testUsernameMatch() {
        val result = engine.shouldBlockTweet(
            fullText = "这是一条普通日常推文",
            screenName = "wanda_bot",
            name = "同城约 - 认证客服"
        )
        assertTrue(result is FilterResult.Blocked)
        assertEquals(FilterResult.BlockReason.USERNAME_MATCH, (result as FilterResult.Blocked).reason)
    }

    @Test
    fun testPromotedTweet() {
        val result = engine.shouldBlockTweet(
            fullText = "Download this amazing game now!",
            screenName = "game_dev",
            name = "Awesome Game",
            isPromoted = true
        )
        assertTrue(result is FilterResult.Blocked)
        assertEquals(FilterResult.BlockReason.PROMOTED_AD, (result as FilterResult.Blocked).reason)
    }

    @Test
    fun testFullwidthAndCompatibilityUnicodeMatching() {
        engine.updateKeywords(
            cloudKeywords = "（接任务\n今夜🈲️不归",
            userKeywords = ""
        )

        // 1. Full-width bracket match (豆豆母狗（接任务)
        val res1 = engine.shouldBlockTweet(
            fullText = "🤐",
            screenName = "oriwen368540",
            name = "豆豆母狗（接任务"
        )
        assertTrue("豆豆母狗（接任务 should be blocked by （接任务", res1 is FilterResult.Blocked)

        // 2. Compatibility character match (今夜🈲️不归)
        val res2 = engine.shouldBlockTweet(
            fullText = "😍",
            screenName = "seanwhite147409",
            name = "今夜🈲️不归"
        )
        assertTrue("今夜🈲️不归 should be blocked by 今夜🈲️不归", res2 is FilterResult.Blocked)
    }

    @Test
    fun testEmojiRegexOnRawAndNormalized() {
        val regex = "/(?=.*(?:🍑|🈷️|❤️))(?=.*(?:今夜|今晚|不归))/"
        engine.updateKeywords(regex, "")

        // 1. 今夜🈷️不归
        val res1 = engine.shouldBlockTweet(
            fullText = "😍",
            screenName = "user1",
            name = "今夜🈷️不归"
        )
        assertTrue("今夜🈷️不归 should be blocked by emoji regex", res1 is FilterResult.Blocked)

        // 2. 今晚🈷️不归
        val res2 = engine.shouldBlockTweet(
            fullText = "😍",
            screenName = "user2",
            name = "今晚🈷️不归"
        )
        assertTrue("今晚🈷️不归 should be blocked by emoji regex", res2 is FilterResult.Blocked)
    }

    @Test
    fun testCompleteKeywordsTxtIntegration() {
        val file = java.io.File("../x-comment-blocker/keywords.txt").let {
            if (it.exists()) it else java.io.File("../../x-comment-blocker/keywords.txt")
        }
        if (file.exists()) {
            val keywordsTxt = file.readText()
            engine.updateKeywords(keywordsTxt, "")

            // 1. 豆豆母狗（接任务
            val res1 = engine.shouldBlockTweet(
                fullText = "🤐",
                screenName = "oriwen368540",
                name = "豆豆母狗（接任务"
            )
            assertTrue("豆豆母狗（接任务 must be blocked by keywords.txt", res1 is FilterResult.Blocked)

            // 2. 今夜🈷️不归
            val res2 = engine.shouldBlockTweet(
                fullText = "😍",
                screenName = "seanwhite147409",
                name = "今夜🈷️不归"
            )
            assertTrue("今夜🈷️不归 must be blocked by keywords.txt", res2 is FilterResult.Blocked)

            // 3. Normal tweet must pass
            val resNormal = engine.shouldBlockTweet(
                fullText = "今天天气真好，出去散步！",
                screenName = "normal_user",
                name = "张三"
            )
            assertEquals(FilterResult.Pass, resNormal)
        }
    }
}
