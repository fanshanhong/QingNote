# Phase 2B-3 音频块录制与播放 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让编辑器支持录音插入和音频播放，实现自定义 appflowy_editor AudioBlockComponentBuilder、录音底部弹窗和音频本地存储。

**Architecture:** 自定义 `AudioBlockComponentBuilder` 注册到 blockComponentBuilders map。`AudioPlayerService` 封装 just_audio 提供共享播放器。录音通过 `record` 包实现，UI 为不可外部关闭的 BottomSheet。音频文件存储复用 NoteFileStorage 的目录模式。

**Tech Stack:** Flutter + appflowy_editor 6.0.0 + record ^5.x + just_audio ^0.9.x + path_provider

---

## 文件清单

### 新建

| 文件 | 职责 |
|------|------|
| `lib/editor/audio_block_component.dart` | AudioBlockKeys + audioNode() + AudioBlockComponentBuilder + AudioBlockComponentWidget |
| `lib/services/audio_player_service.dart` | 共享播放器（just_audio 封装，token 管理） |
| `lib/widgets/editor/audio_recording_sheet.dart` | 录音底部弹窗 UI（计时器+停止/取消） |
| `test/services/audio_player_service_test.dart` | 播放器 token 状态逻辑测试 |
| `test/utils/note_file_storage_audio_test.dart` | 音频存储相关测试 |

### 修改

| 文件 | 改动 |
|------|------|
| `pubspec.yaml` | 添加 record + just_audio |
| `lib/utils/note_file_storage.dart` | 新增 audioDirFromBase / saveAudioToBase / audioFilePath |
| `lib/providers/note_editor_provider.dart` | 新增 insertAudioNode；saveNote 扩展音频孤儿清理 |
| `lib/widgets/editor/text_toolbar.dart` | 录音按钮从 SnackBar → onRecordTap 回调 |
| `lib/pages/note_editor_page.dart` | 注册 audio block builder；传入 onRecordTap；管理 AudioPlayerService 生命周期 |

---

### Task 1: 添加 record + just_audio 依赖

**Files:**
- Modify: `code/HuaweiNoteFlutter/pubspec.yaml`

- [ ] **Step 1: 添加依赖**

在 pubspec.yaml 的 dependencies 末尾添加：

```yaml
  record: ^5.1.2
  just_audio: ^0.9.40
```

- [ ] **Step 2: 运行 flutter pub get**

Run: `cd code/HuaweiNoteFlutter && flutter pub get`
Expected: 成功解析所有依赖

- [ ] **Step 3: Commit**

```bash
git add code/HuaweiNoteFlutter/pubspec.yaml code/HuaweiNoteFlutter/pubspec.lock
git commit -m "feat(p2b3): 添加 record + just_audio 依赖"
```

---

### Task 2: NoteFileStorage 扩展音频存储方法

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/utils/note_file_storage.dart`
- Create: `code/HuaweiNoteFlutter/test/utils/note_file_storage_audio_test.dart`

- [ ] **Step 1: 编写音频存储测试**

```dart
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/note_file_storage.dart';

void main() {
  late Directory tempDir;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('note_audio_test_');
  });

  tearDown(() {
    if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
  });

  group('NoteFileStorage audio', () {
    test('audioDirFromBase 返回正确路径', () {
      final dir = NoteFileStorage.audioDirFromBase(tempDir.path, 7);
      expect(dir.path, '${tempDir.path}/notes/7/audio');
    });

    test('saveAudioToBase 复制文件到音频目录', () async {
      // 准备源文件
      final srcDir = Directory('${tempDir.path}/src')..createSync();
      final srcFile = File('${srcDir.path}/recording.m4a')
        ..writeAsBytesSync([0x00, 0x01, 0x02, 0x03]);

      final saved = await NoteFileStorage.saveAudioToBase(
        tempDir.path, 10, srcFile.path,
      );
      expect(saved.existsSync(), true);
      expect(saved.path.endsWith('.m4a'), true);
      expect(saved.path.contains('/notes/10/audio/'), true);
      expect(saved.readAsBytesSync(), [0x00, 0x01, 0x02, 0x03]);
    });

    test('audioFilePath 返回完整路径', () {
      final path = NoteFileStorage.audioFilePath(tempDir.path, 3, '12345.m4a');
      expect(path, '${tempDir.path}/notes/3/audio/12345.m4a');
    });

    test('cleanOrphanFilesInDir 也适用于 audio 目录', () {
      final dir = NoteFileStorage.audioDirFromBase(tempDir.path, 1);
      dir.createSync(recursive: true);

      final kept = File('${dir.path}/keep.m4a')..writeAsBytesSync([1]);
      final orphan = File('${dir.path}/orphan.m4a')..writeAsBytesSync([2]);

      NoteFileStorage.cleanOrphanFilesInDir(dir, {kept.path});

      expect(kept.existsSync(), true);
      expect(orphan.existsSync(), false);
    });
  });
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/note_file_storage_audio_test.dart`
Expected: FAIL - 找不到 `audioDirFromBase` 等方法

- [ ] **Step 3: 实现音频存储方法**

在 `lib/utils/note_file_storage.dart` 的 `NoteFileStorage` 类末尾（`cleanOrphanImages` 之后）添加：

```dart
  static Directory audioDirFromBase(String basePath, int noteId) {
    return Directory('$basePath/notes/$noteId/audio');
  }

  static Future<Directory> audioDir(int noteId) async {
    final appDir = await getApplicationDocumentsDirectory();
    return audioDirFromBase(appDir.path, noteId);
  }

  static Future<File> saveAudioToBase(
    String basePath,
    int noteId,
    String sourceFilePath,
  ) async {
    final dir = audioDirFromBase(basePath, noteId);
    if (!dir.existsSync()) dir.createSync(recursive: true);
    final fileName = '${DateTime.now().millisecondsSinceEpoch}.m4a';
    final target = File('${dir.path}/$fileName');
    await File(sourceFilePath).copy(target.path);
    return target;
  }

  static Future<File> saveAudio(int noteId, String sourceFilePath) async {
    final appDir = await getApplicationDocumentsDirectory();
    return saveAudioToBase(appDir.path, noteId, sourceFilePath);
  }

  static String audioFilePath(String basePath, int noteId, String fileName) {
    return '$basePath/notes/$noteId/audio/$fileName';
  }

  static Future<void> cleanOrphanAudios(
    int noteId,
    Set<String> referencedFileNames,
  ) async {
    final dir = await audioDir(noteId);
    if (!dir.existsSync()) return;
    for (final file in dir.listSync().whereType<File>()) {
      final name = file.path.split('/').last;
      if (!referencedFileNames.contains(name)) {
        file.deleteSync();
      }
    }
  }
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/note_file_storage_audio_test.dart`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/utils/note_file_storage.dart code/HuaweiNoteFlutter/test/utils/note_file_storage_audio_test.dart
git commit -m "feat(p2b3): NoteFileStorage 扩展音频存储方法"
```

---

### Task 3: 实现 AudioPlayerService

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/services/audio_player_service.dart`
- Create: `code/HuaweiNoteFlutter/test/services/audio_player_service_test.dart`

- [ ] **Step 1: 创建 services 目录并编写测试**

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/services/audio_player_service.dart';

void main() {
  group('AudioPlayerService', () {
    test('初始状态 isPlaying 返回 false', () {
      final service = AudioPlayerService();
      expect(service.isPlaying('any-token'), false);
      service.dispose();
    });

    test('currentToken 初始为 null', () {
      final service = AudioPlayerService();
      expect(service.currentToken, isNull);
      service.dispose();
    });

    test('play 不存在的文件返回 false', () async {
      final service = AudioPlayerService();
      final result = await service.play(
        '/nonexistent/file.m4a',
        'token-1',
        () {},
      );
      expect(result, false);
      expect(service.isPlaying('token-1'), false);
      service.dispose();
    });
  });
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd code/HuaweiNoteFlutter && flutter test test/services/audio_player_service_test.dart`
Expected: FAIL - 找不到 `audio_player_service.dart`

- [ ] **Step 3: 实现 AudioPlayerService**

创建 `code/HuaweiNoteFlutter/lib/services/audio_player_service.dart`：

```dart
import 'dart:io';
import 'package:just_audio/just_audio.dart' as ja;

class AudioPlayerService {
  final _player = ja.AudioPlayer();
  String? _currentToken;
  VoidCallback? _onComplete;

  String? get currentToken => _currentToken;

  bool isPlaying(String token) =>
      _currentToken == token && _player.playing;

  Future<bool> play(
    String filePath,
    String token,
    VoidCallback onComplete,
  ) async {
    await stop();
    final file = File(filePath);
    if (!file.existsSync()) return false;

    _currentToken = token;
    _onComplete = onComplete;

    try {
      await _player.setFilePath(filePath);
      _player.playerStateStream.listen((state) {
        if (state.processingState == ja.ProcessingState.completed) {
          _handleComplete();
        }
      });
      _player.play();
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

需要创建 `lib/services/` 目录和 `test/services/` 目录。

- [ ] **Step 4: 运行测试验证通过**

Run: `cd code/HuaweiNoteFlutter && flutter test test/services/audio_player_service_test.dart`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/services/audio_player_service.dart code/HuaweiNoteFlutter/test/services/audio_player_service_test.dart
git commit -m "feat(p2b3): 添加 AudioPlayerService 共享播放器"
```

---

### Task 4: 实现 AudioBlockComponentBuilder

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/editor/audio_block_component.dart`

- [ ] **Step 1: 创建 editor 目录和音频块组件**

创建 `code/HuaweiNoteFlutter/lib/editor/audio_block_component.dart`：

```dart
import 'dart:io';
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/audio_player_service.dart';
import '../theme.dart';

class AudioBlockKeys {
  const AudioBlockKeys._();
  static const String type = 'audio';
  static const String fileName = 'file_name';
  static const String durationMs = 'duration_ms';
}

Node audioNode({
  required String fileName,
  required int durationMs,
}) {
  return Node(
    type: AudioBlockKeys.type,
    attributes: {
      AudioBlockKeys.fileName: fileName,
      AudioBlockKeys.durationMs: durationMs,
    },
  );
}

class AudioBlockComponentBuilder extends BlockComponentBuilder {
  AudioBlockComponentBuilder({
    required this.noteId,
    required this.audioPlayer,
    required this.editable,
    required this.onDelete,
    required this.basePath,
  });

  final int noteId;
  final AudioPlayerService audioPlayer;
  final bool editable;
  final void Function(Node node) onDelete;
  final String basePath;

  @override
  BlockComponentWidget build(BlockComponentContext blockComponentContext) {
    final node = blockComponentContext.node;
    return AudioBlockComponentWidget(
      key: node.key,
      node: node,
      noteId: noteId,
      audioPlayer: audioPlayer,
      editable: editable,
      onDelete: onDelete,
      basePath: basePath,
    );
  }

  @override
  bool validate(Node node) => node.attributes[AudioBlockKeys.fileName] is String;
}

class AudioBlockComponentWidget extends BlockComponentStatefulWidget {
  const AudioBlockComponentWidget({
    super.key,
    required super.node,
    required this.noteId,
    required this.audioPlayer,
    required this.editable,
    required this.onDelete,
    required this.basePath,
  });

  final int noteId;
  final AudioPlayerService audioPlayer;
  final bool editable;
  final void Function(Node node) onDelete;
  final String basePath;

  @override
  State<AudioBlockComponentWidget> createState() =>
      _AudioBlockComponentWidgetState();
}

class _AudioBlockComponentWidgetState
    extends State<AudioBlockComponentWidget> {
  bool _isPlaying = false;

  String get _fileName =>
      widget.node.attributes[AudioBlockKeys.fileName] as String? ?? '';

  int get _durationMs =>
      widget.node.attributes[AudioBlockKeys.durationMs] as int? ?? 0;

  String get _filePath =>
      '${widget.basePath}/notes/${widget.noteId}/audio/$_fileName';

  String get _token => '${widget.noteId}_$_fileName';

  @override
  void dispose() {
    if (widget.audioPlayer.isPlaying(_token)) {
      widget.audioPlayer.stop();
    }
    super.dispose();
  }

  void _togglePlay() async {
    if (_isPlaying) {
      await widget.audioPlayer.stop();
      return;
    }
    final ok = await widget.audioPlayer.play(_filePath, _token, () {
      if (mounted) setState(() => _isPlaying = false);
    });
    if (ok && mounted) {
      setState(() => _isPlaying = true);
    }
  }

  void _showDeleteConfirm() {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('确定删除这段录音？',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(ctx),
                      child: const Text('取消'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.danger,
                        foregroundColor: Colors.white,
                      ),
                      onPressed: () {
                        Navigator.pop(ctx);
                        widget.onDelete(widget.node);
                      },
                      child: const Text('删除'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onLongPress: widget.editable ? _showDeleteConfirm : null,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.bgWindow,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.divider),
        ),
        child: Row(
          children: [
            GestureDetector(
              onTap: _togglePlay,
              child: Container(
                width: 36,
                height: 36,
                decoration: const BoxDecoration(
                  color: AppColors.primary,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  _isPlaying ? Icons.pause : Icons.play_arrow,
                  color: Colors.white,
                  size: 20,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Text(
              '录音 ${_formatDuration(_durationMs)}',
              style: const TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _formatDuration(int ms) {
    final total = ms ~/ 1000;
    final mm = total ~/ 60;
    final ss = total % 60;
    return '${mm.toString().padLeft(2, '0')}:${ss.toString().padLeft(2, '0')}';
  }
}
```

- [ ] **Step 2: 运行 flutter analyze 验证**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/editor/audio_block_component.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/editor/audio_block_component.dart
git commit -m "feat(p2b3): 添加 AudioBlockComponentBuilder 自定义音频块"
```

---

### Task 5: 实现 AudioRecordingSheet 录音底部弹窗

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/widgets/editor/audio_recording_sheet.dart`

- [ ] **Step 1: 创建录音底部弹窗**

```dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:record/record.dart';
import '../../theme.dart';

class AudioRecordingResult {
  final String filePath;
  final int durationMs;
  const AudioRecordingResult(this.filePath, this.durationMs);
}

Future<AudioRecordingResult?> showAudioRecordingSheet(
  BuildContext context, {
  required String targetFilePath,
}) async {
  return showModalBottomSheet<AudioRecordingResult>(
    context: context,
    isDismissible: false,
    enableDrag: false,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _AudioRecordingContent(targetFilePath: targetFilePath),
  );
}

class _AudioRecordingContent extends StatefulWidget {
  final String targetFilePath;
  const _AudioRecordingContent({required this.targetFilePath});

  @override
  State<_AudioRecordingContent> createState() => _AudioRecordingContentState();
}

class _AudioRecordingContentState extends State<_AudioRecordingContent> {
  final _recorder = AudioRecorder();
  Timer? _timer;
  int _elapsedMs = 0;
  bool _isRecording = false;
  bool _terminated = false;

  @override
  void initState() {
    super.initState();
    _startRecording();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _recorder.dispose();
    super.dispose();
  }

  Future<void> _startRecording() async {
    final hasPermission = await _recorder.hasPermission();
    if (!hasPermission) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('需要麦克风权限')),
        );
        Navigator.pop(context);
      }
      return;
    }

    try {
      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 64000,
          sampleRate: 44100,
        ),
        path: widget.targetFilePath,
      );
      setState(() => _isRecording = true);
      _timer = Timer.periodic(const Duration(milliseconds: 500), (_) {
        setState(() => _elapsedMs += 500);
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('录音启动失败')),
        );
        Navigator.pop(context);
      }
    }
  }

  Future<void> _stop() async {
    if (_terminated) return;
    _terminated = true;
    _timer?.cancel();
    final path = await _recorder.stop();
    if (mounted && path != null) {
      Navigator.pop(
        context,
        AudioRecordingResult(path, _elapsedMs),
      );
    }
  }

  Future<void> _cancel() async {
    if (_terminated) return;
    _terminated = true;
    _timer?.cancel();
    await _recorder.stop();
    // 删除已录制的文件
    final file = java_io_File(widget.targetFilePath);
    if (file.existsSync()) file.deleteSync();
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.mic, size: 48, color: AppColors.danger),
            const SizedBox(height: 12),
            Text(
              _formatTime(_elapsedMs),
              style: const TextStyle(
                fontSize: 32,
                fontWeight: FontWeight.w300,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _cancel,
                    child: const Text('取消'),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isRecording ? _stop : null,
                    icon: const Icon(Icons.stop),
                    label: const Text('停止录音'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.danger,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _formatTime(int ms) {
    final total = ms ~/ 1000;
    final mm = total ~/ 60;
    final ss = total % 60;
    return '${mm.toString().padLeft(2, '0')}:${ss.toString().padLeft(2, '0')}';
  }
}
```

**注意：** 文件中 `java_io_File` 需替换为 `dart:io` 的 `File`。在文件顶部添加 `import 'dart:io' as io;` 并使用 `io.File`。

实际实现时文件顶部 import 为：
```dart
import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:record/record.dart';
import '../../theme.dart';
```

`_cancel` 方法中改为：
```dart
    final file = File(widget.targetFilePath);
    if (file.existsSync()) file.deleteSync();
```

- [ ] **Step 2: 运行 flutter analyze 验证**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/editor/audio_recording_sheet.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/audio_recording_sheet.dart
git commit -m "feat(p2b3): 添加 AudioRecordingSheet 录音底部弹窗"
```

---

### Task 6: NoteEditorNotifier 扩展录音插入 + 音频孤儿清理

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart`

- [ ] **Step 1: 添加 import**

在文件顶部添加：

```dart
import 'package:flutter/material.dart';
import '../editor/audio_block_component.dart';
import '../widgets/editor/audio_recording_sheet.dart';
```

- [ ] **Step 2: 添加 insertAudioNode 方法**

在 `insertImage()` 方法之后添加：

```dart
  Future<void> startRecording(BuildContext context) async {
    final noteId = await ensureNoteSaved();
    if (noteId == 0) return;

    final dir = await NoteFileStorage.audioDir(noteId);
    if (!dir.existsSync()) dir.createSync(recursive: true);
    final targetPath = '${dir.path}/${DateTime.now().millisecondsSinceEpoch}.m4a';

    if (!context.mounted) return;
    final result = await showAudioRecordingSheet(
      context,
      targetFilePath: targetPath,
    );
    if (result == null) return;

    final es = _editorState;
    if (es == null) return;

    final fileName = result.filePath.split('/').last;
    final selection = es.selection;
    final path = selection?.end.path ?? es.document.root.children.last.path;
    final insertPath = path.next;

    final transaction = es.transaction;
    transaction.insertNode(
      insertPath,
      audioNode(fileName: fileName, durationMs: result.durationMs),
    );
    transaction.afterSelection = Selection.collapsed(
      Position(path: insertPath.next, offset: 0),
    );
    await es.apply(transaction);
  }
```

- [ ] **Step 3: 扩展 saveNote 的孤儿清理**

将 `saveNote()` 中现有的孤儿清理块修改为同时清理音频：

```dart
      // 异步清理孤儿文件（不阻塞 UI）
      if (editorDoc != null) {
        final referencedPaths = _extractImagePaths(editorDoc);
        final referencedAudioNames = _extractAudioFileNames(editorDoc);
        final effectiveId = state.noteId == 0 ? id : state.noteId;
        NoteFileStorage.cleanOrphanImages(effectiveId, referencedPaths);
        NoteFileStorage.cleanOrphanAudios(effectiveId, referencedAudioNames);
      }
```

- [ ] **Step 4: 添加 _extractAudioFileNames 辅助方法**

在 `_extractImagePaths` 之后添加：

```dart
  Set<String> _extractAudioFileNames(Document document) {
    final names = <String>{};
    void visit(Node node) {
      if (node.type == AudioBlockKeys.type) {
        final name = node.attributes[AudioBlockKeys.fileName] as String?;
        if (name != null) names.add(name);
      }
      for (final child in node.children) {
        visit(child);
      }
    }
    visit(document.root);
    return names;
  }
```

- [ ] **Step 5: 运行 flutter analyze 验证**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/providers/note_editor_provider.dart`
Expected: No issues found

- [ ] **Step 6: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart
git commit -m "feat(p2b3): NoteEditorNotifier 添加录音插入+音频孤儿清理"
```

---

### Task 7: TextToolbar 录音按钮接通

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/widgets/editor/text_toolbar.dart`

- [ ] **Step 1: 添加 onRecordTap 回调**

修改 `TextToolbar`：

```dart
class TextToolbar extends StatelessWidget {
  final EditorState? editorState;
  final VoidCallback? onStyleTap;
  final VoidCallback? onImageTap;
  final VoidCallback? onRecordTap;

  const TextToolbar({
    super.key,
    this.editorState,
    this.onStyleTap,
    this.onImageTap,
    this.onRecordTap,
  });
```

- [ ] **Step 2: 修改录音按钮**

将录音按钮从 SnackBar 改为调用 `onRecordTap`，同时更新颜色为 active：

```dart
          _ToolbarButton(
            icon: Icons.mic_none,
            color: AppColors.editorIconActive,
            onTap: onRecordTap,
          ),
```

- [ ] **Step 3: 运行 flutter analyze 验证**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/editor/text_toolbar.dart`
Expected: No issues found

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/text_toolbar.dart
git commit -m "feat(p2b3): 工具栏录音按钮接通 onRecordTap"
```

---

### Task 8: NoteEditorPage 集成（AudioBlockBuilder 注册 + AudioPlayerService + 录音回调）

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

- [ ] **Step 1: 添加 import 和 AudioPlayerService 字段**

在文件顶部添加 import：

```dart
import 'package:path_provider/path_provider.dart';
import '../editor/audio_block_component.dart';
import '../services/audio_player_service.dart';
```

在 `_NoteEditorPageState` 类中添加字段：

```dart
  final _audioPlayer = AudioPlayerService();
  String _appDocPath = '';
```

- [ ] **Step 2: 在 initState 中获取 appDocPath**

在 `Future.microtask` 块内、`await notifier.loadNote()` 之后添加：

```dart
      final appDir = await getApplicationDocumentsDirectory();
      _appDocPath = appDir.path;
```

- [ ] **Step 3: 在 dispose 中释放 AudioPlayerService**

在 `dispose` 方法中添加 `_audioPlayer.dispose();`：

```dart
  @override
  void dispose() {
    _audioPlayer.dispose();
    _transactionSub?.cancel();
    _scrollController?.dispose();
    _titleFocusNode.dispose();
    _titleController.dispose();
    super.dispose();
  }
```

- [ ] **Step 4: 修改 _buildBlockComponentBuilders 注册 audio block**

```dart
  Map<String, BlockComponentBuilder> _buildBlockComponentBuilders() {
    final state = ref.read(noteEditorProvider(widget.noteId));
    final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
    return {
      ...standardBlockComponentBuilderMap,
      TodoListBlockKeys.type: TodoListBlockComponentBuilder(
        configuration: const BlockComponentConfiguration(),
        textStyleBuilder: (checked) => TextStyle(
          decoration: checked ? TextDecoration.lineThrough : null,
          color: checked ? AppColors.textHint : AppColors.textPrimary,
        ),
      ),
      AudioBlockKeys.type: AudioBlockComponentBuilder(
        noteId: state.noteId,
        audioPlayer: _audioPlayer,
        editable: state.isEditing,
        basePath: _appDocPath,
        onDelete: (node) {
          final es = notifier.editorState;
          if (es == null) return;
          final transaction = es.transaction;
          transaction.deleteNode(node);
          es.apply(transaction);
        },
      ),
    };
  }
```

- [ ] **Step 5: 修改 _buildBottomBar 传入 onRecordTap**

```dart
  Widget _buildBottomBar(NoteEditorNotifier notifier, NoteEditorState state) {
    if (state.isEditing) {
      return TextToolbar(
        editorState: notifier.editorState,
        onStyleTap: () => _showStylePicker(notifier),
        onImageTap: () => notifier.insertImage(),
        onRecordTap: () => notifier.startRecording(context),
      );
    }
    final note = state.loadedNote;
    final plainText = [state.title, note?.plainText ?? '']
        .where((s) => s.isNotEmpty)
        .join('\n');
    return BrowseBottomBar(
      isFavorite: note?.isFavorite ?? false,
      shareText: plainText,
      onToggleFavorite: () => notifier.toggleFavorite(),
      onDelete: () async {
        await notifier.softDelete();
        if (mounted) context.pop();
      },
    );
  }
```

- [ ] **Step 6: 运行 flutter analyze 验证**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/pages/note_editor_page.dart`
Expected: No issues found

- [ ] **Step 7: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat(p2b3): NoteEditorPage 集成音频块渲染+录音回调+播放器"
```

---

### Task 9: 平台配置 + 全量验证

**Files:**
- Modify: `code/HuaweiNoteFlutter/ios/Runner/Info.plist`
- Modify: `code/HuaweiNoteFlutter/macos/Runner/DebugProfile.entitlements`
- Modify: `code/HuaweiNoteFlutter/macos/Runner/Release.entitlements`

- [ ] **Step 1: 添加 iOS 麦克风权限**

在 `ios/Runner/Info.plist` 的 `<dict>` 内添加（如不存在）：

```xml
<key>NSMicrophoneUsageDescription</key>
<string>需要使用麦克风进行录音</string>
```

- [ ] **Step 2: 添加 macOS 音频输入权限**

在 `macos/Runner/DebugProfile.entitlements` 和 `macos/Runner/Release.entitlements` 的 `<dict>` 内添加：

```xml
<key>com.apple.security.device.audio-input</key>
<true/>
```

- [ ] **Step 3: 运行全部测试**

Run: `cd code/HuaweiNoteFlutter && flutter test`
Expected: All tests PASS

- [ ] **Step 4: 运行 flutter analyze**

Run: `cd code/HuaweiNoteFlutter && flutter analyze`
Expected: No issues found

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/ios/Runner/Info.plist code/HuaweiNoteFlutter/macos/Runner/DebugProfile.entitlements code/HuaweiNoteFlutter/macos/Runner/Release.entitlements
git commit -m "feat(p2b3): 添加 iOS/macOS 麦克风权限配置"
```

---

## 验证清单

- [ ] 录音按钮：点工具栏麦克风 → 弹出录音底部弹窗
- [ ] 录音计时：弹窗内显示 MM:SS 实时计时
- [ ] 停止录音：点停止 → 弹窗关闭 → 编辑器中出现音频块
- [ ] 取消录音：点取消 → 弹窗关闭 → 无节点插入 → 文件已删除
- [ ] 播放：点音频块播放按钮 → 播放 → 图标变暂停
- [ ] 暂停：点暂停 → 停止播放 → 图标恢复播放
- [ ] 播放结束：播放完成 → 自动恢复 idle 状态
- [ ] 单一播放：播放第二个音频 → 第一个自动停止
- [ ] 持久化：保存 → 退出 → 重开 → 音频块仍在 → 可播放
- [ ] 长按删除：编辑模式长按音频块 → 确认弹窗 → 删除节点
- [ ] 孤儿清理：删除音频块 → 保存 → 检查 audio 目录文件已清理
- [ ] 权限拒绝：拒绝麦克风权限 → Toast 提示 → 无崩溃
