# HwNote M10 语音录入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development（严格串行 implementer → spec reviewer → code quality reviewer → fix loop → 标记完成）。

**Goal:** 在编辑器中支持「录制音频 → 内嵌为 AudioBlock → 列表内播放 / 暂停 / 删除」，对齐华为 Note 的录音条交互；底层走 MediaRecorder(AAC) + MediaPlayer。

**Architecture:**
- 新增 `Block.AudioBlock(id, fileName, durationMs)`，NoteJson 增 `type="audio"` 分支。文件落 `filesDir/notes/<noteId>/audio/<uuid>.m4a`，复用 NoteFileStorage 模式（新增 `audioDir / audioFile`）。
- 录音入口：底部 4 键工具栏扩为 5 键（清单 / 样式 / 图片 / 手写 / 录音），weight=1 平铺。
- 录音 UX：点录音按钮 → RECORD_AUDIO 权限检查（沿 CAMERA 模式）→ `ensureNoteSavedAndThen` 拿到 noteId → 弹 `AudioRecordingBottomSheet`（实时计时 + 停止 + 取消）→ 停止后 Presenter 在焦点后插 AudioBlock 并补尾 TextBlock；取消则删本地 m4a。
- 播放：`AudioBlockView` 单例化共享 `AudioPlayer`（编辑器内同一时刻最多一块在播放，新点会停旧的）；播放完成自动回到「未播放」状态。
- 生命周期：录音 / 播放在 Activity onPause 自动停止（录音停止时自动落 block，播放停止时复位 UI）。

**Tech Stack:**
- `android.media.MediaRecorder`（`OutputFormat.MPEG_4` + `AudioEncoder.AAC` + `AudioSource.MIC`）
- `android.media.MediaPlayer`
- Android 12+ 用 `MediaRecorder(context)` 构造器，向下用 `MediaRecorder()` no-arg + suppress deprecation（compileSdk=36，运行时分支 `Build.VERSION.SDK_INT >= 31`）
- `BottomSheetDialog`（Material；与 M9 DeleteConfirmBottomSheet / FilterPickerBottomSheet 同款）
- 权限：`android.permission.RECORD_AUDIO`（dangerous，运行时申请；min/target=24/36 均需）

**约束（来自用户）：**
- 录音时长无上限；UI 仅展示走表计时；退出编辑器 / 切后台自动停止
- 串行 subagent 执行；单测增量按需新增（仅 AudioBlock JSON 一组）；UI 走真机走查
- 不动 AGP/Kotlin/Gradle/SDK 版本；commit 中文动宾，无 Co-Authored-By；git add 指定文件名

---

## 文件清单

**新增（10）：**
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/audio/AudioRecorder.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/audio/AudioPlayer.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/editor/AudioRecordingBottomSheet.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/AudioBlockView.kt`
- `code/HuaWeiNote/app/src/main/res/layout/dialog_audio_recording.xml`
- `code/HuaWeiNote/app/src/main/res/layout/block_audio.xml`
- `code/HuaWeiNote/app/src/main/res/drawable/ic_mic.xml`
- `code/HuaWeiNote/app/src/main/res/drawable/ic_play.xml`
- `code/HuaWeiNote/app/src/main/res/drawable/ic_pause.xml`
- `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonAudioTest.kt`

**修改（8）：**
- `AndroidManifest.xml` — 增 `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
- `Block.kt` — 增 `data class AudioBlock(id, fileName, durationMs)`
- `NoteJson.kt` — `blockToJson` / `blockFromJson` 增 audio 分支
- `NoteFileStorage.kt` — 增 `audioDir(noteId)` / `audioFile(noteId, fileName)`
- `EditorPresenter.kt` — 增 `insertAudioBlockAtFocus(block)` + `bind` 中 `is Block.AudioBlock -> addAudioBlockView(b)` + `onRequestDelete` 中 AudioBlockView 分支清磁盘 m4a + 共享 `AudioPlayer` 实例（`audioPlayer: AudioPlayer` + `stopAllPlayback()` 给 Activity 调）
- `NoteEditorActivity.kt` — 增 `recordAudioPermissionLauncher` + `launchRecordAudioWithPermission()` + `showRecordAudioPermissionDialog()` + `onRecordClicked()` listener + `onPause` 中 `presenter.stopAllPlayback()`（录音停止由 BottomSheet 自身在 onPause 触发）
- `toolbar_text.xml` — 增第 5 个 ImageView `@id/btn_record`（weight=1）
- `TextToolbarView.kt` — 增 `btnRecord` lazy + `Listener.onRecordClicked()`
- `strings.xml` — 增 `tb_record_cd` / `record_audio_permission_dialog_title|message` / `audio_recording_title` / `audio_recording_stop` / `audio_recording_cancel` / `audio_record_failed` / `audio_play_failed` / `toast_record_audio_started`

---

## 任务拆解（13 任务）

### Task 1：Block.AudioBlock 实体 + NoteJson audio 分支 + 单元测试

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Block.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/json/NoteJson.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/NoteContent.kt`（`toPlainText` 跳过 AudioBlock，与 ImageBlock 同处理）
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonAudioTest.kt`

**步骤：**

- **Step 1.1：在 Block sealed 内新增 AudioBlock**

```kotlin
data class AudioBlock(
    override val id: String,
    var fileName: String,
    var durationMs: Long,
) : Block()
```

- **Step 1.2：NoteJson.blockToJson 增 audio 分支**

```kotlin
is Block.AudioBlock -> JSONObject().apply {
    put("type", "audio")
    put("id", b.id)
    put("fileName", b.fileName)
    put("durationMs", b.durationMs)
}
```

- **Step 1.3：NoteJson.blockFromJson 增 audio 分支**

```kotlin
"audio" -> Block.AudioBlock(
    id = o.optString("id"),
    fileName = o.optString("fileName"),
    durationMs = o.optLong("durationMs"),
)
```

- **Step 1.4：NoteContent.toPlainText 跳过 AudioBlock**

打开 `NoteContent.kt`，在 `toPlainText` 的 when 表达式中追加一行（与 ImageBlock 同处理）：

```kotlin
is Block.ImageBlock -> Unit // 图片不进搜索
is Block.AudioBlock -> Unit // 音频不进搜索
```

并把顶部 KDoc 注释中 "- ImageBlock：忽略" 后追加 "- AudioBlock：忽略"。

- **Step 1.5：编写 NoteJsonAudioTest（2 case）**

```kotlin
package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonAudioTest {

    @Test
    fun `audio block round trips`() {
        val content = NoteContent(
            blocks = listOf(
                Block.AudioBlock(id = "a-001", fileName = "uuid-1.m4a", durationMs = 12345L),
            ),
            handwriting = emptyList(),
        )
        val json = NoteJson.toJson(content)
        val back = NoteJson.fromJson(json)
        val b = back.blocks.single() as Block.AudioBlock
        assertEquals("a-001", b.id)
        assertEquals("uuid-1.m4a", b.fileName)
        assertEquals(12345L, b.durationMs)
    }

    @Test
    fun `audio with missing duration defaults to 0`() {
        val raw = """{"blocks":[{"type":"audio","id":"a-002","fileName":"x.m4a"}],"handwriting":{"strokes":[]}}"""
        val content = NoteJson.fromJson(raw)
        val b = content.blocks.single() as Block.AudioBlock
        assertEquals(0L, b.durationMs)
    }
}
```

- **Step 1.6：跑测**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:test
```

预期：83 + 2 = 85 passed

- **Step 1.7：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Block.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/NoteContent.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/json/NoteJson.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonAudioTest.kt
git commit -m "feat(m10): 增 Block.AudioBlock 实体与 NoteJson audio 分支"
```

---

### Task 2：NoteFileStorage 增 audioDir / audioFile

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/storage/NoteFileStorage.kt`

**步骤：**

- **Step 2.1：增方法（仿 imageDir / imageFile）**

```kotlin
fun audioDir(noteId: Long): File {
    val dir = File(noteDir(noteId), "audio")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun audioFile(noteId: Long, fileName: String): File =
    File(audioDir(noteId), fileName)
```

- **Step 2.2：更新顶部目录注释**

把现有 doc 注释中的目录布局改为：
```
filesDir/notes/<noteId>/images/<uuid>.jpg
filesDir/notes/<noteId>/audio/<uuid>.m4a
```

- **Step 2.3：编译 + 跑测**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:test
```

预期：85 passed（无新增测试，但确保未破坏现有）

- **Step 2.4：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/storage/NoteFileStorage.kt
git commit -m "feat(m10): NoteFileStorage 增 audio 目录与文件解析"
```

---

### Task 3：AudioRecorder 包装类（MediaRecorder 封装）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/audio/AudioRecorder.kt`

**说明：**
- 单实例使用模式：start(targetFile) → ...运行中... → stop() 返回 durationMs；中途 cancel() 释放 + 删文件不返时长。
- 不在 Recorder 内做计时器（让 BottomSheet 自己驱动 UI 计时），只在 stop 时基于 `SystemClock.elapsedRealtime()` 算 startElapsed → end 差值。
- 失败暴露给上层：start 内部 try/catch IO/IllegalState，失败抛 `AudioRecorder.StartFailed`；stop 失败抛 `StopFailed`。

**步骤：**

- **Step 3.1：实现 AudioRecorder**

```kotlin
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
```

- **Step 3.2：编译**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- **Step 3.3：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/audio/AudioRecorder.kt
git commit -m "feat(m10): 新增 AudioRecorder（AAC/m4a 封装）"
```

---

### Task 4：AudioPlayer 共享播放器（编辑器内单实例）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/audio/AudioPlayer.kt`

**说明：**
- 编辑器内多个 AudioBlockView 共享同一个 AudioPlayer；点击播放一块时，先把其它块的 UI 切回 idle（通过 currentToken 比对 + listener.onIdle 回调）。
- 播放结束 / 失败 / stop 都触发 listener.onIdle，由 AudioBlockView 收回 UI 状态。

**步骤：**

- **Step 4.1：实现 AudioPlayer**

```kotlin
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
```

- **Step 4.2：编译**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- **Step 4.3：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/audio/AudioPlayer.kt
git commit -m "feat(m10): 新增 AudioPlayer（编辑器内共享单例）"
```

---

### Task 5：RECORD_AUDIO 权限声明 + 录音相关 strings + 录音/播放/暂停 drawable

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/AndroidManifest.xml`
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_mic.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_play.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_pause.xml`

**步骤：**

- **Step 5.1：AndroidManifest 增权限（紧跟 CAMERA 行下方）**

```xml
<!-- M10 录音 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- **Step 5.2：strings.xml 增条目（追加到末尾）**

```xml
<!-- M10 录音 -->
<string name="tb_record_cd">录音</string>
<string name="record_audio_permission_dialog_title">需要录音权限</string>
<string name="record_audio_permission_dialog_message">已拒绝录音权限。请在系统设置中开启「麦克风」权限后再次尝试。</string>
<string name="audio_recording_title">正在录音</string>
<string name="audio_recording_stop">停止</string>
<string name="audio_recording_cancel">取消</string>
<string name="audio_record_failed">录音启动失败</string>
<string name="audio_play_failed">音频播放失败</string>
<string name="audio_delete_title">删除录音</string>
<string name="audio_delete_message">将删除本条录音，操作不可撤销。</string>
```

- **Step 5.3：ic_mic.xml（Material 麦克风图标）**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"/>
</vector>
```

- **Step 5.4：ic_play.xml（Material 播放）**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M8,5v14l11,-7z"/>
</vector>
```

- **Step 5.5：ic_pause.xml（Material 暂停）**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M6,19h4V5L6,5v14zM14,5v14h4L18,5h-4z"/>
</vector>
```

- **Step 5.6：编译**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- **Step 5.7：commit**

```bash
git add code/HuaWeiNote/app/src/main/AndroidManifest.xml \
        code/HuaWeiNote/app/src/main/res/values/strings.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_mic.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_play.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_pause.xml
git commit -m "feat(m10): 声明 RECORD_AUDIO + 增录音相关 strings 与图标"
```

---

### Task 6：AudioRecordingBottomSheet（录音 UI：计时 + 停止 + 取消）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_audio_recording.xml`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/editor/AudioRecordingBottomSheet.kt`

**说明：**
- 收起手势 / 系统返回 = 取消（与点取消按钮同语义）。
- 录音过程中 cancelable = false 时手势会失效，仍保留系统返回；不在这里做特殊兜底（Activity onPause 一并停止）。
- 自驱计时：`Handler(Looper.getMainLooper()).postDelayed(this, 500)` 每 500ms 更新 mm:ss 文本。

**步骤：**

- **Step 6.1：dialog_audio_recording.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        android:text="@string/audio_recording_title"
        android:textColor="@color/text_primary"
        android:textSize="16sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/audio_timer"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="16dp"
        android:text="00:00"
        android:textColor="@color/text_primary"
        android:textSize="36sp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/btn_audio_cancel"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:text="@string/audio_recording_cancel"
            android:textColor="@color/text_primary"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/btn_audio_stop"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:text="@string/audio_recording_stop"
            android:textColor="@color/error"
            android:textSize="16sp"
            android:textStyle="bold" />
    </LinearLayout>
</LinearLayout>
```

- **Step 6.2：AudioRecordingBottomSheet.kt**

```kotlin
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
```

- **Step 6.3：编译**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- **Step 6.4：commit**

```bash
git add code/HuaWeiNote/app/src/main/res/layout/dialog_audio_recording.xml \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/editor/AudioRecordingBottomSheet.kt
git commit -m "feat(m10): 新增 AudioRecordingBottomSheet（计时 + 停止/取消）"
```

---

### Task 7：AudioBlockView（播放 / 暂停 / 时长 / 长按删除）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/res/layout/block_audio.xml`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/AudioBlockView.kt`

**说明：**
- 视觉：横排 [播放/暂停 ImageView] [中部 弹性 spacer + "录音 · mm:ss" TextView] ；高度 56dp；与 block_image 同样的 marginVertical=8dp 圆角背景（简化用浅灰 round rect）。
- 长按弹 DeleteConfirmBottomSheet（与 M9 NoteListActivity 同款）；onConfirm → callback?.onRequestDelete(this)（Presenter 会清磁盘 m4a）。
- AudioPlayer 由 Presenter 注入（同一编辑器内共享）；播放期间按钮换 ic_pause；onIdle 切回 ic_play。

**步骤：**

- **Step 7.1：block_audio.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginVertical="8dp"
    android:background="@drawable/bg_image_block"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:paddingVertical="12dp">

    <ImageView
        android:id="@+id/audio_play_pause"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/audio_recording_title"
        android:src="@drawable/ic_play" />

    <TextView
        android:id="@+id/audio_label"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_weight="1"
        android:text="录音 · 00:00"
        android:textColor="@color/text_primary"
        android:textSize="14sp" />
</LinearLayout>
```

> 备注：`bg_image_block` 是 M5 既有的浅灰圆角背景；复用即可。

- **Step 7.2：AudioBlockView.kt**

```kotlin
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
```

- **Step 7.3：strings.xml 增 audio_label_format**

```xml
<string name="audio_label_format">录音 · %1$s</string>
```

> 备注：把这条加进 Task 5 也行，但单独放在这里是因为 AudioBlockView 是唯一用户。改用 inline 拼接也可，但抽到 string 资源更便于以后改文案。

- **Step 7.4：编译**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- **Step 7.5：commit**

```bash
git add code/HuaWeiNote/app/src/main/res/layout/block_audio.xml \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/AudioBlockView.kt \
        code/HuaWeiNote/app/src/main/res/values/strings.xml
git commit -m "feat(m10): 新增 AudioBlockView（播放/暂停/长按删除）"
```

---

### Task 8：EditorPresenter 接通 AudioBlock（bind / 插入 / 删除 / 播放收口）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

**步骤：**

- **Step 8.1：增字段 + addAudioBlockView**

在 `noteId` 字段下方追加：

```kotlin
/** 编辑器内共享 AudioPlayer（多块共用，新点播放会停旧的）。 */
val audioPlayer = com.fan.hwnote.app.model.audio.AudioPlayer()
```

在 addChecklistBlockView 之后追加：

```kotlin
private fun addAudioBlockView(block: Block.AudioBlock, insertAt: Int = -1) {
    val v = com.fan.hwnote.app.view.block.AudioBlockView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        callback = this@EditorPresenter
        noteId = this@EditorPresenter.noteId
        audioPlayer = this@EditorPresenter.audioPlayer
        bind(block)
    }
    if (insertAt < 0 || insertAt >= currentBlocks.size) {
        container.addView(v); currentBlocks.add(v)
    } else {
        container.addView(v, insertAt); currentBlocks.add(insertAt, v)
    }
}
```

- **Step 8.2：bind(note) 中支持 AudioBlock**

在现有 `when (b)` 分支增 `is Block.AudioBlock -> addAudioBlockView(b)`

- **Step 8.3：onRequestDelete 增 AudioBlockView 分支清磁盘 m4a**

把现有 ImageBlockView 那段改写为：

```kotlin
if (view is ImageBlockView) {
    (view.toBlock() as? Block.ImageBlock)?.let { purgeImageOnDisk(it) }
} else if (view is com.fan.hwnote.app.view.block.AudioBlockView) {
    (view.toBlock() as? Block.AudioBlock)?.let { purgeAudioOnDisk(it) }
}
```

- **Step 8.4：增 purgeAudioOnDisk**

```kotlin
/** 删除某 audio 块对应的本地 m4a。noteId<=0 视为未落库，no-op。失败吞掉。 */
private fun purgeAudioOnDisk(block: Block.AudioBlock) {
    if (noteId <= 0L) return
    runCatching {
        NoteFileStorage(context).audioFile(noteId, block.fileName).delete()
    }
}
```

- **Step 8.5：增 insertAudioBlockAtFocus（仿 insertImageBlocksAtFocus，但只插 1 块）**

```kotlin
/**
 * M10 入口：把 1 个 AudioBlock 插到当前焦点 TextBlock 之后，并补尾 TextBlock 接管焦点。
 * 焦点未知 → 追加到列表末尾。
 */
fun insertAudioBlockAtFocus(block: Block.AudioBlock) {
    val anchor = focusedTextBlock
    val baseIdx = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                  else currentBlocks.size
    addAudioBlockView(block, insertAt = baseIdx)
    val tail = emptyTextBlock()
    addTextBlockView(tail, insertAt = baseIdx + 1)
    (currentBlocks[baseIdx + 1] as TextBlockView).focusEditEnd()
}

/** Activity onPause 调：停掉编辑器内任何在播的音频。 */
fun stopAllPlayback() {
    audioPlayer.stop()
}
```

- **Step 8.6：编译**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- **Step 8.7：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt
git commit -m "feat(m10): EditorPresenter 接通 AudioBlock（bind/插入/删除/播放收口）"
```

---

### Task 9：工具栏扩 5 键（清单 / 样式 / 图片 / 手写 / 录音）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/layout/toolbar_text.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt`

**步骤：**

- **Step 9.1：toolbar_text.xml 在 btn_handwriting 之后追加 btn_record**

```xml
<ImageView android:id="@+id/btn_record"
    android:layout_width="0dp" android:layout_height="match_parent"
    android:layout_weight="1"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:clickable="true"
    android:contentDescription="@string/tb_record_cd"
    android:focusable="true"
    android:padding="12dp"
    android:src="@drawable/ic_mic" />
```

- **Step 9.2：TextToolbarView.kt 增 btnRecord lazy + listener.onRecordClicked**

在 `btnHandwriting` lazy 后追加：

```kotlin
private val btnRecord by lazy { findViewById<ImageView>(R.id.btn_record) }
```

在 wireListeners 末尾追加：

```kotlin
btnRecord.setOnClickListener { listener?.onRecordClicked() }
```

在 Listener interface 末尾追加：

```kotlin
fun onRecordClicked()
```

- **Step 9.3：编译**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

预期：BUILD FAILED — NoteEditorActivity 的匿名 Listener 没实现 onRecordClicked。这是预期的，Task 10 修复。

- **Step 9.4：commit（允许编译失败，因为下一任务紧跟，串行执行不会留下断点）**

> ⚠️ 由于编译失败，本任务不单独 commit，把 9 + 10 合并 commit。跳过本步骤，进入 Task 10。

---

### Task 10：NoteEditorActivity 接通录音按钮（权限 + ensureNoteSavedAndThen + Sheet + 插入）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**步骤：**

- **Step 10.1：在 cameraPermissionLauncher 字段后追加 recordAudioPermissionLauncher**

```kotlin
private val recordAudioPermissionLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) startAudioRecording()
    else showRecordAudioPermissionDialog()
}
```

- **Step 10.2：在字段区追加 currentRecordingSheet（供 onPause 兜底取消）**

```kotlin
private var currentRecordingSheet: com.fan.hwnote.app.view.editor.AudioRecordingBottomSheet? = null
```

- **Step 10.3：TextToolbarView.Listener 匿名实现追加 onRecordClicked**

在 `override fun onHandwritingClicked()` 之后追加：

```kotlin
override fun onRecordClicked() {
    ensureNoteSavedAndThen { launchRecordAudioWithPermission() }
}
```

- **Step 10.4：增 launchRecordAudioWithPermission / startAudioRecording / showRecordAudioPermissionDialog（仿 camera 三件套）**

放在 launchCamera 方法之后：

```kotlin
private fun launchRecordAudioWithPermission() {
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        this, android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (granted) startAudioRecording()
    else recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
}

private fun showRecordAudioPermissionDialog() {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(R.string.record_audio_permission_dialog_title)
        .setMessage(R.string.record_audio_permission_dialog_message)
        .setPositiveButton(R.string.action_open_settings) { _, _ ->
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            runCatching { startActivity(intent) }
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

private fun startAudioRecording() {
    val curNoteId = loadedNote?.id ?: return
    if (curNoteId <= 0L) return
    val storage = com.fan.hwnote.app.model.storage.NoteFileStorage(this)
    val fileName = "${java.util.UUID.randomUUID()}.m4a"
    val target = storage.audioFile(curNoteId, fileName)
    val sheet = com.fan.hwnote.app.view.editor.AudioRecordingBottomSheet(
        context = this,
        targetFile = target,
        onComplete = { durationMs ->
            currentRecordingSheet = null
            val block = com.fan.hwnote.app.model.entity.Block.AudioBlock(
                id = "a-${java.util.UUID.randomUUID().toString().take(8)}",
                fileName = fileName,
                durationMs = durationMs,
            )
            presenter.insertAudioBlockAtFocus(block)
        },
        onCancel = {
            currentRecordingSheet = null
            // 取消时文件由 AudioRecorder.cancel() 已删除；这里仅清引用
        },
    )
    currentRecordingSheet = sheet
    sheet.show()
}
```

- **Step 10.5：onPause 增 stop 播放 + 兜底取消录音 Sheet**

把现有 `override fun onPause()` 改为：

```kotlin
override fun onPause() {
    super.onPause()
    // 停掉编辑器内任何在播的音频
    presenter.stopAllPlayback()
    // 兜底取消正在录的 Sheet（用户切后台/锁屏/跳别的 Activity）
    currentRecordingSheet?.forceCancel()
    currentRecordingSheet = null
    saveNote()
}
```

- **Step 10.6：完整构建 + 跑测**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test
```

预期：BUILD SUCCESSFUL + 85 passed

- **Step 10.7：commit（合并 Task 9 + 10 的 toolbar 和 Activity 改动）**

```bash
git add code/HuaWeiNote/app/src/main/res/layout/toolbar_text.xml \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "feat(m10): 工具栏扩 5 键并接通录音按钮入口"
```

---

### Task 11：NoteRepository 软删 / 彻删笔记时清掉音频目录（自动复用 deleteNoteDir）

**Files:**
- 查阅 `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt` 中 deletePermanently / softDelete 路径

**说明：**
- M5 时 deletePermanently 已会触发 `NoteFileStorage.deleteNoteDir(noteId)` 把整个 `notes/<noteId>/` 删干净；audioDir 是子目录，自动被带走。
- 软删（M9 deleted_at）不删本地文件 —— 复原后图片 / 音频都还在。这是设计意图，无需改动。
- 本任务为「核查」型：读代码确认行为正确，不需要编码改动；如果发现遗漏再补。

**步骤：**

- **Step 11.1：读 NoteRepository.deletePermanently 实现**

`Read` 工具读 `NoteRepository.kt`，定位 deletePermanently 方法体；确认其内部调用 `NoteFileStorage(...).deleteNoteDir(noteId)`。

- **Step 11.2：若未调用 → 补一行；若已调用 → 加注释「audio 子目录由 deleteNoteDir 一并清除（M10）」**

- **Step 11.3：跑测**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:test
```

预期：85 passed

- **Step 11.4：若有改动则 commit；无改动则跳过本任务的 commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt
git commit -m "chore(m10): 注明笔记彻删自动清理 audio 目录"
```

---

### Task 12：全量构建 + 测试通过（gate）

**步骤：**

- **Step 12.1：clean + assembleDebug + test**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:clean :app:assembleDebug :app:test --no-daemon
```

预期：BUILD SUCCESSFUL；test 85/85 passed。

- **Step 12.2：若任何步骤失败 → 不要 force commit，回头修；修完重跑直到全绿**

- **Step 12.3：跑通过后停下，等真机走查**

---

### Task 13：真机走查 + STATUS 收尾 + 内存更新

**说明：** 真机走查清单由用户人工执行，全部通过后再写 STATUS。

**真机走查清单（用户执行）：**

1. 编辑器底部工具栏可见 5 个按钮（清单 / 样式 / 图片 / 手写 / 录音），无遮挡，间距均匀
2. 点录音按钮 → 首次弹系统 RECORD_AUDIO 权限弹窗
3. 允许权限后 → 弹底部录音 Sheet，时间从 00:00 开始走表，文案"正在录音"
4. 录 ≥3 秒 → 点"停止" → Sheet 关闭，编辑器内当前位置出现录音块「▶ 录音 · 00:03」（实际值与录制时长一致），下方出现空 TextBlock 拿到焦点
5. 点录音块的播放按钮 → 图标变为暂停，听到回放声音；播放完图标自动回到播放
6. 播放中再点暂停按钮 → 立即停止，图标复位
7. 录两条音频，连续播放第二条；播第二条时第一条若在播应自动停（共享 AudioPlayer）
8. 退出编辑器（返回 / Home）→ 再进 → 录音块仍在，仍可播放
9. 长按录音块 → 弹 DeleteConfirmBottomSheet "删除录音"；点删除 → 块消失，再进编辑器仍无；本地 m4a 文件已删
10. 拒绝 RECORD_AUDIO 权限 → 弹自定义说明 dialog 引导去设置；再次点录音按钮重走流程不崩
11. 录音过程中按返回（系统返回键不会关 Sheet，因为 cancelable=false）；按取消按钮 → Sheet 关闭，无录音块插入，本地文件已删
12. 录音过程中按 Home / 切后台 → Sheet 自动取消（onPause 触发 forceCancel）；前台后无残留 Sheet
13. 在已有图片块、清单块、文本块、手写笔画的笔记里追加录音块 → 保存 → 再进入仍正确显示所有块类型（验证 NoteJson 兼容）

**步骤：**

- **Step 13.1：把走查清单原文贴回会话，让用户在真机依次跑**

- **Step 13.2：用户确认「通过」之后，更新 STATUS.md**

在 `docs/superpowers/STATUS.md` 顶部时间戳改为 `最后更新：2026-06-04（M10 语音录入完成 — PRD §13 进度 3/4，仅 M11 撤销重做待执行）`。

阶段地图最后两行（代码实施 / 手测验收）增加 M10 列。

进度表 M10 行从 ⏳ 待执行 改为 ✅ 完成。

在 M9 完成详情节后追加 `## M10 完成详情（2026-06-04）` 小节：

- 起因（PRD §13 M10 + 用户 2026-06-04 确认 3 个 UX 决策）
- 4 改动维度（实体/JSON、Storage、录音/播放、UI）
- 涉及文件清单（10 新 + 8 改）
- commit 列表（按 git log 实际抓取，~9 条）
- 测试统计（83 → 85 +2 audio JSON round-trip）
- 验收清单（13 条，从上面 copy）
- 执行模式（subagent-driven 串行 13 任务）

项目完成总览表增加 M10 行；累计 commit/测试更新。

`M10-M11 待执行` 章节去掉 M10 项；仅剩 M11。

- **Step 13.3：STATUS commit**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs(m10): 标记 M10 语音录入完成"
```

- **Step 13.4：更新 auto-memory**

更新 `/Users/yichen/.claude/projects/-Users-yichen-cainiao-AI-ClaudeCode-HuaWeiNote/memory/project_hwnote_context.md`：
- description / 顶部 current state 改为 `M1..M10 complete (2026-06-04). 85 unit tests + real-device walkthrough PASSED`
- 表格加 M10 行
- Latest HEAD 改为新 STATUS commit 的 sha
- 加 `M10 architectural notes worth remembering` 小节：MediaRecorder Android 12+ 构造器差异 / 共享 AudioPlayer 模式 / forceCancel 兜底模式 / NoteJson 容错让旧版本仍可读

更新 `MEMORY.md` 项目行：`M1..M10 complete (2026-06-04), 85 unit tests + real-device walkthrough PASSED; M11 撤销重做 待执行`

---

## 不动的东西（明确边界）

- 数据库 schema（M9 v2）— 不需要 v3 迁移
- M9 categories / soft-delete / metadata strip — 不动
- M6 手写 overlay — 不动
- M5 image / 拍照 pipeline — 不动
- 列表页（NoteListActivity / Adapter）— 不动；录音块在列表卡片预览中不需要特殊渲染（plainText 已跳过 AudioBlock）
- 现有 84 单元测试 — 全部继续 pass（M10 仅 +2）

---

## 风险与回退

- **MediaRecorder 兼容性**：targetSdk=36 对 RECORD_AUDIO 仍是 dangerous 运行时权限（Android 6+），无新限制。Android 14+ 部分机型对 setAudioSource(MIC) 有前台服务要求 — 编辑器是前台 Activity，无需 Service。
- **5 键 weight=1 在窄屏挤**：480dp 宽屏每按钮约 96dp，足够 24dp 图标 + 36dp 内边距。极端窄屏（< 360dp）可能挤，但 PRD 最低支持 360dp 不在问题区。如真机走查觉得挤，转用 HorizontalScrollView 包一层。
- **录音中 Activity 进程被回收**：AudioRecorder 在堆里，进程被杀就丢；目标文件留磁盘但未引用，下次启动也无入口看到（无 AudioBlock 引用它）。属于可接受降级（华为 Note 同样行为）；不做 onSaveInstanceState 持久化，否则恢复后录音状态不可信。
- **HEAD 失败回退**：每 task 单独 commit，失败可 `git reset --hard <prev-sha>` 回到上个绿点；不做强制推送，只在本地。

---

## 执行方式

Subagent-Driven Development（同 M9）：每任务 implementer DONE → spec reviewer → code quality reviewer → fix（如需）→ re-review → 标记完成。Task 12 / 13 仅静态验证 + 用户真机走查 + STATUS 收尾。

预估 4-6 小时（13 任务 × 平均 2 轮审核）。
