package com.anyelk.led.ui.screens.control.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 style HSV color wheel: hue around the circle, saturation from
 * the white center to the vivid rim. A knob follows the finger; the selected
 * color is previewed in the middle.
 */
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    onHueSaturationChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val latestHue by rememberUpdatedState(hue)
    val latestSat by rememberUpdatedState(saturation)
    val latestCallback by rememberUpdatedState(onHueSaturationChange)

    var dragOffset by remember { mutableStateOf<Offset?>(null) }

    fun updateFromOffset(offset: Offset, radius: Float, center: Offset) {
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val dist = hypot(dx, dy)
        if (dist < radius * 0.05f) return // ignore dead center
        val newHue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()) + 360f) % 360f
        val newSat = (dist / radius).coerceIn(0f, 1f)
        latestCallback(newHue, newSat)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val r = min(this.size.width, this.size.height) / 2f
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        updateFromOffset(offset, r, c)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragOffset = offset
                            val r = min(this.size.width, this.size.height) / 2f
                            val c = Offset(this.size.width / 2f, this.size.height / 2f)
                            updateFromOffset(offset, r, c)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val r = min(this.size.width, this.size.height) / 2f
                            val c = Offset(this.size.width / 2f, this.size.height / 2f)
                            val prev = dragOffset ?: Offset(
                                c.x + cos(Math.toRadians(latestHue.toDouble())).toFloat() *
                                    latestSat * r,
                                c.y + sin(Math.toRadians(latestHue.toDouble())).toFloat() *
                                    latestSat * r
                            )
                            val next = Offset(prev.x + amount.x, prev.y + amount.y)
                            dragOffset = next
                            updateFromOffset(next, r, c)
                        },
                        onDragEnd = { dragOffset = null },
                        onDragCancel = { dragOffset = null }
                    )
                }
        ) {
            val radius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Hue ring (HSV hue 0..360).
            val hueBrush = Brush.sweepGradient(
                colors = (0..12).map { hsvColor(it * 30f, 1f, 1f) },
                center = center
            )
            drawCircle(brush = hueBrush, radius = radius, center = center)

            // Saturation: white in the middle, pure hue at the rim.
            val satBrush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = radius
            )
            drawCircle(brush = satBrush, radius = radius, center = center)

            // Subtle outline.
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Knob.
            val angleRad = Math.toRadians(hue.toDouble())
            val knobRadius = saturation * radius
            val knobCenter = Offset(
                center.x + (cos(angleRad) * knobRadius).toFloat(),
                center.y + (sin(angleRad) * knobRadius).toFloat()
            )
            val knobOuter = 16.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = knobOuter,
                center = knobCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = knobOuter + 3.dp.toPx(),
                center = knobCenter,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = hsvColor(hue, saturation, 1f),
                radius = knobOuter - 5.dp.toPx(),
                center = knobCenter
            )

            // Center preview of the selected color.
            val previewRadius = radius * 0.17f
            drawCircle(
                color = Color.White,
                radius = previewRadius + 4.dp.toPx(),
                center = center
            )
            drawCircle(
                color = hsvColor(hue, saturation, 1f),
                radius = previewRadius,
                center = center
            )
        }
    }
}

fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val argb = android.graphics.Color.HSVToColor(
        floatArrayOf(
            ((hue % 360f) + 360f) % 360f,
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f)
        )
    )
    return Color(argb)
}
