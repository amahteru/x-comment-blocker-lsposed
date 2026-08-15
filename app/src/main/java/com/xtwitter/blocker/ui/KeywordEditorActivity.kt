package com.xtwitter.blocker.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.databinding.ActivityKeywordEditorBinding
import com.xtwitter.blocker.engine.SpamFilterEngine

class KeywordEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeywordEditorBinding
    private lateinit var prefs: SharedPreferences
    private var mode = MODE_USER_KEYWORDS

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityKeywordEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getIntExtra(EXTRA_MODE, MODE_USER_KEYWORDS)
        prefs = ConfigManager.getPreferences(this)

        setupInsets()
        setupToolbar()
        loadData()
        setupListeners()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBarInsets.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentLayout) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = navInsets.bottom + 16)
            insets
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = if (mode == MODE_USER_KEYWORDS) "自定义屏蔽词" else "白名单用户"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadData() {
        val key = if (mode == MODE_USER_KEYWORDS) PrefsConstants.KEY_USER_KEYWORDS else PrefsConstants.KEY_WHITELIST
        val text = prefs.getString(key, "") ?: ""
        binding.etKeywords.setText(text)
        updateLineStats(text)
    }

    private fun setupListeners() {
        binding.etKeywords.addTextChangedListener { editable ->
            updateLineStats(editable?.toString() ?: "")
        }

        binding.btnSave.setOnClickListener {
            val content = binding.etKeywords.text?.toString() ?: ""
            val key = if (mode == MODE_USER_KEYWORDS) PrefsConstants.KEY_USER_KEYWORDS else PrefsConstants.KEY_WHITELIST
            prefs.edit().putString(key, content).apply()
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateLineStats(text: String) {
        val count = text.lines().count { it.isNotBlank() }
        binding.tvKeywordStats.text = "共 $count 条${if (mode == MODE_USER_KEYWORDS) "屏蔽词" else "白名单用户"}"
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_USER_KEYWORDS = 1
        const val MODE_WHITELIST = 2
    }
}
