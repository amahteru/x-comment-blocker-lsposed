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
}
