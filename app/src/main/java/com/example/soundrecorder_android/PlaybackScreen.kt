package com.example.soundrecorder_android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ButtonShape = RoundedCornerShape(16.dp)
private val ButtonHeight = 64.dp

@Composable
fun PlaybackScreen(
    playbackButtons: List<PlaybackButton>,
    recordings: List<Recording>,
    currentlyPlayingId: Long?,
    onButtonPressed: (PlaybackButton) -> Unit,
    onAddButton: (Recording) -> Unit,
    onRemoveButton: (PlaybackButton) -> Unit,
    onRenameButton: (PlaybackButton, String) -> Unit,
    onReassignButton: (PlaybackButton, Recording) -> Unit,
) {
    var showAddPicker by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(playbackButtons, key = { it.id }) { button ->
            val isPlaying = button.recordingId == currentlyPlayingId
            PlaybackButtonItem(
                button = button,
                isPlaying = isPlaying,
                recordings = recordings,
                onClick = { onButtonPressed(button) },
                onRename = { newLabel -> onRenameButton(button, newLabel) },
                onReassign = { recording -> onReassignButton(button, recording) },
                onRemove = { onRemoveButton(button) },
            )
        }

        item {
            OutlinedButton(
                onClick = { showAddPicker = true },
                shape = ButtonShape,
                border = BorderStroke(2.dp, Color(0xFF3A3A3C)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add button")
            }
        }
    }

    if (showAddPicker) {
        RecordingPickerDialog(
            title = "Add button for…",
            recordings = recordings,
            onSelect = { recording ->
                onAddButton(recording)
                showAddPicker = false
            },
            onDismiss = { showAddPicker = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaybackButtonItem(
    button: PlaybackButton,
    isPlaying: Boolean,
    recordings: List<Recording>,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onReassign: (Recording) -> Unit,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showReassignPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(ButtonShape)
            .background(if (isPlaying) Color(0xFFCC1100) else Color(0xFFFF5C5C))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = button.label,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = { showMenu = false; showRenameDialog = true },
            )
            DropdownMenuItem(
                text = { Text("Change recording") },
                onClick = { showMenu = false; showReassignPicker = true },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = { showMenu = false; onRemove() },
            )
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentLabel = button.label,
            onConfirm = { newLabel -> onRename(newLabel); showRenameDialog = false },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showReassignPicker) {
        RecordingPickerDialog(
            title = "Change recording…",
            recordings = recordings,
            onSelect = { recording -> onReassign(recording); showReassignPicker = false },
            onDismiss = { showReassignPicker = false },
        )
    }
}

@Composable
private fun RenameDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2C2C2E),
        title = { Text("Rename button", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF0A84FF),
                    unfocusedBorderColor = Color(0xFF8E8E93),
                    cursorColor = Color.White,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text("Save", color = Color(0xFF0A84FF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF0A84FF))
            }
        },
    )
}

@Composable
private fun RecordingPickerDialog(
    title: String,
    recordings: List<Recording>,
    onSelect: (Recording) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2C2C2E),
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            if (recordings.isEmpty()) {
                Text("No recordings yet. Record something first.", color = Color(0xFF8E8E93))
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    recordings.forEach { recording ->
                        TextButton(
                            onClick = { onSelect(recording) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(recording.name, color = Color.White, fontSize = 16.sp)
                                Text(
                                    text = "%d:%02d".format(
                                        recording.durationSeconds / 60,
                                        recording.durationSeconds % 60,
                                    ),
                                    color = Color(0xFF8E8E93),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF0A84FF))
            }
        },
    )
}
