package com.anyelk.led.ui.screens.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anyelk.led.ServiceLocator
import com.anyelk.led.data.ble.BleLedRepository
import com.anyelk.led.data.ble.LedConnectionState
import com.anyelk.led.data.ble.ScannedDevice
import com.anyelk.led.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val ble: BleLedRepository = ServiceLocator.bleRepository,
    private val settings: SettingsRepository = ServiceLocator.settingsRepository
) : ViewModel() {

    val devices: StateFlow<List<ScannedDevice>> = ble.devices
    val connection: StateFlow<LedConnectionState> = ble.connection

    fun scan() = ble.startScan()

    fun stopScan() = ble.stopScan()

    fun connect(device: ScannedDevice) {
        ble.connect(device.address, device.name)
        viewModelScope.launch {
            settings.setLastDevice(device.address, device.name)
        }
    }

    fun disconnect() = ble.disconnect()

    fun forgetLastDevice() {
        viewModelScope.launch { settings.setLastDevice(null, null) }
    }
}
