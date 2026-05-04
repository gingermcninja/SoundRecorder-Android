package com.example.soundrecorder_android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun RecordScreen(
    phase: RecordPhase,
    elapsedSeconds: Int,
    previewPositionMs: Int,
    isPreviewPlaying: Boolean,
    suggestedName: String,
    waveform: FloatArray?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTogglePreview: (trimStartMs: Int, trimEndMs: Int) -> Unit,
    onSeekPreview: (Int) -> Unit,
    onSave: (name: String, trimStartMs: Int, trimEndMs: Int) -> Unit,
    onDiscard: () -> Unit,
) {
    when (phase) {
        is RecordPhase.Idle, is RecordPhase.Recording -> RecordingSection(
            isRecording = phase is RecordPhase.Recording,
            elapsedSeconds = elapsedSeconds,
            onStart = onStart,
            onStop = onStop,
        )
        is RecordPhase.Reviewing -> ReviewSection(
            reviewing = phase,
            suggestedName = suggestedName,
            waveform = waveform,
            previewPositionMs = previewPositionMs,
            isPreviewPlaying = isPreviewPlaying,
            onTogglePreview = onTogglePreview,
            onSeekPreview = onSeekPreview,
            onSave = onSave,
            onDiscard = onDiscard,
        )
    }
}

// ── Recording phase ───────────────────────────────────────────────────────────

@Composable
private fun RecordingSection(
    isRecording: Boolean,
    elapsedSeconds: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onStart() }

    val timerText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Record",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = timerText,
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = {
                if (isRecording) {
                    onStop()
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) onStart() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color(0xFFCC1100) else Color(0xFFFF5C5C),
            ),
            modifier = Modifier.size(130.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = if (isRecording) "Stop" else "Start",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Review phase ──────────────────────────────────────────────────────────────

@Composable
private fun ReviewSection(
    reviewing: RecordPhase.Reviewing,
    suggestedName: String,
    waveform: FloatArray?,
    previewPositionMs: Int,
    isPreviewPlaying: Boolean,
    onTogglePreview: (trimStartMs: Int, trimEndMs: Int) -> Unit,
    onSeekPreview: (Int) -> Unit,
    onSave: (String, Int, Int) -> Unit,
    onDiscard: () -> Unit,
) {
    val durationMs = reviewing.durationMs

    var name by remember(reviewing.tempFile) { mutableStateOf(suggestedName) }
    var trimRange by remember(reviewing.tempFile) { mutableStateOf(0f..durationMs.toFloat()) }

    // Separate local drag state so the player timer doesn't fight the thumb.
    var isDraggingPreview by remember { mutableStateOf(false) }
    var localDragPosition by remember { mutableStateOf(0f) }
    val displayedPreviewPosition =
        if (isDraggingPreview) localDragPosition
        else previewPositionMs.toFloat().coerceIn(trimRange.start, trimRange.endInclusive)

    val accentBlue = Color(0xFF0A84FF)
    val labelGray = Color(0xFF8E8E93)
    val trackDark = Color(0xFF3A3A3C)

    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = accentBlue,
        inactiveTrackColor = trackDark,
    )
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = accentBlue,
        unfocusedBorderColor = labelGray,
        focusedLabelColor = accentBlue,
        unfocusedLabelColor = labelGray,
        cursorColor = Color.White,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 36.dp, bottom = 120.dp),
    ) {
        // ── Name ──────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
        )

        Spacer(Modifier.height(32.dp))

        // ── Trim ──────────────────────────────────────────────────────────────
        SectionLabel("TRIM", labelGray)
        Spacer(Modifier.height(8.dp))

        if (waveform != null) {
            WaveformTrimBar(
                amplitudes = waveform,
                trimStartFraction = trimRange.start / durationMs,
                trimEndFraction = trimRange.endInclusive / durationMs,
                playFraction = previewPositionMs.toFloat() / durationMs,
                onTrimStartChange = { f ->
                    trimRange = (f * durationMs)..(trimRange.endInclusive)
                },
                onTrimEndChange = { f ->
                    trimRange = trimRange.start..(f * durationMs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )
        } else {
            // Loading placeholder — shown briefly while decoding runs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xFF1C1C1E), RoundedCornerShape(8.dp)),
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatMs(trimRange.start.toInt()), color = labelGray, fontSize = 12.sp)
            val keptMs = (trimRange.endInclusive - trimRange.start).toInt()
            Text("${formatMs(keptMs)} kept", color = labelGray, fontSize = 12.sp)
            Text(formatMs(trimRange.endInclusive.toInt()), color = labelGray, fontSize = 12.sp)
        }

        Spacer(Modifier.height(32.dp))

        // ── Preview ───────────────────────────────────────────────────────────
        SectionLabel("PREVIEW", labelGray)
        Spacer(Modifier.height(4.dp))

        val trimStartMs = trimRange.start.toInt()
        val trimEndMs = trimRange.endInclusive.toInt()
        val posInTrim = (previewPositionMs - trimStartMs).coerceAtLeast(0)
        val trimDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(1)

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onTogglePreview(trimStartMs, trimEndMs) }) {
                Icon(
                    imageVector = if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPreviewPlaying) "Pause" else "Play",
                    tint = Color.White,
                )
            }
            Text(
                text = "${formatMs(posInTrim)} / ${formatMs(trimDurationMs)}",
                color = labelGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Slider(
            value = displayedPreviewPosition,
            onValueChange = {
                isDraggingPreview = true
                localDragPosition = it
            },
            onValueChangeFinished = {
                isDraggingPreview = false
                onSeekPreview(localDragPosition.toInt())
            },
            valueRange = trimRange.start..trimRange.endInclusive,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(40.dp))

        // ── Actions ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, labelGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text("Discard")
            }
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        trimRange.start.toInt(),
                        trimRange.endInclusive.toInt(),
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
            ) {
                Text("Save", color = Color.White)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
}

private fun formatMs(ms: Int): String {
    val m = ms / 60_000
    val s = (ms % 60_000) / 1000
    return "%d:%02d".format(m, s)
}
