package com.fan.hwnote.app.model.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * 录音状态机：IDLE → RECORDING → IDLE。
 * 单实例使用：同一时刻一个 AudioRecorder 只录一段音频。
 * stop() 返回时长 ms；cancel() 释放但不返时长（用于 BottomSheet 取消）。
 */
class AudioRecorder(private val context: Context) {

    class StartFailed(cause: Throwable) : RuntimeException(cause)
    class StopFailed(cause: Throwable) : RuntimeException(cause)

    private var recorder: MediaRecorder? = null
    private var startElapsed: Long = 0L
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(target: File) {
        if (recorder != null) return
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Throwable) {
            runCatching { r.release() }
            throw StartFailed(e)
        }
        recorder = r
        outputFile = target
        startElapsed = SystemClock.elapsedRealtime()
    }

    /** 返回时长 ms（>= 0）。停止失败抛 StopFailed（文件可能不可用，调用方应删除 outputFile）。 */
    fun stop(): Long {
        val r = recorder ?: return 0L
        recorder = null
        val durationMs = SystemClock.elapsedRealtime() - startElapsed
        try {
            r.stop()
        } catch (e: Throwable) {
            runCatching { r.release() }
            throw StopFailed(e)
        }
        r.release()
        return durationMs.coerceAtLeast(0L)
    }

    /** 取消：释放 recorder + 删文件，不抛。供 BottomSheet 取消按钮调。 */
    fun cancel() {
        val r = recorder ?: return
        recorder = null
        val file = outputFile
        outputFile = null
        runCatching { r.stop() }
        runCatching { r.release() }
        runCatching { file?.delete() }
    }
}
