package com.xtwitter.blocker

import com.xtwitter.blocker.engine.FilterResult
import com.xtwitter.blocker.engine.SpamFilterEngine
import org.junit.Assert.*
import org.junit.Test

class RealSpamFilterTest {

    @Test
    fun testExactSpamCommentsFromLiveFeed() {
        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isCheckUsername = true

        val testKeywords = "比她好看的没她骚\n同城上门\n线下选妃\n我福不黑不信你看\n应该没人比我玩的开了吧"
        engine.updateKeywords(testKeywords, "")

        val text1 = "@lugeneralBtc 比她好看的没她骚比她骚的没她好看 @ffoo8899 ⁮♂️‍♀️12"
        val result1 = engine.shouldBlockTweet(fullText = text1, screenName = "sk8_th", name = "matheus henrique")
        assertTrue("Text 1 should be blocked: $text1", result1 is FilterResult.Blocked)

        val text2 = "应该没人比我玩的开了吧😏🚑我福不黑不信你看"
        val result2 = engine.shouldBlockTweet(fullText = text2, screenName = "luoqi123", name = "罗琦🌸同城上门♥线下选妃")
        assertTrue("Text 2 should be blocked: $text2", result2 is FilterResult.Blocked)

        val text3 = "应该没人比我玩的更开了吧🤓🚄我福不黑不信你看"
        val result3 = engine.shouldBlockTweet(fullText = text3, screenName = "jotan213922", name = "凌双🌸")
        assertTrue("Text 3 should be blocked: $text3", result3 is FilterResult.Blocked)

        val normalText = "@lugeneralBtc 上海好像也没有古茗"
        val normalResult = engine.shouldBlockTweet(fullText = normalText, screenName = "Lindalive1984", name = "Linda")
        assertTrue("Normal tweet should pass", normalResult is FilterResult.Pass)
    }
}
