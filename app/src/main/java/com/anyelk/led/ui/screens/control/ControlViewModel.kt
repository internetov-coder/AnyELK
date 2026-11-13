package com.anyelk.led.ui.screens.control

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anyelk.led.ServiceLocator
import com.anyelk.led.data.ble.BleLedRepository
import com.anyelk.led.data.ble.LedConnectionState
import com.anyelk.led.data.ble.PixelOrder
import com.anyelk.led.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ControlUiState(
    val connected: Boolean = false,
    val connectionLabel: String = "",
    val power: Boolean = true,
    /** HSV hue in degrees, 0..360. */
    val hue: Float = 0f,
    /** HSV saturation, 0..1. */
    val saturation: Float = 1f,
    /** Brightness percent, 1..100. */
    val brightness: Int = 100,
    val color: Color = Color.Red,
    val pixelOrder: PixelOrder = PixelOrder.GRB
)

class ControlViewModel(
    private val ble: BleLedRepository = ServiceLocator.bleRepository,
    private val settings: SettingsRepository = ServiceLocator.settingsRepository
) : ViewModel() {

    private val powerFlow = MutableStateFlow(true)
    private val hueFlow = MutableStateFlow(0f)
    private val satFlow = MutableStateFlow(1f)
    private val brightnessFlow = MutableStateFlow(100)

    private val connectionAndSettings = combine(ble.connection, settings.settings) { c, s ->
        c to s
    }

    private val lightState = combine(powerFlow, hueFlow, satFlow, brightnessFlow) { p, h, s, b ->
        LightState(p, h, s, b)
    }

    val uiState: StateFlow<ControlUiState> = combine(connectionAndSettings, lightState) { cs, light ->
        val (connection, appSettings) = cs
        val connected = connection is LedConnectionState.Connected
        ControlUiState(
            connected = connected,
            connectionLabel = connectionLabel(connection),
            power = light.power,
            hue = light.hue,
            saturation = light.saturation,
            brightness = light.brightness,
            color = hsvColor(light.hue, light.saturation, light.brightness / 100f),
            pixelOrder = appSettings.pixelOrder
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControlUiState())

    private data class LightState(
        val power: Boolean,
        val hue: Float,
        val saturation: Float,
        val brightness: Int
    )

    init {
        // Push the current UI state whenever the strip (re)connects.
        viewModelScope.launch {
            ble.connection.collect { conn ->
                if (conn is LedConnectionState.Connected) {
                    pushPower()
                    pushBrightness()
                    pushColor()
                }
            }
        }
    }

    fun setHueSaturation(hue: Float, saturation: Float) {
        hueFlow.value = ((hue % 360f) + 360f) % 360f
        satFlow.value = saturation.coerceIn(0f, 1f)
        if (powerFlow.value) pushColor()
    }

    fun setPreset(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hueFlow.value = hsv[0]
        satFlow.value = hsv[1]
        if (powerFlow.value) pushColor()
    }

    fun setBrightness(percent: Int) {
        brightnessFlow.value = percent.coerceIn(1, 100)
        pushBrightness()
    }

    fun togglePower() {
        powerFlow.value = !powerFlow.value
        pushPower()
        if (powerFlow.value) pushColor()
    }

    private fun pushColor() {
        if (!ble.connection.value.isReady()) return
        val c = hsvColor(hueFlow.value, satFlow.value, brightnessFlow.value / 100f)
        ble.sendColor(
            red = (c.red * 255).roundToInt(),
            green = (c.green * 255).roundToInt(),
            blue = (c.blue * 255).roundToInt()
        )
    }

    private fun pushBrightness() {
        if (!ble.connection.value.isReady()) return
        val scaled = if (powerFlow.value) brightnessFlow.value else 0
        ble.sendControl(com.anyelk.led.data.ble.BleProtocol.brightness(scaled))
    }

    private fun pushPower() {
        if (!ble.connection.value.isReady()) return
        ble.sendControl(
            if (powerFlow.value) com.anyelk.led.data.ble.BleProtocol.powerOn()
            else com.anyelk.led.data.ble.BleProtocol.powerOff()
        )
    }

    private fun LedConnectionState.isReady(): Boolean = this is LedConnectionState.Connected

    private fun connectionLabel(state: LedConnectionState): String = when (state) {
        is LedConnectionState.Connected -> "connected"
        is LedConnectionState.Connecting -> "connecting"
        is LedConnectionState.Scanning -> "scanning"
        is LedConnectionState.Error -> "error"
        is LedConnectionState.Disconnected -> "disconnected"
        LedConnectionState.Idle -> "idle"
    }

    companion object {
        fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
            val hsv = floatArrayOf(hue, saturation, value.coerceIn(0f, 1f))
            val argb = android.graphics.Color.HSVToColor(hsv)
            return Color(argb)
        }
    }
}
