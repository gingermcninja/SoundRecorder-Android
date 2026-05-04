package com.example.soundrecorder_android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private enum class TrimHandle { NONE, START, END }

/**
 * Draws a waveform with draggable trim handles and a playhead indicator.
 *
 * [trimStartFraction] and [trimEndFraction] are in 0..1.
 * [playFraction] is in 0..1 (playhead position).
 */
@Composable
fun WaveformTrimBar(
    amplitudes: FloatArray,
    trimStartFraction: Float,
    trimEndFraction: Float,
    playFraction: Float,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberUpdatedState lets the pointerInput lambda (key = Unit, long-lived)
    // always read the latest trim positions without restarting the gesture detector.
    val startState = rememberUpdatedState(trimStartFraction)
    val endState = rememberUpdatedState(trimEndFraction)
    val cbStart = rememberUpdatedState(onTrimStartChange)
    val cbEnd = rememberUpdatedState(onTrimEndChange)
    val activeHandle = remember { mutableStateOf(TrimHandle.NONE) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        val threshold = 48f / size.width // generous ~48px hit zone
                        val dStart = abs(f - startState.value)
                        val dEnd = abs(f - endState.value)
                        activeHandle.value = when {
                            dStart < threshold && dStart <= dEnd -> TrimHandle.START
                            dEnd < threshold -> TrimHandle.END
                            else -> TrimHandle.NONE
                        }
                    },
                    onDragEnd = { activeHandle.value = TrimHandle.NONE },
                    onDragCancel = { activeHandle.value = TrimHandle.NONE },
                    onDrag = { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        when (activeHandle.value) {
                            TrimHandle.START ->
                                cbStart.value(f.coerceAtMost(endState.value - 0.01f))
                            TrimHandle.END ->
                                cbEnd.value(f.coerceAtLeast(startState.value + 0.01f))
                            TrimHandle.NONE -> {}
                        }
                        change.consume()
                    },
                )
            },
    ) {
        val n = amplitudes.size.coerceAtLeast(1)
        val barPitch = size.width / n
        val barStroke = (barPitch * 0.65f).coerceAtLeast(1f)
        val cy = size.height / 2f
        val maxH = size.height * 0.85f

        // ── Waveform bars ─────────────────────────────────────────────────────
        amplitudes.forEachIndexed { i, amp ->
            val x = i * barPitch + barPitch / 2f
            val frac = (i + 0.5f) / n
            val inRange = frac in trimStartFraction..trimEndFraction
            val h = (amp * maxH).coerceAtLeast(2f)
            drawLine(
                color = if (inRange) Color.White else Color(0xFF3A3A3C),
                start = Offset(x, cy - h / 2f),
                end = Offset(x, cy + h / 2f),
                strokeWidth = barStroke,
            )
        }

        // ── Trim handles ──────────────────────────────────────────────────────
        val lineW = 2.dp.toPx()
        val capW = 12.dp.toPx()
        val capH = 20.dp.toPx()
        val capR = CornerRadius(3.dp.toPx())

        listOf(trimStartFraction, trimEndFraction).forEach { frac ->
            val x = (frac * size.width).coerceIn(0f, size.width)
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), lineW)
            // top cap
            drawRoundRect(Color.White, Offset(x - capW / 2f, 0f), Size(capW, capH), capR)
            // bottom cap
            drawRoundRect(
                Color.White,
                Offset(x - capW / 2f, size.height - capH),
                Size(capW, capH),
                capR,
            )
        }

        // ── Playhead ──────────────────────────────────────────────────────────
        val px = (playFraction.coerceIn(0f, 1f) * size.width)
        drawLine(
            color = Color(0xFF0A84FF),
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}
