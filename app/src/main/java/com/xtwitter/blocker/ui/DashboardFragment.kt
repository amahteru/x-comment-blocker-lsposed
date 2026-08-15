package com.xtwitter.blocker.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.xtwitter.blocker.R
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.databinding.FragmentDashboardBinding
import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.ModuleState
import com.xtwitter.blocker.hook.ModuleStatus

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: SharedPreferences

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PrefsConstants.KEY_BLOCKED_COUNT || key == PrefsConstants.KEY_CLOUD_KEYWORDS || key == PrefsConstants.KEY_USER_KEYWORDS) {
            activity?.runOnUiThread {
                updateStats()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)

        // Handle Status Bar Window Insets for AppBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBarInsets.top)
            insets
        }

        setupListeners()
        loadPreferences()
        updateStats()
    }

    override fun onResume() {
        super.onResume()
        updateStats()
        updateStatusCard(binding.switchMaster.isChecked)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (_: Exception) {}
        _binding = null
    }

    private fun setupListeners() {
        val ctx = requireContext()

        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_ENABLED, isChecked).apply()
            updateStatusCard(isChecked)
            ConfigManager.fromContext(ctx).loadToEngine(SpamFilterEngine.instance, ctx)
        }

        binding.switchBlockPromoted.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_PROMOTED, isChecked).apply()
            ConfigManager.fromContext(ctx).loadToEngine(SpamFilterEngine.instance, ctx)
        }

        binding.switchCheckUsername.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_CHECK_USERNAME, isChecked).apply()
            ConfigManager.fromContext(ctx).loadToEngine(SpamFilterEngine.instance, ctx)
        }

        binding.switchBlockSpecialChars.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_SPECIAL_CHARS, isChecked).apply()
            ConfigManager.fromContext(ctx).loadToEngine(SpamFilterEngine.instance, ctx)
        }

        binding.switchBlockEmoji.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_EMOJI, isChecked).apply()
            ConfigManager.fromContext(ctx).loadToEngine(SpamFilterEngine.instance, ctx)
        }

        binding.switchBlockGrok.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_BLOCK_GROK, isChecked).apply()
            ConfigManager.fromContext(ctx).loadToEngine(SpamFilterEngine.instance, ctx)
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
        val isHookActive = ModuleStatus.isModuleActive()
        val state = ModuleStatus.resolveModuleState(isHookActive = isHookActive, isMasterEnabled = isEnabled)

        when (state) {
            ModuleState.NOT_ACTIVATED -> {
                binding.ivStatusDot.setBackgroundResource(R.drawable.circle_red)
                binding.tvStatusTitle.setText(R.string.status_module_inactive)
                binding.tvStatusSubtitle.setText(R.string.status_desc_inactive)
            }
            ModuleState.ACTIVE_ENABLED -> {
                binding.ivStatusDot.setBackgroundResource(R.drawable.circle_green)
                binding.tvStatusTitle.setText(R.string.status_module_active)
                binding.tvStatusSubtitle.setText(R.string.status_desc_active)
            }
            ModuleState.ACTIVE_PAUSED -> {
                binding.ivStatusDot.setBackgroundResource(R.drawable.circle_yellow)
                binding.tvStatusTitle.setText(R.string.status_module_paused)
                binding.tvStatusSubtitle.setText(R.string.status_desc_paused)
            }
        }
    }

    fun updateStats() {
        if (_binding == null) return
        val blockedCount = prefs.getInt(PrefsConstants.KEY_BLOCKED_COUNT, 0)
        binding.tvBlockedCount.text = blockedCount.toString()

        val cloudKeywords = prefs.getString(PrefsConstants.KEY_CLOUD_KEYWORDS, "") ?: ""
        val cloudCount = if (cloudKeywords.isBlank()) 0 else cloudKeywords.lines().count { it.isNotBlank() }
        binding.tvCloudKeywordsCount.text = cloudCount.toString()

        val userKeywords = prefs.getString(PrefsConstants.KEY_USER_KEYWORDS, "") ?: ""
        val userCount = if (userKeywords.isBlank()) 0 else userKeywords.lines().count { it.isNotBlank() }
        binding.tvUserKeywordsCount.text = userCount.toString()
    }
}
