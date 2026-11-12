package com.anyelk.led.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anyelk.led.ServiceLocator
import com.anyelk.led.data.ble.BleLedRepository
import com.anyelk.led.data.ble.PixelOrder
import com.anyelk.led.data.settings.AppSettings
import com.anyelk.led.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository = ServiceLocator.settingsRepository,
    private val ble: BleLedRepository = ServiceLocator.bleRepository
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoConnect(enabled) }
    }

    fun setPixelOrder(order: PixelOrder) {
        viewModelScope.launch {
            settings.setPixelOrder(order)
            ble.sendPixelOrder(order)
        }
    }
}
