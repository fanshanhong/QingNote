package com.fan.hwnote.app.model.audio

import android.media.MediaPlayer
import java.io.File

/**
 * 编辑器内共享播放器。同一时刻最多一个 token 在播；play 新 token 会自动停旧 token。
 * 对外只暴露 play / stop / 当前 token 查询；状态变化通过 Listener 回调。
 */
class AudioPlayer {

    interface Listener {
        /** 该 token 切回 idle（播放完成 / 被新播放打断 / 停止 / 失败）。在主线程触发。 */
        fun onIdle(token: Any)
    }

    private var player: MediaPlayer? = null
    private var currentToken: Any? = null
    private var currentListener: Listener? = null

    fun isPlaying(token: Any): Boolean = currentToken === token && player?.isPlaying == true

    /** 开始播放；返回 true 表示已启动播放（同步），false 表示失败（文件不存在 / 解码失败）。 */
    fun play(file: File, token: Any, listener: Listener): Boolean {
        stop()
        if (!file.exists()) return false
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                val t = currentToken; val l = currentListener
                releaseInternal()
                if (t != null && l != null) l.onIdle(t)
            }
            mp.setOnErrorListener { _, _, _ ->
                val t = currentToken; val l = currentListener
                releaseInternal()
                if (t != null && l != null) l.onIdle(t)
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            currentToken = token
            currentListener = listener
            true
        } catch (_: Throwable) {
            runCatching { mp.release() }
            false
        }
    }

    /** 主动停止；触发 listener.onIdle。 */
    fun stop() {
        val t = currentToken ?: return
        val l = currentListener
        releaseInternal()
        l?.onIdle(t)
    }

    private fun releaseInternal() {
        val mp = player
        player = null
        currentToken = null
        currentListener = null
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
    }
}
