package com.fan.hwnote.app.view.editor

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.audio.AudioRecorder
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

/**
 * 录音底部 Sheet。
 * - 拥有 AudioRecorder 的生命周期：show() 时立即 start；stop 按钮触发 stop + 回传时长；
 *   取消按钮 / 关闭 / Activity onPause 都视为取消。
 * - 不在 Sheet 内决定目标文件路径，调用方传入。
 * - 取消时已写入的 m4a 由 AudioRecorder.cancel() 删除；停止时调用方在 onComplete 内决定怎么用文件。
 */
class AudioRecordingBottomSheet(
    context: Context,
    private val targetFile: File,
    private val onComplete: (durationMs: Long) -> Unit,
    private val onCancel: () -> Unit,
) : BottomSheetDialog(context) {

    private val recorder = AudioRecorder(context)
    private val handler = Handler(Looper.getMainLooper())
    private var startElapsed: Long = 0L
    private var terminated: Boolean = false
    private lateinit var timerText: TextView

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!recorder.isRecording) return
            val ms = android.os.SystemClock.elapsedRealtime() - startElapsed
            timerText.text = formatMmSs(ms)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_audio_recording, null)
        setContentView(view)
        // 录音过程中禁止外部点击 / 后退收起 = 取消（不收起，让用户必须点取消按钮明确决策）
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        timerText = view.findViewById(R.id.audio_timer)
        val btnStop = view.findViewById<TextView>(R.id.btn_audio_stop)
        val btnCancel = view.findViewById<TextView>(R.id.btn_audio_cancel)

        try {
            recorder.start(targetFile)
            startElapsed = android.os.SystemClock.elapsedRealtime()
            handler.post(tickRunnable)
        } catch (_: AudioRecorder.StartFailed) {
            terminated = true
            android.widget.Toast.makeText(
                context, R.string.audio_record_failed, android.widget.Toast.LENGTH_SHORT,
            ).show()
            dismiss()
            onCancel()
            return
        }

        btnStop.setOnClickListener {
            if (terminated) return@setOnClickListener
            terminated = true
            handler.removeCallbacks(tickRunnable)
            val duration = try {
                recorder.stop()
            } catch (_: AudioRecorder.StopFailed) {
                runCatching { targetFile.delete() }
                android.widget.Toast.makeText(
                    context, R.string.audio_record_failed, android.widget.Toast.LENGTH_SHORT,
                ).show()
                dismiss()
                onCancel()
                return@setOnClickListener
            }
            dismiss()
            onComplete(duration)
        }
        btnCancel.setOnClickListener {
            if (terminated) return@setOnClickListener
            terminated = true
            handler.removeCallbacks(tickRunnable)
            recorder.cancel()
            dismiss()
            onCancel()
        }
    }

    /** Activity onPause 直接调：兜底取消（设备息屏 / 切后台 / 进入其它 Activity）。 */
    fun forceCancel() {
        if (terminated) return
        terminated = true
        handler.removeCallbacks(tickRunnable)
        recorder.cancel()
        runCatching { dismiss() }
        onCancel()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tickRunnable)
    }

    private fun formatMmSs(ms: Long): String {
        val total = ms / 1000
        val mm = total / 60
        val ss = total % 60
        return "%02d:%02d".format(mm, ss)
    }
}
