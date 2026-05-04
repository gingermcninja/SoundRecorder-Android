package com.example.soundrecorder_android

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null
    private var timerJob: Job? = null

    val recordings = mutableStateListOf<Recording>()
    val playbackButtons = mutableStateListOf<PlaybackButton>()

    var isRecording by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableIntStateOf(0)
        private set
    var currentlyPlayingId by mutableStateOf<Long?>(null)
        private set

    // ── Recording ────────────────────────────────────────────────────────────

    fun startRecording() {
        stopPlayback()
        val file = File(context.filesDir, "rec_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            currentFile = file
            mediaRecorder = recorder
            isRecording = true
            elapsedSeconds = 0
            timerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    elapsedSeconds++
                }
            }
        } catch (e: Exception) {
            recorder.release()
            file.delete()
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        timerJob = null

        val recorder = mediaRecorder ?: return
        mediaRecorder = null

        val duration = elapsedSeconds
        isRecording = false
        elapsedSeconds = 0

        val file = currentFile
        currentFile = null

        try {
            recorder.stop()
        } catch (_: Exception) {
            recorder.release()
            file?.delete()
            return
        }
        recorder.release()

        if (file != null && file.exists() && file.length() > 0) {
            recordings.add(
                Recording(
                    id = System.currentTimeMillis(),
                    name = "Recording ${recordings.size + 1}",
                    filePath = file.absolutePath,
                    durationSeconds = duration,
                )
            )
        }
    }

    // ── Playback ─────────────────────────────────────────────────────────────

    fun addPlaybackButton(recording: Recording) {
        playbackButtons.add(
            PlaybackButton(
                id = System.currentTimeMillis(),
                recordingId = recording.id,
                label = recording.name,
            )
        )
    }

    fun removePlaybackButton(button: PlaybackButton) {
        val index = playbackButtons.indexOfFirst { it.id == button.id }
        if (index >= 0) playbackButtons.removeAt(index)
    }

    fun renamePlaybackButton(button: PlaybackButton, newLabel: String) {
        val index = playbackButtons.indexOfFirst { it.id == button.id }
        if (index >= 0) playbackButtons[index] = button.copy(label = newLabel)
    }

    fun reassignPlaybackButton(button: PlaybackButton, recording: Recording) {
        val index = playbackButtons.indexOfFirst { it.id == button.id }
        if (index >= 0) playbackButtons[index] = button.copy(
            recordingId = recording.id,
            label = recording.name,
        )
    }

    fun pressPlaybackButton(button: PlaybackButton) {
        val recording = recordings.find { it.id == button.recordingId } ?: return
        togglePlayback(recording)
    }

    fun togglePlayback(recording: Recording) {
        if (currentlyPlayingId == recording.id) {
            stopPlayback()
        } else {
            playRecording(recording)
        }
    }

    private fun playRecording(recording: Recording) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                start()
                setOnCompletionListener {
                    currentlyPlayingId = null
                    it.release()
                    if (mediaPlayer == it) mediaPlayer = null
                }
            }
            currentlyPlayingId = recording.id
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        mediaPlayer = null
        currentlyPlayingId = null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        mediaRecorder?.release()
        mediaRecorder = null
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
