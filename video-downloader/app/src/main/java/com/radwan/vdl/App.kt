package com.radwan.vdl

import android.app.Application

class App : Application() {
    @Volatile var engineError: String? = null

    override fun onCreate() {
        super.onCreate()
        try {
            Engine.init(this)
        } catch (e: Exception) {
            engineError = e.message
        }
    }
}
