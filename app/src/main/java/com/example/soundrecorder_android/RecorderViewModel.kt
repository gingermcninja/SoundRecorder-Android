package com.example.soundrecorder_android

import android.app.Application
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

sealed class RecordPhase {
    object Idle : RecordPhase()
    object Recording : RecordPhase()
    data class Reviewing(val tempFile: File, val durationMs: Int) : RecordPhase()
}

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // ── Observable state ──────────────────────────────────────────────────────

    val recordings = mutableStateListOf<Recording>()
    val playbackButtons = mutableStateListOf<PlaybackButton>()

    var phase by mutableStateOf<RecordPhase>(RecordPhase.Idle)
        private set
    var elapsedSeconds by mutableIntStateOf(0)
        private set

    var previewPositionMs by mutableIntStateOf(0)
        private set
    var isPreviewPlaying by mutableStateOf(false)
        private set

    var currentlyPlayingId by mutableStateOf<Long?>(null)
        private set
    var waveform by mutableStateOf<FloatArray?>(null)
        private set

    // ── Private fields ────────────────────────────────────────────────────────

    private var mediaRecorder: MediaRecorder? = null
    private var recordTimerJob: Job? = null
    private var currentFile: File? = null

    private var previewPlayer: MediaPlayer? = null
    private var previewTimerJob: Job? = null
    private var previewTrimStartMs = 0
    private var previewTrimEndMs = Int.MAX_VALUE

    private var listPlayer: MediaPlayer? = null

    // ── Recording ─────────────────────────────────────────────────────────────

    fun startRecording() {
        if (phase !is RecordPhase.Idle) return
        stopListPlayback()
        releasePreviewPlayer()

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
            phase = RecordPhase.Recording
            elapsedSeconds = 0
            recordTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    elapsedSeconds++
                }
            }
        } catch (_: Exception) {
            recorder.release()
            file.delete()
        }
    }

    fun stopRecording() {
        recordTimerJob?.cancel()
        recordTimerJob = null

        val recorder = mediaRecorder ?: return
        mediaRecorder = null

        val file = currentFile
        currentFile = null

        try {
            recorder.stop()
        } catch (_: Exception) {
            recorder.release()
            file?.delete()
            phase = RecordPhase.Idle
            elapsedSeconds = 0
            return
        }
        recorder.release()

        if (file == null || !file.exists() || file.length() == 0L) {
            phase = RecordPhase.Idle
            elapsedSeconds = 0
            return
        }

        val durationMs = MediaPlayer().let { mp ->
            try {
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                mp.duration.coerceAtLeast(1)
            } catch (_: Exception) {
                (elapsedSeconds * 1000).coerceAtLeast(1)
            } finally {
                mp.release()
            }
        }

        elapsedSeconds = 0
        previewPositionMs = 0
        waveform = null
        phase = RecordPhase.Reviewing(tempFile = file, durationMs = durationMs)

        val capturedFile = file
        val capturedDuration = durationMs
        viewModelScope.launch(Dispatchers.IO) {
            val amplitudes = decodeWaveform(capturedFile, capturedDuration, 200)
            withContext(Dispatchers.Main) { waveform = amplitudes }
        }
    }

    // ── Review: preview ───────────────────────────────────────────────────────

    fun togglePreview(trimStartMs: Int, trimEndMs: Int) {
        if (isPreviewPlaying) pausePreview() else startPreview(trimStartMs, trimEndMs)
    }

    fun seekPreview(ms: Int) {
        previewPositionMs = ms
        previewPlayer?.seekTo(ms)
    }

    private fun startPreview(trimStartMs: Int, trimEndMs: Int) {
        val reviewing = phase as? RecordPhase.Reviewing ?: return
        releasePreviewPlayer()
        previewTrimStartMs = trimStartMs
        previewTrimEndMs = trimEndMs
        try {
            previewPlayer = MediaPlayer().apply {
                setDataSource(reviewing.tempFile.absolutePath)
                prepare()
                seekTo(trimStartMs)
                start()
                // Natural file-end fallback (normally we stop at trimEndMs in the timer)
                setOnCompletionListener {
                    isPreviewPlaying = false
                    previewPositionMs = trimStartMs
                }
            }
            previewPositionMs = trimStartMs
            isPreviewPlaying = true
            previewTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(100)
                    val player = previewPlayer ?: break
                    val pos = player.currentPosition
                    if (pos >= trimEndMs) {
                        // Reached trim boundary — stop and rewind to trim start
                        try { player.release() } catch (_: Exception) {}
                        previewPlayer = null
                        isPreviewPlaying = false
                        previewPositionMs = trimStartMs
                        break
                    }
                    previewPositionMs = pos
                }
            }
        } catch (_: Exception) {
            previewPlayer?.release()
            previewPlayer = null
        }
    }

    private fun pausePreview() {
        previewTimerJob?.cancel()
        previewTimerJob = null
        previewPlayer?.pause()
        isPreviewPlaying = false
    }

    private fun releasePreviewPlayer() {
        previewTimerJob?.cancel()
        previewTimerJob = null
        previewPlayer?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        previewPlayer = null
        isPreviewPlaying = false
        previewPositionMs = 0
    }

    // ── Review: save / discard ────────────────────────────────────────────────

    fun saveRecording(name: String, trimStartMs: Int, trimEndMs: Int) {
        val reviewing = phase as? RecordPhase.Reviewing ?: return
        releasePreviewPlayer()
        waveform = null

        val nextIndex = recordings.size + 1
        val savedName = name.ifBlank { "Recording $nextIndex" }
        val needsTrim = trimStartMs > 0 || trimEndMs < reviewing.durationMs

        viewModelScope.launch(Dispatchers.IO) {
            val outputFile = File(context.filesDir, "saved_${System.currentTimeMillis()}.m4a")

            if (needsTrim) {
                trimAudio(
                    input = reviewing.tempFile,
                    output = outputFile,
                    startUs = trimStartMs * 1000L,
                    endUs = trimEndMs * 1000L,
                )
                reviewing.tempFile.delete()
            } else {
                reviewing.tempFile.renameTo(outputFile)
            }

            val savedDurationSec = ((trimEndMs - trimStartMs) / 1000).coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                recordings.add(
                    Recording(
                        id = System.currentTimeMillis(),
                        name = savedName,
                        filePath = outputFile.absolutePath,
                        durationSeconds = savedDurationSec,
                    )
                )
                phase = RecordPhase.Idle
            }
        }
    }

    fun discardRecording() {
        val reviewing = phase as? RecordPhase.Reviewing ?: return
        releasePreviewPlayer()
        reviewing.tempFile.delete()
        waveform = null
        phase = RecordPhase.Idle
    }

    private fun decodeWaveform(file: File, durationMs: Int, targetBars: Int): FloatArray {
        val peaks = FloatArray(targetBars)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)

            var audioTrack = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = i; trackFormat = fmt; break
                }
            }
            if (audioTrack < 0 || trackFormat == null) return FloatArray(targetBars) { 0.3f }

            extractor.selectTrack(audioTrack)

            val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            val totalMonoSamples = (sampleRate.toLong() * durationMs / 1000L).coerceAtLeast(1L)
            val samplesPerBar = totalMonoSamples.toFloat() / targetBars

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var isEOS = false
            var sampleOffset = 0L

            while (!isEOS) {
                val inIdx = decoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = decoder.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.order(ByteOrder.nativeOrder())
                        val shortBuf = outBuf.asShortBuffer()
                        val numShorts = shortBuf.remaining()
                        for (i in 0 until numShorts step channels) {
                            val monoIdx = sampleOffset + i / channels
                            val barIdx = (monoIdx / samplesPerBar).toInt().coerceIn(0, targetBars - 1)
                            val absVal = abs(shortBuf.get(i).toInt()).toFloat() / 32768f
                            if (absVal > peaks[barIdx]) peaks[barIdx] = absVal
                        }
                        sampleOffset += numShorts / channels
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } catch (_: Exception) {
            return FloatArray(targetBars) { 0.3f }
        } finally {
            extractor.release()
            try { decoder?.stop() } catch (_: Exception) {}
            decoder?.release()
        }

        val maxPeak = peaks.max().coerceAtLeast(0.01f)
        return FloatArray(targetBars) { peaks[it] / maxPeak }
    }

    private fun trimAudio(input: File, output: File, startUs: Long, endUs: Long) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)

        var audioTrack = -1
        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i
                trackFormat = fmt
                break
            }
        }

        if (audioTrack < 0 || trackFormat == null) {
            extractor.release()
            return
        }

        extractor.selectTrack(audioTrack)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerTrack = muxer.addTrack(trackFormat)
        muxer.start()

        val buffer = ByteBuffer.allocate(512 * 1024)
        val info = MediaCodec.BufferInfo()

        try {
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0 || extractor.sampleTime > endUs) break
                info.presentationTimeUs = extractor.sampleTime - startUs
                info.size = size
                info.flags = extractor.sampleFlags
                info.offset = 0
                muxer.writeSampleData(muxerTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            extractor.release()
            try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    // ── List playback ─────────────────────────────────────────────────────────

    fun togglePlayback(recording: Recording) {
        if (currentlyPlayingId == recording.id) stopListPlayback() else playListRecording(recording)
    }

    private fun playListRecording(recording: Recording) {
        stopListPlayback()
        try {
            listPlayer = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                start()
                setOnCompletionListener {
                    currentlyPlayingId = null
                    it.release()
                    if (listPlayer == it) listPlayer = null
                }
            }
            currentlyPlayingId = recording.id
        } catch (_: Exception) {}
    }

    fun stopListPlayback() {
        listPlayer?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        listPlayer = null
        currentlyPlayingId = null
    }

    // ── Playback buttons ──────────────────────────────────────────────────────

    fun deleteRecording(recording: Recording) {
        if (currentlyPlayingId == recording.id) stopListPlayback()
        val linked = playbackButtons.filter { it.recordingId == recording.id }
        linked.forEach { btn ->
            val idx = playbackButtons.indexOfFirst { it.id == btn.id }
            if (idx >= 0) playbackButtons.removeAt(idx)
        }
        recordings.remove(recording)
        File(recording.filePath).delete()
    }

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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        recordTimerJob?.cancel()
        mediaRecorder?.release()
        mediaRecorder = null
        releasePreviewPlayer()
        listPlayer?.release()
        listPlayer = null
    }
}
