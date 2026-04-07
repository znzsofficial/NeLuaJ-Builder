package com.nekolaska

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.nekolaska.utils.Toaster

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        CrashHandler.instance.init(this)
        Toaster.init(this)
    }
}