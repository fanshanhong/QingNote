# Phase 2B-3 设计文档 — 音频块录制与播放

> 对标 Android AudioRecorder + AudioPlayer + AudioBlockView + AudioRecordingBottomSheet

**目标：** 让编辑器支持录音插入和音频播放。实现自定义 appflowy_editor `AudioBlockComponentBuilder`，录音底部弹窗，以及音频文件本地存储。

**技术栈：** Flutter + appflowy_editor 6.0.0 + record ^5.x + just_audio ^0.9.x + path_provider

---

## 1. 架构决策

### 1.1 自定义 Block 策略

appflowy_editor 没有内置音频块，需要自定义：
- 定义 `AudioBlockKeys`（type, file_name, duration_ms）
- 实现 `audioNode()` 工厂函数
- 实现 `AudioBlockComponentBuilder` + `AudioBlockComponentWidget`
- 注册到 `blockComponentBuilders` map

### 1.2 录音方案

使用 `record` 包（^5.x）：
- 跨平台：iOS / Android / macOS / Windows
- 格式：AAC 编码，M4A 容器
- 参数：44.1kHz 采样率，64kbps 码率（与 Android 一致）
- 状态机：IDLE → RECORDING → IDLE

### 1.3 播放方案

使用 `just_audio` 包（^0.9.x）：
- 共享播放器实例：同一时刻最多一个音频在播
- 播放新音频时自动停止旧音频
- 播放完成/错误/停止 → 回调 UI 恢复 idle 状态

### 1.4 不做的事

- ❌ 波形可视化（远期 Rust FFI 实现）
- ❌ 录音暂停/继续（Android 端也没有）
- ❌ 播放进度条（Android 端只有 play/pause 按钮）
- ❌ 音频裁剪/编辑
- ❌ 后台录音（录音底部弹窗必须在前台）

---

## 2. 文档格式

### 2.1 节点定义

```dart
class AudioBlockKeys {
  static const String type = 'audio';
  static const String fileName = 'file_name';
  static const String durationMs = 'duration_ms';
}
```

### 2.2 节点工厂

```dart
Node audioNode({
  required String fileName,
  required int durationMs,
}) => Node(
  type: AudioBlockKeys.type,
  attributes: {
    AudioBlockKeys.fileName: fileName,
    AudioBlockKeys.durationMs: durationMs,
  },
);
```

### 2.3 JSON 序列化示例

```json
{
  "type": "audio",
  "data": {
    "file_name": "1717689600000.m4a",
    "duration_ms": 15230
  }
}
```

---

## 3. 录音流程

### 3.1 触发流程

```
用户点工具栏"录音"按钮
  → NoteEditorNotifier.ensureNoteSaved()（新笔记先保存获取 noteId）
  → 请求麦克风权限（permission_handler 或 record 包自带）
  → 显示 AudioRecordingSheet
  → record 包开始录音，目标文件：{audioDir}/{timestamp}.m4a
  → 用户点"停止" → 停止录音，获取 durationMs
  → 插入 audioNode(fileName, durationMs) 到文档
  → 用户点"取消" → 停止录音，删除文件
```

### 3.2 AudioRecordingSheet

底部弹窗（BottomSheet），不可外部关闭：
- 实时计时器（MM:SS 格式，每 500ms 刷新）
- "停止录音"按钮 → 完成录音，回传 durationMs
- "取消"按钮 → 取消录音，删除文件
- 录音失败（权限/硬件）→ Toast 提示 + 自动关闭

### 3.3 权限处理

`record` 包提供 `AudioRecorder().hasPermission()` 和 `AudioRecorder().checkPermission()` 方法。无需额外 permission_handler 依赖。在录音前检查：
- 已授权 → 直接录音
- 未授权 → 触发系统权限弹窗
- 拒绝 → Toast 提示"需要麦克风权限"

---

## 4. 播放方案

### 4.1 AudioPlayerService

全局共享播放器，与 Android `AudioPlayer` 同设计：

```dart
class AudioPlayerService {
  final _player = ap.AudioPlayer();  // just_audio
  String? _currentToken;
  VoidCallback? _onComplete;

  bool isPlaying(String token) => _currentToken == token && _player.playing;

  Future<bool> play(String filePath, String token, VoidCallback onComplete) async {
    await stop();
    final file = File(filePath);
    if (!file.existsSync()) return false;
    _currentToken = token;
    _onComplete = onComplete;
    try {
      await _player.setFilePath(filePath);
      _player.play();
      _player.playerStateStream.listen((state) {
        if (state.processingState == ProcessingState.completed) {
          _handleComplete();
        }
      });
      return true;
    } catch (_) {
      _reset();
      return false;
    }
  }

  Future<void> stop() async {
    if (_currentToken == null) return;
    await _player.stop();
    _handleComplete();
  }

  void _handleComplete() {
    final cb = _onComplete;
    _reset();
    cb?.call();
  }

  void _reset() {
    _currentToken = null;
    _onComplete = null;
  }

  void dispose() {
    _player.dispose();
  }
}
```

### 4.2 播放器生命周期

- 通过 Riverpod Provider 管理，编辑器页面级共享
- 页面 dispose 时调用 `audioPlayerService.dispose()`
- 同一时刻最多一个音频块在播放

---

## 5. AudioBlockComponentBuilder

### 5.1 渲染

自定义 Widget，匹配 Android AudioBlockView 外观：
- 左侧播放/暂停圆形按钮（图标切换）
- 右侧文字标签："录音 MM:SS"
- 编辑模式下长按弹出删除确认

### 5.2 Widget 结构

```dart
class AudioBlockComponentWidget extends StatefulWidget {
  final Node node;
  final AudioPlayerService audioPlayer;
  final int noteId;
  final bool editable;
  // ...
}
```

State 管理：
- `_isPlaying` — 当前块是否正在播放
- 点击播放 → `audioPlayer.play(filePath, token, onComplete)` → setState playing
- onComplete 回调 → setState idle
- 点击暂停 → `audioPlayer.stop()`

### 5.3 文件路径解析

audioNode 只存 `file_name`（如 `1717689600000.m4a`），完整路径需运行时拼接：
- `{appDocDir}/notes/{noteId}/audio/{file_name}`
- noteId 通过 Widget 注入（从 NoteEditorNotifier.state.noteId 获取）

---

## 6. 文件存储扩展

### 6.1 NoteFileStorage 新增方法

```dart
static Directory audioDirFromBase(String basePath, int noteId) {
  return Directory('$basePath/notes/$noteId/audio');
}

static Future<Directory> audioDir(int noteId) async { ... }

static Future<File> saveAudio(int noteId, String sourceFilePath) async {
  // 复制录音文件到 audioDir，文件名用 timestamp.m4a
  // 返回目标文件 File
}

static String audioFilePath(String basePath, int noteId, String fileName) {
  return '$basePath/notes/$noteId/audio/$fileName';
}
```

### 6.2 孤儿清理

与图片同策略：保存笔记时扫描 audioDir 与文档中实际引用的 file_name 差集，删除孤儿文件。

复用 `cleanOrphanFilesInDir`，在 `_extractImagePaths` 旁新增 `_extractAudioFileNames` 辅助方法。

---

## 7. NoteEditorNotifier 扩展

### 7.1 新增 startRecording 方法

```dart
Future<void> startRecording(BuildContext context) async {
  final noteId = await ensureNoteSaved();
  if (noteId == 0) return;
  // 显示 AudioRecordingSheet，录音完成后插入 audioNode
}
```

### 7.2 saveNote 扩展孤儿清理

在现有 `_extractImagePaths` 递归遍历中同时收集 audio 节点的 file_name，清理音频孤儿文件。

---

## 8. 文件清单

### 新建

| 文件 | 职责 |
|------|------|
| `lib/editor/audio_block_component.dart` | AudioBlockKeys + audioNode + AudioBlockComponentBuilder + Widget |
| `lib/services/audio_player_service.dart` | 共享播放器（just_audio 封装） |
| `lib/widgets/editor/audio_recording_sheet.dart` | 录音底部弹窗 UI |
| `test/services/audio_player_service_test.dart` | 播放器单测（状态管理逻辑） |

### 修改

| 文件 | 改动 |
|------|------|
| `pubspec.yaml` | 添加 record + just_audio |
| `lib/utils/note_file_storage.dart` | 新增 audioDirFromBase / audioDir / saveAudio / audioFilePath |
| `lib/providers/note_editor_provider.dart` | 新增 startRecording；saveNote 中扩展音频孤儿清理 |
| `lib/widgets/editor/text_toolbar.dart` | 录音按钮从 SnackBar → onRecordTap 回调 |
| `lib/pages/note_editor_page.dart` | blockComponentBuilders 注册 audio；传入 onRecordTap；注入 AudioPlayerService |
| `test/utils/note_file_storage_test.dart` | 补充 audio 相关测试 |

---

## 9. 平台配置

### iOS

`Info.plist` 已有 `NSMicrophoneUsageDescription`？如无，需添加：
```xml
<key>NSMicrophoneUsageDescription</key>
<string>需要使用麦克风进行录音</string>
```

### macOS

`DebugProfile.entitlements` + `Release.entitlements` 需添加：
```xml
<key>com.apple.security.device.audio-input</key>
<true/>
```

### Android

`AndroidManifest.xml` 需添加（如无）：
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
```

---

## 10. 测试策略

- `audio_player_service_test.dart`：播放器状态逻辑（token 管理、isPlaying 判断）
- `note_file_storage_test.dart`：audioDir 路径、saveAudio 文件复制
- 手动验证：录音按钮 → 弹窗 → 计时 → 停止 → 插入 → 播放 → 保存 → 重开 → 仍可播放
- 手动验证：取消录音 → 文件已删除 → 无节点插入
- 手动验证：删除音频块 → 保存 → 孤儿文件被清理

---

## 11. 范围边界

### 包含
- ✅ 录音（AAC/M4A，44.1kHz/64kbps）
- ✅ 录音 UI（底部弹窗 + 计时器 + 停止/取消）
- ✅ 播放（共享播放器，play/stop 切换）
- ✅ 自定义 AudioBlockComponentBuilder
- ✅ 音频文件本地存储
- ✅ 音频孤儿清理
- ✅ 权限处理（麦克风）
- ✅ 长按删除确认

### 不包含
- ❌ 波形可视化
- ❌ 播放进度条
- ❌ 录音暂停/继续
- ❌ 音频裁剪/编辑
- ❌ 后台录音
- ❌ 手写块（Phase 2B-4）
