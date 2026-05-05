package com.example.soundrecorder_android

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ListScreen(
    recordings: List<Recording>,
    currentlyPlayingId: Long?,
    onTogglePlayback: (Recording) -> Unit,
    onDeleteRecording: (Recording) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 120.dp),
    ) {
        Text(
            text = "Recordings",
            //color = Color(0xFF1C1C1E),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "No recordings yet",
                    color = Color(0xFF8E8E93),
                    fontSize = 16.sp,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings, key = { it.id }) { recording ->
                    RecordingItem(
                        recording = recording,
                        isPlaying = recording.id == currentlyPlayingId,
                        onClick = { onTogglePlayback(recording) },
                        onDelete = { onDeleteRecording(recording) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItem(
    recording: Recording,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = recording.name,
                color = Color(0xFF1C1C1E),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%d:%02d".format(recording.durationSeconds / 60, recording.durationSeconds % 60),
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                tint = if (isPlaying) Color(0xFF0A84FF) else Color(0xFF8E8E93),
                modifier = Modifier.size(22.dp),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Delete", color = Color(0xFFFF3B30)) },
                onClick = { showMenu = false; showConfirm = true },
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete \"${recording.name}\"?") },
            text = { Text("This will permanently delete the recording and remove any linked playback buttons.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) {
                    Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
