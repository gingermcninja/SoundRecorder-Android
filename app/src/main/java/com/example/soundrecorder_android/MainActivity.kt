package com.example.soundrecorder_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.soundrecorder_android.ui.theme.SoundRecorderAndroidTheme

enum class Tab(val label: String, val icon: ImageVector) {
    PLAYBACK("Playback", Icons.Filled.PlayArrow),
    LIST("List", Icons.AutoMirrored.Filled.FormatListBulleted),
    RECORD("Record", Icons.Filled.Mic),
}

class MainActivity : ComponentActivity() {
    private val viewModel: RecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoundRecorderAndroidTheme(dynamicColor = false) {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: RecorderViewModel) {
    var selectedTab by remember { mutableStateOf(Tab.RECORD) }
    val isDark = selectedTab != Tab.LIST

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color(0xFFF2F2F7)),
    ) {
        when (selectedTab) {
            Tab.RECORD -> RecordScreen(
                isRecording = viewModel.isRecording,
                elapsedSeconds = viewModel.elapsedSeconds,
                onStart = viewModel::startRecording,
                onStop = viewModel::stopRecording,
            )
            Tab.LIST -> ListScreen(
                recordings = viewModel.recordings,
                currentlyPlayingId = viewModel.currentlyPlayingId,
                onTogglePlayback = viewModel::togglePlayback,
                onDeleteRecording = viewModel::deleteRecording,
            )
            Tab.PLAYBACK -> PlaybackScreen(
                playbackButtons = viewModel.playbackButtons,
                recordings = viewModel.recordings,
                currentlyPlayingId = viewModel.currentlyPlayingId,
                onButtonPressed = viewModel::pressPlaybackButton,
                onAddButton = viewModel::addPlaybackButton,
                onRemoveButton = viewModel::removePlaybackButton,
                onRenameButton = viewModel::renamePlaybackButton,
                onReassignButton = viewModel::reassignPlaybackButton,
            )
        }

        BottomNavBar(
            selectedTab = selectedTab,
            isDark = isDark,
            onTabSelected = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun BottomNavBar(
    selectedTab: Tab,
    isDark: Boolean,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barBg = if (isDark) Color(0xFF2C2C2E) else Color.White
    val defaultColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val activeColor = Color(0xFF0A84FF)
    val activePillBg = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    val pillShape = RoundedCornerShape(50.dp)

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth()
            .background(barBg, pillShape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in Tab.entries) {
            val isSelected = tab == selectedTab
            val itemColor = if (isSelected) activeColor else defaultColor

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .clickable { onTabSelected(tab) }
                    .background(if (isSelected) activePillBg else Color.Transparent)
                    .padding(vertical = 10.dp),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = itemColor,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = tab.label,
                    color = itemColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
