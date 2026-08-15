package com.xtwitter.blocker.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.xtwitter.blocker.R
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.databinding.ActivityTestFilterBinding
import com.xtwitter.blocker.engine.FilterResult
import com.xtwitter.blocker.engine.SpamFilterEngine

class TestFilterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestFilterBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Make sure engine is refreshed with latest preferences
        ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)

        binding.btnRunTest.setOnClickListener {
            runTest()
        }
    }

    private fun runTest() {
        val username = binding.etTestUsername.text?.toString()?.trim() ?: ""
        val content = binding.etTestContent.text?.toString() ?: ""

        val result = SpamFilterEngine.instance.shouldBlockTweet(
            fullText = content,
            screenName = username,
            name = username,
            isPromoted = false,
            hasGrokCard = content.contains("grok.com/share") || content.contains("x.com/i/grok")
        )

        binding.cardResult.visibility = View.VISIBLE

        when (result) {
            is FilterResult.Pass -> {
                binding.tvVerdict.text = "✅ 判定结果：【放行】"
                binding.tvVerdict.setTextColor(getColor(R.color.status_green))
                binding.tvDetail.text = "此内容未命中任何屏蔽词、用户名黑名单或特殊字符规则，正常展示。"
            }
            is FilterResult.Blocked -> {
                binding.tvVerdict.text = "🚫 判定结果：【拦截】"
                binding.tvVerdict.setTextColor(getColor(R.color.status_red))

                val reasonDesc = when (result.reason) {
                    FilterResult.BlockReason.PROMOTED_AD -> "推广广告推文"
                    FilterResult.BlockReason.KEYWORD_MATCH -> "评论正文命中屏蔽词库"
                    FilterResult.BlockReason.USERNAME_MATCH -> "用户名/昵称命中屏蔽规则 (${result.matchedRule ?: ""})"
                    FilterResult.BlockReason.SPECIAL_CHARS -> "检测到花体字/变体火星文字符"
                    FilterResult.BlockReason.EMOJI_SPAM -> "检测到垃圾 Emoji 堆砌"
                    FilterResult.BlockReason.GROK_CARD -> "包含 Grok 分享卡片"
                }
                binding.tvDetail.text = "拦截原因：$reasonDesc"
            }
        }
    }
}
