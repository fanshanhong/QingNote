package com.fan.hwnote.app.view.block

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.audio.AudioPlayer
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.storage.NoteFileStorage
import com.fan.hwnote.app.view.list.DeleteConfirmBottomSheet

class AudioBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr), AudioPlayer.Listener {

    private val btnPlayPause: ImageView
    private val labelText: TextView
    private var data: Block.AudioBlock? = null

    /** Presenter 注入：用于解析本地 m4a 文件路径。 */
    var noteId: Long = 0L
    /** Presenter 注入：编辑器内共享 AudioPlayer。 */
    var audioPlayer: AudioPlayer? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.block_audio, this, true)
        btnPlayPause = findViewById(R.id.audio_play_pause)
        labelText = findViewById(R.id.audio_label)
        btnPlayPause.setOnClickListener { togglePlay() }
        setOnLongClickListener { showDeleteDialog(); true }
    }

    override fun bind(block: Block) {
        require(block is Block.AudioBlock) { "AudioBlockView only accepts AudioBlock" }
        check(noteId != 0L) { "AudioBlockView.bind() called before noteId was set" }
        data = block
        labelText.text = context.getString(R.string.audio_label_format, formatMmSs(block.durationMs))
        btnPlayPause.setImageResource(R.drawable.ic_play)
    }

    override fun toBlock(): Block = data
        ?: throw IllegalStateException("AudioBlockView.toBlock() called before bind()")

    override fun onDetachedFromWindow() {
        // 离开屏幕时若正播放，停掉（Presenter 删块或 Activity 销毁都会触发）
        val player = audioPlayer
        if (player != null && player.isPlaying(this)) {
            player.stop()
        }
        super.onDetachedFromWindow()
    }

    override fun onIdle(token: Any) {
        if (token === this) {
            btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun togglePlay() {
        val block = data ?: return
        val player = audioPlayer ?: return
        if (player.isPlaying(this)) {
            player.stop()
            return
        }
        val file = NoteFileStorage(context).audioFile(noteId, block.fileName)
        val ok = player.play(file, this, this)
        if (!ok) {
            Toast.makeText(context, R.string.audio_play_failed, Toast.LENGTH_SHORT).show()
            return
        }
        btnPlayPause.setImageResource(R.drawable.ic_pause)
    }

    private fun showDeleteDialog() {
        DeleteConfirmBottomSheet(
            context,
            title = context.getString(R.string.audio_delete_title),
            message = context.getString(R.string.audio_delete_message),
            confirmLabel = context.getString(R.string.action_delete),
            onConfirm = { callback?.onRequestDelete(this) },
        ).show()
    }

    private fun formatMmSs(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
