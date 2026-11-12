package com.anyelk.led

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.anyelk.led.ui.navigation.AnyELKNavHost
import com.anyelk.led.ui.theme.AnyELKTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) tryAutoConnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnyELKTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnyELKNavHost()
                }
            }
        }
        ensurePermissions()
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.BLUETOOTH
            perms += Manifest.permission.BLUETOOTH_ADMIN
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return perms.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    private fun ensurePermissions() {
        val missing = requiredPermissions()
        if (missing.isEmpty()) {
            tryAutoConnect()
        } else {
            permissionLauncher.launch(missing)
        }
    }

    private fun tryAutoConnect() {
        val settings = ServiceLocator.settingsRepository
        val ble = ServiceLocator.bleRepository
        lifecycleScope.launch {
            val s = settings.settings.first()
            if (s.autoConnect) {
                ble.connectLast(s.lastDeviceAddress, s.lastDeviceName)
            }
        }
    }
}
