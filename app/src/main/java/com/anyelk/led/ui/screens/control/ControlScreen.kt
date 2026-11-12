package com.anyelk.led.ui.screens.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anyelk.led.R
import com.anyelk.led.ui.screens.control.components.ColorWheel
import com.anyelk.led.ui.theme.presetSwatches
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    viewModel: ControlViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.control_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                ConnectionBadge(connected = state.connected, label = state.connectionLabel)
            }
            IconButton(
                onClick = viewModel::togglePower,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.power && state.connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .semantics {
                        contentDescription = "Power"
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = stringResource(R.string.control_power),
                    tint = if (state.power && state.connected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            ColorWheel(
                hue = state.hue,
                saturation = state.saturation,
                onHueSaturationChange = viewModel::setHueSaturation,
                modifier = Modifier
                    .size(280.dp)
                    .semantics { contentDescription = "Color wheel" }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(state.color)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = String.format(
                    Locale.US,
                    "#%02X%02X%02X",
                    (state.color.red * 255).toInt(),
                    (state.color.green * 255).toInt(),
                    (state.color.blue * 255).toInt()
                ),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace
            )
        }

        if (!state.connected) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.control_not_connected),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.control_connect_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.control_brightness),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${state.brightness}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = state.brightness.toFloat(),
            onValueChange = { viewModel.setBrightness(it.toInt()) },
            valueRange = 1f..100f,
            steps = 98,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.control_presets),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.control_current_color),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        presetSwatches.chunked(6).forEach { rowColors ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowColors.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (sameColor(swatch, state.color)) 3.dp else 1.dp,
                                color = if (sameColor(swatch, state.color))
                                    MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setPreset(swatch) }
                            .semantics {
                                contentDescription = "Preset ${swatch.toHexString()}"
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live preview strip of the color currently sent to the controller.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (state.connected && state.power) state.color
            else MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.connected) {
                        String.format(
                            Locale.US,
                            "R %3d   G %3d   B %3d",
                            (state.color.red * 255).toInt(),
                            (state.color.green * 255).toInt(),
                            (state.color.blue * 255).toInt()
                        )
                    } else {
                        stringResource(R.string.control_not_connected)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (state.connected && state.power &&
                        luminance(state.color) > 0.5f
                    ) Color.Black else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ConnectionBadge(connected: Boolean, label: String) {
    val color = if (connected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                connected -> stringResource(R.string.devices_status_connected)
                label == "connecting" -> stringResource(R.string.devices_status_connecting)
                label == "error" -> stringResource(R.string.devices_status_error)
                else -> stringResource(R.string.devices_status_disconnected)
            },
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

private fun sameColor(a: Color, b: Color): Boolean =
    Math.abs(a.red - b.red) < 0.02f &&
        Math.abs(a.green - b.green) < 0.02f &&
        Math.abs(a.blue - b.blue) < 0.02f

private fun luminance(c: Color): Float =
    0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

private fun Color.toHexString(): String = String.format(
    Locale.US, "%02X%02X%02X",
    (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
