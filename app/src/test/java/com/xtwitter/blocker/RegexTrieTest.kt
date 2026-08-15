package com.xtwitter.blocker

import com.xtwitter.blocker.engine.RegexTrie
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexTrieTest {

    @Test
    fun testTrieRegexMatching() {
        val keywords = listOf("无偿", "万达广场", "男大弟弟", "sao货", "骚货")
        val pattern = RegexTrie.buildTriePattern(keywords)

        assertNotNull(pattern)

        assertTrue(pattern!!.matcher("这是万达广场附近的测试").find())
        assertTrue(pattern.matcher("无偿提供资源").find())
        assertTrue(pattern.matcher("SAO货一个").find()) // case-insensitive test
        assertTrue(pattern.matcher("骚货").find())

        assertFalse(pattern.matcher("今天天气真好，出去散步").find())
        assertFalse(pattern.matcher("毫无瓜葛").find())
    }

    @Test
    fun testPruningRedundantSubstrings() {
        // "sao" is a substring of "sao货", so "sao" alone is enough to match both
        val keywords = listOf("sao", "sao货", "sao货大合集")
        val pattern = RegexTrie.buildTriePattern(keywords)

        assertNotNull(pattern)
        assertTrue(pattern!!.matcher("sao").find())
        assertTrue(pattern.matcher("sao货").find())
        assertTrue(pattern.matcher("sao货大合集").find())
    }

    @Test
    fun testCustomRegexParsing() {
        val raw = """
            无偿
            /v[x信][:：\s]*[a-zA-Z0-9_-]+/i
            /TG[:：\s]*@[a-zA-Z0-9_]+/i
        """.trimIndent()

        val parsed = RegexTrie.parseKeywords(raw)
        assertTrue(parsed.plainKeywords.contains("无偿"))
        assertTrue(parsed.customRegexes.size == 2)

        val vxRegex = parsed.customRegexes[0]
        assertTrue(vxRegex.matcher("加我vx: test_123 领取").find())
        assertTrue(vxRegex.matcher("VX ： abc_888").find())
        assertFalse(vxRegex.matcher("这是一条正常推文").find())
    }

    @Test
    fun testLargeKeywordListPerformance() {
        val keywords = (1..1000).map { "spam_keyword_$it" } + listOf("真实资源", "同城约会", "点我头像")
        val pattern = RegexTrie.buildTriePattern(keywords)
        assertNotNull(pattern)

        val start = System.nanoTime()
        val match = pattern!!.matcher("今天在同城约会点我头像获取真实资源").find()
        val durationMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(match)
        assertTrue("Matching should take less than 5ms, took: ${durationMs}ms", durationMs < 5.0)
    }
}
