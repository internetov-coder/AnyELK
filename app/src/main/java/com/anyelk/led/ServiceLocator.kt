package com.anyelk.led

import android.content.Context
import com.anyelk.led.data.ble.BleLedRepository
import com.anyelk.led.data.music.AudioVisualizerManager
import com.anyelk.led.data.music.MusicController
import com.anyelk.led.data.music.MusicRepository
import com.anyelk.led.data.settings.SettingsRepository

object ServiceLocator {
    lateinit var appContext: Context
        private set

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val bleRepository: BleLedRepository by lazy { BleLedRepository(appContext) }
    val musicRepository: MusicRepository by lazy { MusicRepository(appContext) }
    val musicController: MusicController by lazy { MusicController(appContext) }
    val visualizerManager: AudioVisualizerManager by lazy { AudioVisualizerManager() }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
