package com.xtwitter.blocker.hook

import com.xtwitter.blocker.data.ConfigManager
import com.xtwitter.blocker.engine.SpamFilterEngine
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "XCommentBlocker-Init"
        const val MODULE_PACKAGE = "com.xtwitter.blocker"
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
        if (lpparam.packageName == MODULE_PACKAGE) {
            hookModuleStatus(lpparam.classLoader)
            return
        }

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

    private fun hookModuleStatus(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.xtwitter.blocker.hook.ModuleStatus",
                classLoader,
                "isModuleActive",
                XC_MethodReplacement.returnConstant(true)
            )
            XposedBridge.log("[$TAG] Successfully hooked ModuleStatus.isModuleActive")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook ModuleStatus.isModuleActive: ${t.message}")
        }
    }
}
