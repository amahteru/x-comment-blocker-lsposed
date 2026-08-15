package com.xtwitter.blocker.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xtwitter.blocker.R
import com.xtwitter.blocker.data.CloudSyncManager
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.databinding.FragmentRulesBinding
import com.xtwitter.blocker.engine.SpamFilterEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RulesFragment : Fragment() {

    private var _binding: FragmentRulesBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = ConfigManager.getPreferences(requireContext())

        // Handle Status Bar Window Insets for AppBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBarInsets.top)
            insets
        }

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupListeners() {
        binding.cardUserKeywords.setOnClickListener {
            val intent = Intent(requireContext(), KeywordEditorActivity::class.java).apply {
                putExtra(KeywordEditorActivity.EXTRA_MODE, KeywordEditorActivity.MODE_USER_KEYWORDS)
            }
            startActivity(intent)
        }

        binding.cardWhitelist.setOnClickListener {
            val intent = Intent(requireContext(), KeywordEditorActivity::class.java).apply {
                putExtra(KeywordEditorActivity.EXTRA_MODE, KeywordEditorActivity.MODE_WHITELIST)
            }
            startActivity(intent)
        }

        binding.btnSyncCloud.setOnClickListener {
            syncCloudKeywords()
        }
    }

    fun updateStats() {
        if (_binding == null) return

        val cloudKeywords = prefs.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
        val cloudCount = if (cloudKeywords.isBlank()) 0 else cloudKeywords.lines().count { it.isNotBlank() }
        binding.tvCloudRulesCount.text = "云端词条：$cloudCount 条"

        val lastSync = prefs.getLong(PrefsConstants.KEY_LAST_SYNC_TIME, 0L)
        if (lastSync > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.tvLastSyncTime.text = getString(R.string.stat_last_sync, sdf.format(Date(lastSync)))
        } else {
            binding.tvLastSyncTime.text = getString(R.string.stat_last_sync, getString(R.string.never_synced))
        }

        val userKeywords = prefs.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""
        val userCount = if (userKeywords.isBlank()) 0 else userKeywords.lines().count { it.isNotBlank() }
        binding.tvUserKeywordsCount.text = "当前包含 $userCount 条自定义规则"

        val whitelist = prefs.getString(PrefsConstants.KEY_WHITELIST, "") ?: ""
        val whitelistCount = if (whitelist.isBlank()) 0 else whitelist.lines().count { it.isNotBlank() }
        binding.tvWhitelistCount.text = "当前包含 $whitelistCount 位白名单用户"
    }

    private fun syncCloudKeywords() {
        val ctx = context ?: return
        binding.btnSyncCloud.isEnabled = false
        binding.btnSyncCloud.text = "正在同步..."

        viewLifecycleOwner.lifecycleScope.launch {
            val result = CloudSyncManager.syncKeywords(ctx)
            if (_binding == null) return@launch

            binding.btnSyncCloud.isEnabled = true
            binding.btnSyncCloud.setText(R.string.btn_sync_now)

            result.onSuccess { count ->
                Toast.makeText(ctx, "云端词库同步成功，共 $count 条", Toast.LENGTH_SHORT).show()
                updateStats()
            }.onFailure { err ->
                Toast.makeText(ctx, "同步失败: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
