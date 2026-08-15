package com.xtwitter.blocker.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xtwitter.blocker.R
import com.xtwitter.blocker.data.CloudSyncManager
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.databinding.ActivityMainBinding
import com.xtwitter.blocker.engine.SpamFilterEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        initDefaultAssetsIfNeeded()
        setupUI()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        updateStats()
        ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)
    }

    private fun initDefaultAssetsIfNeeded() {
        val cloudKeywords = prefs.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, null)
        if (cloudKeywords == null) {
            try {
                val assetContent = assets.open("default_keywords.txt").bufferedReader().use { it.readText() }
                prefs.edit().putString(PrefsConstants.KEY_CLOUD_KEYWORDS, assetContent).apply()
            } catch (_: Exception) {}
        }
    }

    private fun setupUI() {
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_ENABLED, isChecked).apply()
            updateStatusCard(isChecked)
            ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)
        }

        binding.switchBlockPromoted.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, isChecked).apply()
            ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)
        }

        binding.switchCheckUsername.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_CHECK_USERNAME, isChecked).apply()
            ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)
        }

        binding.switchBlockSpecialChars.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, isChecked).apply()
            ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)
        }

        binding.switchBlockEmoji.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_EMOJI, isChecked).apply()
            ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)
        }

        binding.switchBlockGrok.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_GROK, isChecked).apply()
            ConfigManager.fromContext(this).loadToEngine(SpamFilterEngine.instance, this)
        }

        binding.btnEditKeywords.setOnClickListener {
            val intent = Intent(this, KeywordEditorActivity::class.java).apply {
                putExtra(KeywordEditorActivity.EXTRA_MODE, KeywordEditorActivity.MODE_USER_KEYWORDS)
            }
            startActivity(intent)
        }

        binding.btnEditWhitelist.setOnClickListener {
            val intent = Intent(this, KeywordEditorActivity::class.java).apply {
                putExtra(KeywordEditorActivity.EXTRA_MODE, KeywordEditorActivity.MODE_WHITELIST)
            }
            startActivity(intent)
        }

        binding.btnTestFilter.setOnClickListener {
            startActivity(Intent(this, TestFilterActivity::class.java))
        }

        binding.btnSyncCloud.setOnClickListener {
            syncCloudKeywords()
        }
    }

    private fun loadPreferences() {
        val isEnabled = prefs.getBoolean(PrefsConstants.KEY_ENABLED, true)
        binding.switchMaster.isChecked = isEnabled
        updateStatusCard(isEnabled)

        binding.switchBlockPromoted.isChecked = prefs.getBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, true)
        binding.switchCheckUsername.isChecked = prefs.getBoolean(PrefsConstants.KEY_CHECK_USERNAME, true)
        binding.switchBlockSpecialChars.isChecked = prefs.getBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, false)
        binding.switchBlockEmoji.isChecked = prefs.getBoolean(PrefsConstants.KEY_BLOCK_EMOJI, false)
        binding.switchBlockGrok.isChecked = prefs.getBoolean(PrefsConstants.KEY_BLOCK_GROK, false)
    }

    private fun updateStatusCard(isEnabled: Boolean) {
        if (isEnabled) {
            binding.ivStatusDot.setBackgroundResource(R.drawable.circle_green)
            binding.tvStatusTitle.setText(R.string.status_module_active)
        } else {
            binding.ivStatusDot.setBackgroundResource(R.drawable.circle_red)
            binding.tvStatusTitle.text = "拦截功能已暂停"
        }
    }

    private fun updateStats() {
        val blockedCount = prefs.getInt(PrefsConstants.KEY_BLOCKED_COUNT, 0)
        binding.tvBlockedCount.text = blockedCount.toString()

        val cloudKeywords = prefs.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
        val cloudCount = if (cloudKeywords.isBlank()) 0 else cloudKeywords.lines().count { it.isNotBlank() }
        binding.tvCloudKeywordsCount.text = cloudCount.toString()

        val userKeywords = prefs.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""
        val userCount = if (userKeywords.isBlank()) 0 else userKeywords.lines().count { it.isNotBlank() }
        binding.tvUserKeywordsCount.text = userCount.toString()

        val lastSync = prefs.getLong(PrefsConstants.KEY_LAST_SYNC_TIME, 0L)
        if (lastSync > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.tvLastSyncTime.text = getString(R.string.stat_last_sync, sdf.format(Date(lastSync)))
        } else {
            binding.tvLastSyncTime.text = getString(R.string.stat_last_sync, getString(R.string.never_synced))
        }
    }

    private fun syncCloudKeywords() {
        binding.btnSyncCloud.isEnabled = false
        binding.btnSyncCloud.text = "正在同步..."

        lifecycleScope.launch {
            val result = CloudSyncManager.syncKeywords(this@MainActivity)
            binding.btnSyncCloud.isEnabled = true
            binding.btnSyncCloud.setText(R.string.btn_sync_now)

            result.onSuccess { count ->
                Toast.makeText(this@MainActivity, "云端词库同步成功，共 $count 条", Toast.LENGTH_SHORT).show()
                updateStats()
                ConfigManager.fromContext(this@MainActivity).loadToEngine(SpamFilterEngine.instance, this@MainActivity)
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "同步失败: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
