package com.xtwitter.blocker

import android.app.Application
import com.google.android.material.color.DynamicColors

class XCommentBlockerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply Material You / Monet Dynamic Colors on Android 12+ (API 31+)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
