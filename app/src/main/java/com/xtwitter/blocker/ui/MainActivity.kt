package com.xtwitter.blocker.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.xtwitter.blocker.R
import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.data.PrefsConstants
import com.xtwitter.blocker.databinding.ActivityMainBinding
import com.xtwitter.blocker.engine.SpamFilterEngine

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ConfigManager.getPreferences(this)

        setupInsets()
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun setupInsets() {
        // Handle bottom navigation bar insets so BottomNavigationView respects the system navigation bar / gesture bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navInsets.bottom)
            insets
        }
    }

    private fun setupNavigation() {
        val fragments = listOf(
            DashboardFragment(),
            RulesFragment(),
            SettingsFragment()
        )

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        // Disable swipe if desired, or keep smooth switching
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = 2

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    binding.viewPager.setCurrentItem(0, false)
                    true
                }
                R.id.nav_rules -> {
                    binding.viewPager.setCurrentItem(1, false)
                    true
                }
                R.id.nav_settings -> {
                    binding.viewPager.setCurrentItem(2, false)
                    true
                }
                else -> false
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val itemId = when (position) {
                    0 -> R.id.nav_dashboard
                    1 -> R.id.nav_rules
                    2 -> R.id.nav_settings
                    else -> R.id.nav_dashboard
                }
                if (binding.bottomNav.selectedItemId != itemId) {
                    binding.bottomNav.selectedItemId = itemId
                }
            }
        })
    }
}
