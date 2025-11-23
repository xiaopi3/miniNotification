package com.example.mininotification

import android.app.Application

class MyApplication : Application() {
    var settingsViewModel: SettingsViewModel? = null
        private set

    override fun onCreate() {
        super.onCreate()
        settingsViewModel = SettingsViewModel(this)
    }
}