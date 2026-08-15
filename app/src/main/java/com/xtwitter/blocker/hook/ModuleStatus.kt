package com.xtwitter.blocker.hook

import androidx.annotation.Keep

enum class ModuleState {
    NOT_ACTIVATED,  // LSPosed is not loaded / module not active
    ACTIVE_ENABLED, // LSPosed active and master switch enabled
    ACTIVE_PAUSED   // LSPosed active but master switch disabled
}

object ModuleStatus {

    /**
     * Hooked by XposedInit in LSPosed environment to return true.
     * When LSPosed is active and module is loaded for this app, this method returns true.
     * Otherwise, returns false.
     */
    @JvmStatic
    @Keep
    fun isModuleActive(): Boolean {
        return false
    }

    /**
     * Determines current operational state based on framework active status and user preference.
     */
    @JvmStatic
    fun resolveModuleState(isHookActive: Boolean, isMasterEnabled: Boolean): ModuleState {
        return when {
            !isHookActive -> ModuleState.NOT_ACTIVATED
            isMasterEnabled -> ModuleState.ACTIVE_ENABLED
            else -> ModuleState.ACTIVE_PAUSED
        }
    }
}
