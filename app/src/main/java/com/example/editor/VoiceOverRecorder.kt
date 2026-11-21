package com.example.editor

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VoiceOverState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val error: String? = null
)

@Singleton
class VoiceOverRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(VoiceOverState())
    val state: StateFlow<VoiceOverState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var timerJob: Job? = null
    private var startedAtMs: Long = 0L

    fun startRecording(): Boolean {
        if (_state.value.isRecording) return false
        return runCatching {
            val dir = File(context.filesDir, "voiceovers").apply { mkdirs() }
            val file = File(dir, "voiceover_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            startedAtMs = System.currentTimeMillis()
            _state.value = VoiceOverState(isRecording = true, elapsedMs = 0L, error = null)
            timerJob?.cancel()
            timerJob = scope.launch {
                while (isActive && _state.value.isRecording) {
                    _state.value = _state.value.copy(elapsedMs = System.currentTimeMillis() - startedAtMs)
                    delay(100L)
                }
            }
            true
        }.getOrElse { err ->
            cleanupRecorder()
            _state.value = VoiceOverState(
                isRecording = false,
                elapsedMs = 0L,
                error = err.localizedMessage ?: "Microphone unavailable"
            )
            false
        }
    }

    fun stopRecording(): Pair<String, Long>? {
        timerJob?.cancel()
        val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1_000L)
        val file = outputFile
        return runCatching {
            recorder?.stop()
            cleanupRecorder()
            _state.value = VoiceOverState(isRecording = false, elapsedMs = 0L)
            if (file != null && file.exists() && file.length() > 0) {
                Uri.fromFile(file).toString() to elapsed
            } else null
        }.getOrElse {
            cleanupRecorder()
            _state.value = VoiceOverState(isRecording = false, elapsedMs = 0L)
            null
        }
    }

    private fun cleanupRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }
}
