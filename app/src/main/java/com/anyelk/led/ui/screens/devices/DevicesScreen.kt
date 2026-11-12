package com.anyelk.led.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anyelk.led.R
import com.anyelk.led.data.ble.LedConnectionState
import com.anyelk.led.data.ble.ScannedDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val scanning = connection is LedConnectionState.Scanning

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.devices_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = { if (scanning) viewModel.stopScan() else viewModel.scan() }) {
                    Icon(
                        imageVector = if (scanning) Icons.Filled.Stop else Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            if (scanning) R.string.devices_stop_scan else R.string.devices_scan
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (scanning) viewModel.stopScan() else viewModel.scan() },
                icon = {
                    Icon(
                        imageVector = if (scanning) Icons.Filled.Stop else Icons.Filled.Refresh,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        stringResource(
                            if (scanning) R.string.devices_scanning else R.string.devices_scan
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            StatusCard(connection)

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty() && !scanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.devices_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(devices, key = { it.address }) { device ->
                        DeviceCard(
                            device = device,
                            connection = connection,
                            onConnect = { viewModel.connect(device) },
                            onDisconnect = viewModel::disconnect
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(connection: LedConnectionState) {
    val (text, container, content) = when (connection) {
        is LedConnectionState.Connected -> Triple(
            stringResource(R.string.devices_status_connected) +
                (connection.name?.let { " · $it" } ?: ""),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is LedConnectionState.Connecting -> Triple(
            stringResource(R.string.devices_status_connecting),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        is LedConnectionState.Scanning -> Triple(
            stringResource(R.string.devices_scanning),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        is LedConnectionState.Error -> Triple(
            connection.message,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        is LedConnectionState.Disconnected -> Triple(
            stringResource(R.string.devices_status_disconnected) +
                (connection.reason?.let { " · $it" } ?: ""),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        LedConnectionState.Idle -> Triple(
            stringResource(R.string.devices_status_disconnected),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (connection is LedConnectionState.Scanning ||
                connection is LedConnectionState.Connecting
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Icon(
                    imageVector = if (connection is LedConnectionState.Connected) {
                        Icons.Filled.Bluetooth
                    } else {
                        Icons.Filled.BluetoothDisabled
                    },
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: ScannedDevice,
    connection: LedConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = connection is LedConnectionState.Connected &&
        connection.address == device.address
    val isConnecting = connection is LedConnectionState.Connecting &&
        connection.address == device.address

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = { if (isConnected) onDisconnect() else onConnect() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (device.isLikelyLed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: stringResource(R.string.devices_unknown_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (device.isLikelyLed) FontWeight.SemiBold
                    else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(device.address)
                        append("  ·  ")
                        append(stringResource(R.string.devices_rssi, device.rssi))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isConnected || isConnecting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (isConnected) R.string.devices_status_connected
                            else R.string.devices_status_connecting
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = stringResource(
                    if (isConnected) R.string.devices_disconnect else R.string.devices_connect
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
