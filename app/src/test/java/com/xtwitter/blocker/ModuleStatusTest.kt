package com.xtwitter.blocker

import com.xtwitter.blocker.hook.ModuleState
import com.xtwitter.blocker.hook.ModuleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModuleStatusTest {

    @Test
    fun testDefaultIsModuleActiveReturnsFalse() {
        // Without LSPosed hooking the process, isModuleActive must return false
        assertFalse(ModuleStatus.isModuleActive())
    }

    @Test
    fun testResolveModuleStateWhenHookNotActiveAndMasterEnabled() {
        // Even if master switch is ON, if hook is not active, status must be NOT_ACTIVATED
        val state = ModuleStatus.resolveModuleState(isHookActive = false, isMasterEnabled = true)
        assertEquals(ModuleState.NOT_ACTIVATED, state)
    }

    @Test
    fun testResolveModuleStateWhenHookNotActiveAndMasterDisabled() {
        val state = ModuleStatus.resolveModuleState(isHookActive = false, isMasterEnabled = false)
        assertEquals(ModuleState.NOT_ACTIVATED, state)
    }

    @Test
    fun testResolveModuleStateWhenHookActiveAndMasterEnabled() {
        val state = ModuleStatus.resolveModuleState(isHookActive = true, isMasterEnabled = true)
        assertEquals(ModuleState.ACTIVE_ENABLED, state)
    }

    @Test
    fun testResolveModuleStateWhenHookActiveAndMasterDisabled() {
        val state = ModuleStatus.resolveModuleState(isHookActive = true, isMasterEnabled = false)
        assertEquals(ModuleState.ACTIVE_PAUSED, state)
    }
}
