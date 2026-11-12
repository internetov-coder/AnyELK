package com.anyelk.led

import android.app.Application

class AnyElkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
