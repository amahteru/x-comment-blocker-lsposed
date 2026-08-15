package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamCharCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamCharCleanerTest {

    @Test
    fun testRemoveInvisibleChars() {
        // Zero-width space \u200B, Zero-width joiner \u200D, BOM \uFEFF
        val textWithZw = "万\u200B达\u200C广\u200D场\uFEFF"
        val cleaned = SpamCharCleaner.removeInvisibleChars(textWithZw)
        assertEquals("万达广场", cleaned)
    }

    @Test
    fun testExtractCleanScreenName() {
        assertEquals("elonmusk", SpamCharCleaner.extractCleanScreenName("@elonmusk"))
        assertEquals("elonmusk", SpamCharCleaner.extractCleanScreenName("https://x.com/elonmusk"))
        assertEquals("elonmusk", SpamCharCleaner.extractCleanScreenName("@elonmusk/status/12345"))
        assertEquals("test_bot", SpamCharCleaner.extractCleanScreenName("@test_bot?s=20"))
    }

    @Test
    fun testContainsSpamChars() {
        // Obfuscated mathematical bold text: 𝑻𝒆𝒔𝒕
        val mathText = "\uD835\uDC7B\uD835\uDC86\uD835\uDC94\uD835\uDC95"
        assertTrue(SpamCharCleaner.containsSpamChars(mathText))

        val normalText = "Hello world this is normal text"
        assertFalse(SpamCharCleaner.containsSpamChars(normalText))
    }

    @Test
    fun testContainsEmoji() {
        assertTrue(SpamCharCleaner.containsEmoji("🔥 精彩内容 👇"))
        assertTrue(SpamCharCleaner.containsEmoji("😀"))
        assertFalse(SpamCharCleaner.containsEmoji("纯文本无表情"))
    }

    @Test
    fun testRemoveSeparators() {
        assertEquals("找萢友", SpamCharCleaner.removeSeparators("找.萢友"))
        assertEquals("同城约", SpamCharCleaner.removeSeparators("同-城-约"))
        assertEquals("微信", SpamCharCleaner.removeSeparators("微_信"))
        assertEquals("同城约", SpamCharCleaner.removeSeparators("同·城•约"))
        assertEquals("同城约", SpamCharCleaner.removeSeparators("同，城。约"))
    }
}
