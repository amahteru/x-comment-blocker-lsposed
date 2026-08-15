package com.xtwitter.blocker.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.xtwitter.blocker.R
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.databinding.FragmentSettingsBinding
import com.xtwitter.blocker.hook.ModuleState
import com.xtwitter.blocker.hook.ModuleStatus

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        // Handle Status Bar Window Insets for AppBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBarInsets.top)
            insets
        }

        displayAppVersion()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateModuleStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun displayAppVersion() {
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvAppVersion.text = "v${pInfo.versionName}"
        } catch (_: Exception) {
            binding.tvAppVersion.text = "v1.0.0"
        }
    }

    private fun updateModuleStatus() {
        val isHookActive = ModuleStatus.isModuleActive()
        val isMasterEnabled = prefs.getBoolean(PrefsConstants.KEY_ENABLED, true)
        val state = ModuleStatus.resolveModuleState(isHookActive = isHookActive, isMasterEnabled = isMasterEnabled)

        when (state) {
            ModuleState.NOT_ACTIVATED -> {
                binding.ivSettingsStatusDot.setBackgroundResource(R.drawable.circle_red)
                binding.tvSettingsStatusText.setText(R.string.status_module_inactive)
            }
            ModuleState.ACTIVE_ENABLED -> {
                binding.ivSettingsStatusDot.setBackgroundResource(R.drawable.circle_green)
                binding.tvSettingsStatusText.setText(R.string.status_module_active)
            }
            ModuleState.ACTIVE_PAUSED -> {
                binding.ivSettingsStatusDot.setBackgroundResource(R.drawable.circle_yellow)
                binding.tvSettingsStatusText.setText(R.string.status_module_paused)
            }
        }
    }

    private fun setupListeners() {
        binding.btnOpenLSPosed.setOnClickListener {
            openLSPosedManager()
        }

        binding.cardGitHub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tzucet/x-comment-blocker-lsposed"))
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLSPosedManager() {
        val lsposedPackages = listOf(
            "org.lsposed.manager",
            "org.lsposed.manager.nightly",
            "io.github.lsposed.manager"
        )
        val pm = requireContext().packageManager
        for (pkg in lsposedPackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        Toast.makeText(requireContext(), R.string.settings_open_lsposed_fail, Toast.LENGTH_LONG).show()
    }
}
