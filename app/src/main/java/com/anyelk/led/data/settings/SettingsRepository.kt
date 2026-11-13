package com.anyelk.led.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anyelk.led.data.ble.PixelOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "anyelk_settings")

data class AppSettings(
    val autoConnect: Boolean = true,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
    val pixelOrder: PixelOrder = PixelOrder.GRB,
    val musicTreeUri: String? = null
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val LAST_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_NAME = stringPreferencesKey("last_device_name")
        val PIXEL_ORDER = stringPreferencesKey("pixel_order")
        val MUSIC_TREE = stringPreferencesKey("music_tree_uri")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: true,
            lastDeviceAddress = prefs[Keys.LAST_ADDRESS],
            lastDeviceName = prefs[Keys.LAST_NAME],
            pixelOrder = PixelOrder.fromName(prefs[Keys.PIXEL_ORDER]),
            musicTreeUri = prefs[Keys.MUSIC_TREE]
        )
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun setLastDevice(address: String?, name: String?) {
        context.dataStore.edit {
            if (address == null) {
                it.remove(Keys.LAST_ADDRESS)
                it.remove(Keys.LAST_NAME)
            } else {
                it[Keys.LAST_ADDRESS] = address
                if (name != null) it[Keys.LAST_NAME] = name
            }
        }
    }

    suspend fun setPixelOrder(order: PixelOrder) {
        context.dataStore.edit { it[Keys.PIXEL_ORDER] = order.name }
    }

    suspend fun setMusicTree(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.MUSIC_TREE) else it[Keys.MUSIC_TREE] = uri
        }
    }
}
