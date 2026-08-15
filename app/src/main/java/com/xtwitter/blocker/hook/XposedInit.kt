package com.xtwitter.blocker.hook

import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.engine.SpamFilterEngine
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "XCommentBlocker-Init"
        val TARGET_PACKAGES = setOf(
            "com.twitter.android",
            "com.twitter.android.alpha",
            "com.twitter.android.beta"
        )
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        try {
            ConfigManager.forXposed().loadToEngine(SpamFilterEngine.instance)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to load initial config in initZygote: ${t.message}")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return
        }

        XposedBridge.log("[$TAG] Target package loaded: ${lpparam.packageName} (process: ${lpparam.processName})")

        try {
            TwitterHook.initHook(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Exception during TwitterHook initialization: ${t.message}")
        }
    }
}
