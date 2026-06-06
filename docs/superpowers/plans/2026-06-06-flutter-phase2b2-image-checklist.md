# Phase 2B-2 图片块插入 + 清单块完善 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让编辑器的图片选择/压缩/存储/插入完整可用，清单块外观定制（checked 删除线+灰色），工具栏 toggle 逻辑完善。

**Architecture:** 利用 appflowy_editor 6.0.0 内置 `ImageBlockComponentBuilder`（本地文件路径方案）和 `TodoListBlockComponentBuilder`（textStyleBuilder 定制），补齐图片拾取/压缩/本地存储/工具栏接通。新增 `NoteFileStorage` + `ImageCompressor` 工具类，修改 `NoteEditorNotifier` 增加 `ensureNoteSaved()` 和孤儿清理。

**Tech Stack:** Flutter + appflowy_editor 6.0.0 + image_picker ^1.1.2 + flutter_image_compress ^2.3.0 + path_provider（已有）

---

## 文件清单

### 新建

| 文件 | 职责 |
|------|------|
| `lib/utils/note_file_storage.dart` | 笔记图片目录管理 + 保存/删除/孤儿清理 |
| `lib/utils/image_compressor.dart` | 图片压缩（长边 1920 / JPEG 85） |
| `test/utils/note_file_storage_test.dart` | 文件存储单测 |
| `test/utils/image_compressor_test.dart` | 压缩逻辑单测 |

### 修改

| 文件 | 改动 |
|------|------|
| `pubspec.yaml` | 添加 image_picker + flutter_image_compress |
| `lib/providers/note_editor_provider.dart` | 新增 `ensureNoteSaved()`；saveNote 后调 cleanOrphanImages |
| `lib/widgets/editor/text_toolbar.dart` | 图片按钮调用图片选择流程；清单按钮增加 toggle（todo→paragraph） |
| `lib/pages/note_editor_page.dart` | 传入自定义 blockComponentBuilders（todo_list 外观）；图片插入回调 |

---

### Task 1: 添加依赖

**Files:**
- Modify: `code/HuaweiNoteFlutter/pubspec.yaml`

- [ ] **Step 1: 添加 image_picker 和 flutter_image_compress 依赖**

```yaml
dependencies:
  flutter:
    sdk: flutter
  sqflite: ^2.4.1
  path_provider: ^2.1.5
  flutter_riverpod: ^2.6.1
  go_router: ^14.8.1
  shared_preferences: ^2.3.4
  flutter_staggered_grid_view: ^0.7.0
  appflowy_editor: ">=6.0.0 <7.0.0"
  share_plus: ^12.0.2
  image_picker: ^1.1.2
  flutter_image_compress: ^2.3.0
```

- [ ] **Step 2: 运行 flutter pub get 验证依赖解析**

Run: `cd code/HuaweiNoteFlutter && flutter pub get`
Expected: 成功解析所有依赖，无冲突

- [ ] **Step 3: Commit**

```bash
git add code/HuaweiNoteFlutter/pubspec.yaml code/HuaweiNoteFlutter/pubspec.lock
git commit -m "feat(p2b2): 添加 image_picker + flutter_image_compress 依赖"
```

---

### Task 2: 实现 ImageCompressor 图片压缩工具

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/utils/image_compressor.dart`
- Create: `code/HuaweiNoteFlutter/test/utils/image_compressor_test.dart`

- [ ] **Step 1: 编写 ImageCompressor 测试**

```dart
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/image_compressor.dart';

void main() {
  group('ImageCompressor', () {
    test('computeScaledSize - 横向大图应缩放到 maxDimension', () {
      final result = ImageCompressor.computeScaledSize(
        width: 3840,
        height: 2160,
        maxDimension: 1920,
      );
      expect(result.width, 1920);
      expect(result.height, 1080);
    });

    test('computeScaledSize - 纵向大图应缩放到 maxDimension', () {
      final result = ImageCompressor.computeScaledSize(
        width: 1080,
        height: 3840,
        maxDimension: 1920,
      );
      expect(result.width, 540);
      expect(result.height, 1920);
    });

    test('computeScaledSize - 小图不缩放', () {
      final result = ImageCompressor.computeScaledSize(
        width: 800,
        height: 600,
        maxDimension: 1920,
      );
      expect(result.width, 800);
      expect(result.height, 600);
    });

    test('computeScaledSize - 正方形大图', () {
      final result = ImageCompressor.computeScaledSize(
        width: 4000,
        height: 4000,
        maxDimension: 1920,
      );
      expect(result.width, 1920);
      expect(result.height, 1920);
    });

    test('computeScaledSize - 刚好等于 maxDimension 不缩放', () {
      final result = ImageCompressor.computeScaledSize(
        width: 1920,
        height: 1080,
        maxDimension: 1920,
      );
      expect(result.width, 1920);
      expect(result.height, 1080);
    });
  });
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/image_compressor_test.dart`
Expected: FAIL - 找不到 `image_compressor.dart`

- [ ] **Step 3: 实现 ImageCompressor**

```dart
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_image_compress/flutter_image_compress.dart';

class ScaledSize {
  final int width;
  final int height;
  const ScaledSize(this.width, this.height);
}

class ImageCompressor {
  static ScaledSize computeScaledSize({
    required int width,
    required int height,
    int maxDimension = 1920,
  }) {
    final longer = width >= height ? width : height;
    if (longer <= maxDimension) return ScaledSize(width, height);
    final ratio = maxDimension / longer;
    return ScaledSize(
      (width * ratio).round(),
      (height * ratio).round(),
    );
  }

  static Future<Uint8List?> compress(
    File source, {
    int maxDimension = 1920,
    int quality = 85,
  }) async {
    final result = await FlutterImageCompress.compressWithFile(
      source.absolute.path,
      minWidth: maxDimension,
      minHeight: maxDimension,
      quality: quality,
      format: CompressFormat.jpeg,
    );
    return result;
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/image_compressor_test.dart`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/utils/image_compressor.dart code/HuaweiNoteFlutter/test/utils/image_compressor_test.dart
git commit -m "feat(p2b2): 添加 ImageCompressor 图片压缩工具"
```

---

### Task 3: 实现 NoteFileStorage 文件存储工具

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/utils/note_file_storage.dart`
- Create: `code/HuaweiNoteFlutter/test/utils/note_file_storage_test.dart`

- [ ] **Step 1: 编写 NoteFileStorage 测试**

```dart
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/note_file_storage.dart';

void main() {
  late Directory tempDir;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('note_storage_test_');
  });

  tearDown(() {
    if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
  });

  group('NoteFileStorage', () {
    test('imageDir 返回正确路径', () {
      final dir = NoteFileStorage.imageDirFromBase(tempDir.path, 42);
      expect(dir.path, '${tempDir.path}/notes/42/images');
    });

    test('saveImage 创建目录并写入文件', () async {
      final bytes = Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0]);
      final file = await NoteFileStorage.saveImageToBase(
        tempDir.path, 1, bytes,
      );
      expect(file.existsSync(), true);
      expect(file.path.endsWith('.jpg'), true);
      expect(file.readAsBytesSync(), bytes);
    });

    test('deleteImage 删除指定文件', () async {
      final bytes = Uint8List.fromList([1, 2, 3]);
      final file = await NoteFileStorage.saveImageToBase(
        tempDir.path, 1, bytes,
      );
      expect(file.existsSync(), true);
      NoteFileStorage.deleteImage(file.path);
      expect(file.existsSync(), false);
    });

    test('cleanOrphanFiles 删除未引用的文件', () async {
      final dir = NoteFileStorage.imageDirFromBase(tempDir.path, 1);
      dir.createSync(recursive: true);

      final kept = File('${dir.path}/kept.jpg')..writeAsBytesSync([1]);
      final orphan = File('${dir.path}/orphan.jpg')..writeAsBytesSync([2]);

      NoteFileStorage.cleanOrphanFilesInDir(dir, {kept.path});

      expect(kept.existsSync(), true);
      expect(orphan.existsSync(), false);
    });

    test('cleanOrphanFiles 目录不存在时不报错', () {
      final dir = Directory('${tempDir.path}/nonexistent');
      expect(
        () => NoteFileStorage.cleanOrphanFilesInDir(dir, {}),
        returnsNormally,
      );
    });

    test('deleteNoteDir 删除整个笔记目录', () async {
      final bytes = Uint8List.fromList([1, 2, 3]);
      await NoteFileStorage.saveImageToBase(tempDir.path, 5, bytes);
      final noteDir = Directory('${tempDir.path}/notes/5');
      expect(noteDir.existsSync(), true);

      NoteFileStorage.deleteNoteDirFromBase(tempDir.path, 5);
      expect(noteDir.existsSync(), false);
    });
  });
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/note_file_storage_test.dart`
Expected: FAIL - 找不到 `note_file_storage.dart`

- [ ] **Step 3: 实现 NoteFileStorage**

```dart
import 'dart:io';
import 'dart:typed_data';
import 'package:path_provider/path_provider.dart';

class NoteFileStorage {
  static Future<Directory> imageDir(int noteId) async {
    final appDir = await getApplicationDocumentsDirectory();
    return imageDirFromBase(appDir.path, noteId);
  }

  static Directory imageDirFromBase(String basePath, int noteId) {
    return Directory('$basePath/notes/$noteId/images');
  }

  static Future<File> saveImage(int noteId, Uint8List bytes) async {
    final appDir = await getApplicationDocumentsDirectory();
    return saveImageToBase(appDir.path, noteId, bytes);
  }

  static Future<File> saveImageToBase(
    String basePath,
    int noteId,
    Uint8List bytes,
  ) async {
    final dir = imageDirFromBase(basePath, noteId);
    if (!dir.existsSync()) dir.createSync(recursive: true);
    final fileName = '${DateTime.now().millisecondsSinceEpoch}.jpg';
    final file = File('${dir.path}/$fileName');
    await file.writeAsBytes(bytes);
    return file;
  }

  static void deleteImage(String filePath) {
    final file = File(filePath);
    if (file.existsSync()) file.deleteSync();
  }

  static void deleteNoteDirFromBase(String basePath, int noteId) {
    final dir = Directory('$basePath/notes/$noteId');
    if (dir.existsSync()) dir.deleteSync(recursive: true);
  }

  static Future<void> deleteNoteDir(int noteId) async {
    final appDir = await getApplicationDocumentsDirectory();
    deleteNoteDirFromBase(appDir.path, noteId);
  }

  static void cleanOrphanFilesInDir(
    Directory dir,
    Set<String> referencedPaths,
  ) {
    if (!dir.existsSync()) return;
    for (final file in dir.listSync().whereType<File>()) {
      if (!referencedPaths.contains(file.path)) {
        file.deleteSync();
      }
    }
  }

  static Future<void> cleanOrphanImages(
    int noteId,
    Set<String> referencedPaths,
  ) async {
    final dir = await imageDir(noteId);
    cleanOrphanFilesInDir(dir, referencedPaths);
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/note_file_storage_test.dart`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/utils/note_file_storage.dart code/HuaweiNoteFlutter/test/utils/note_file_storage_test.dart
git commit -m "feat(p2b2): 添加 NoteFileStorage 图片文件存储工具"
```

---

### Task 4: NoteEditorNotifier 新增 ensureNoteSaved + 图片插入 + 孤儿清理

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart`

- [ ] **Step 1: 添加 import 和 extractImagePaths 辅助方法**

在文件顶部添加 import：

```dart
import 'dart:io';
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import '../models/note.dart';
import '../models/note_content.dart';
import '../repositories/note_repository.dart';
import '../repositories/notebook_repository.dart';
import '../repositories/category_repository.dart';
import '../utils/image_compressor.dart';
import '../utils/note_file_storage.dart';
import 'repository_providers.dart';
```

- [ ] **Step 2: 添加 ensureNoteSaved 方法**

在 `NoteEditorNotifier` 类中 `saveNote()` 之后添加：

```dart
  Future<int> ensureNoteSaved() async {
    if (state.noteId != 0) return state.noteId;
    await saveNote();
    return state.noteId;
  }
```

- [ ] **Step 3: 添加 insertImage 方法**

在 `ensureNoteSaved()` 之后添加：

```dart
  Future<void> insertImage() async {
    final noteId = await ensureNoteSaved();
    if (noteId == 0) return;

    final picker = ImagePicker();
    final picked = await picker.pickImage(source: ImageSource.gallery);
    if (picked == null) return;

    final source = File(picked.path);
    final compressed = await ImageCompressor.compress(source);
    if (compressed == null) return;

    final savedFile = await NoteFileStorage.saveImage(noteId, compressed);

    final es = _editorState;
    if (es == null) return;

    final selection = es.selection;
    final path = selection?.end.path ?? es.document.root.children.last.path;
    final insertPath = path.next;

    final transaction = es.transaction;
    transaction.insertNode(insertPath, imageNode(url: savedFile.path));
    transaction.afterSelection = Selection.collapsed(
      Position(path: insertPath.next, offset: 0),
    );
    await es.apply(transaction);
  }
```

- [ ] **Step 4: 在 saveNote 成功后添加孤儿清理**

修改 `saveNote()` 方法，在 `final id = await _noteRepo.save(note);` 之后、状态更新之前，添加孤儿清理调用：

```dart
  Future<bool> saveNote() async {
    if (state.isSaving) return false;
    if (state.noteId == 0 && state.isNoteEmpty) return false;

    state = state.copyWith(isSaving: true);
    try {
      final editorDoc = _editorState?.document;
      final docJson = editorDoc?.toJson() ?? {};
      final content = NoteContent(
        documentJson: {'document': docJson},
        handwriting: state.loadedNote?.content.handwriting ?? [],
      );
      final now = DateTime.now().millisecondsSinceEpoch;
      final note = (state.loadedNote ?? Note.newNote(now: now)).copyWith(
        title: state.title,
        plainText: content.toPlainText(),
        content: content,
        notebookId: state.pendingNotebookId,
        background: state.pendingBackground,
        updatedAt: now,
      );
      final id = await _noteRepo.save(note);

      // 异步清理孤儿图片（不阻塞 UI）
      if (editorDoc != null) {
        final referencedPaths = _extractImagePaths(editorDoc);
        final effectiveId = state.noteId == 0 ? id : state.noteId;
        NoteFileStorage.cleanOrphanImages(effectiveId, referencedPaths);
      }

      if (state.noteId == 0) {
        state = state.copyWith(
          noteId: id,
          loadedNote: note.copyWith(id: id),
          isSaving: false,
        );
      } else {
        state = state.copyWith(
          loadedNote: note,
          isSaving: false,
        );
      }
      return true;
    } catch (_) {
      state = state.copyWith(isSaving: false);
      return false;
    }
  }

  Set<String> _extractImagePaths(Document document) {
    final paths = <String>{};
    void visit(Node node) {
      if (node.type == ImageBlockKeys.type) {
        final url = node.attributes[ImageBlockKeys.url] as String?;
        if (url != null) paths.add(url);
      }
      for (final child in node.children) {
        visit(child);
      }
    }
    visit(document.root);
    return paths;
  }
```

- [ ] **Step 5: 运行 flutter analyze 验证无错误**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/providers/note_editor_provider.dart`
Expected: No issues found

- [ ] **Step 6: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart
git commit -m "feat(p2b2): NoteEditorNotifier 添加图片插入+ensureNoteSaved+孤儿清理"
```

---

### Task 5: 清单块外观定制（blockComponentBuilders 注入）

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

- [ ] **Step 1: 在 `_buildEditor` 方法中添加自定义 blockComponentBuilders**

将 `_buildEditor` 方法修改为：

```dart
  Widget _buildEditor(NoteEditorNotifier notifier, NoteEditorState state) {
    final editorState = notifier.editorState;
    if (!_editorReady || editorState == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return AppFlowyEditor(
      editorState: editorState,
      editable: state.isEditing,
      editorScrollController: _scrollController,
      blockComponentBuilders: _buildBlockComponentBuilders(),
      editorStyle: EditorStyle.mobile(
        padding: const EdgeInsets.symmetric(
          horizontal: AppDimens.editorContentPadding,
        ),
        textStyleConfiguration: TextStyleConfiguration(
          text: const TextStyle(fontSize: 16, color: AppColors.textPrimary),
        ),
      ),
    );
  }

  Map<String, BlockComponentBuilder> _buildBlockComponentBuilders() {
    return {
      ...standardBlockComponentBuilderMap,
      TodoListBlockKeys.type: TodoListBlockComponentBuilder(
        configuration: const BlockComponentConfiguration(),
        textStyleBuilder: (checked) => TextStyle(
          decoration: checked ? TextDecoration.lineThrough : null,
          color: checked ? AppColors.textHint : AppColors.textPrimary,
        ),
      ),
    };
  }
```

- [ ] **Step 2: 运行 flutter analyze 验证**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/pages/note_editor_page.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat(p2b2): 注入自定义 blockComponentBuilders（清单删除线+灰色）"
```

---

### Task 6: TextToolbar 图片按钮接通 + 清单 toggle 完善

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/widgets/editor/text_toolbar.dart`

- [ ] **Step 1: 添加 onImageTap 回调并修改图片按钮**

将 `TextToolbar` 修改为接收 `onImageTap` 回调：

```dart
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

class TextToolbar extends StatelessWidget {
  final EditorState? editorState;
  final VoidCallback? onStyleTap;
  final VoidCallback? onImageTap;

  const TextToolbar({
    super.key,
    this.editorState,
    this.onStyleTap,
    this.onImageTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: AppDimens.editorToolbarHeight,
      decoration: const BoxDecoration(
        color: AppColors.editorToolbarBg,
        border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _ToolbarButton(
            icon: Icons.checklist,
            color: AppColors.editorIconActive,
            onTap: () => _toggleTodoList(context),
          ),
          _ToolbarButton(
            icon: Icons.text_format,
            color: AppColors.primary,
            onTap: onStyleTap,
          ),
          _ToolbarButton(
            icon: Icons.image_outlined,
            color: AppColors.editorIconActive,
            onTap: onImageTap,
          ),
          _ToolbarButton(
            icon: Icons.draw_outlined,
            color: AppColors.editorIconInactive,
            onTap: () => _showSnackBar(context, '手写功能将在后续版本实现'),
          ),
          _ToolbarButton(
            icon: Icons.mic_none,
            color: AppColors.editorIconInactive,
            onTap: () => _showSnackBar(context, '录音功能将在后续版本实现'),
          ),
        ],
      ),
    );
  }

  void _toggleTodoList(BuildContext context) {
    final es = editorState;
    if (es == null) return;
    final selection = es.selection;
    if (selection == null) return;

    final node = es.getNodeAtPath(selection.end.path);
    if (node == null) return;

    final transaction = es.transaction;

    if (node.type == TodoListBlockKeys.type) {
      // todo_list → paragraph（保留文字）
      final delta = node.delta ?? Delta();
      final paragraph = paragraphNode(delta: delta);
      transaction
        ..insertNode(node.path, paragraph)
        ..deleteNode(node)
        ..afterSelection = Selection.collapsed(
          Position(path: node.path, offset: delta.toPlainText().length),
        );
    } else if (node.delta != null && node.delta!.toPlainText().isEmpty) {
      // 空段落 → 原地替换为 todo_list
      final newNode = todoListNode(checked: false);
      transaction
        ..insertNode(node.path, newNode)
        ..deleteNode(node)
        ..afterSelection = Selection.collapsed(
          Position(path: node.path, offset: 0),
        );
    } else {
      // 非空段落 → 下方插入新 todo_list
      final nextPath = node.path.next;
      final newNode = todoListNode(checked: false);
      transaction
        ..insertNode(nextPath, newNode)
        ..afterSelection = Selection.collapsed(
          Position(path: nextPath, offset: 0),
        );
    }
    es.apply(transaction);
  }

  void _showSnackBar(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}

class _ToolbarButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final VoidCallback? onTap;

  const _ToolbarButton({
    required this.icon,
    required this.color,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(icon, color: color, size: 24),
      onPressed: onTap,
    );
  }
}
```

- [ ] **Step 2: 更新 note_editor_page.dart 中 TextToolbar 的调用**

在 `_buildBottomBar` 方法中给 `TextToolbar` 传入 `onImageTap`：

```dart
  Widget _buildBottomBar(NoteEditorNotifier notifier, NoteEditorState state) {
    if (state.isEditing) {
      return TextToolbar(
        editorState: notifier.editorState,
        onStyleTap: () => _showStylePicker(notifier),
        onImageTap: () => notifier.insertImage(),
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

- [ ] **Step 3: 运行 flutter analyze 验证**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/editor/text_toolbar.dart lib/pages/note_editor_page.dart`
Expected: No issues found

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/text_toolbar.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat(p2b2): 工具栏图片按钮接通+清单toggle逻辑完善"
```

---

### Task 7: 全量验证 + 平台配置

**Files:**
- 无新建/修改文件，验证任务

- [ ] **Step 1: 运行全部测试**

Run: `cd code/HuaweiNoteFlutter && flutter test`
Expected: All tests PASS

- [ ] **Step 2: 运行 flutter analyze 全项目**

Run: `cd code/HuaweiNoteFlutter && flutter analyze`
Expected: No issues found

- [ ] **Step 3: iOS 平台配置验证**

检查 `ios/Runner/Info.plist` 是否需要添加相册权限描述。如果 image_picker 的 iOS setup 需要在 Info.plist 中添加：

```xml
<key>NSPhotoLibraryUsageDescription</key>
<string>需要访问相册以插入图片到笔记</string>
<key>NSCameraUsageDescription</key>
<string>需要使用相机拍照插入到笔记</string>
```

- [ ] **Step 4: macOS 平台配置验证**

检查 `macos/Runner/DebugProfile.entitlements` 和 `macos/Runner/Release.entitlements` 是否需要添加文件访问权限：

```xml
<key>com.apple.security.files.user-selected.read-only</key>
<true/>
```

- [ ] **Step 5: 运行 flutter build 验证编译通过**

Run: `cd code/HuaweiNoteFlutter && flutter build ios --no-codesign --debug 2>&1 | tail -5`（或 macOS: `flutter build macos --debug`）
Expected: 编译成功

- [ ] **Step 6: Commit（如有平台配置改动）**

```bash
git add code/HuaweiNoteFlutter/ios/Runner/Info.plist code/HuaweiNoteFlutter/macos/Runner/DebugProfile.entitlements code/HuaweiNoteFlutter/macos/Runner/Release.entitlements
git commit -m "feat(p2b2): 添加 iOS/macOS 相册和相机权限配置"
```

---

## 验证清单

完成所有 Task 后手动验证：

- [ ] 图片选择：点工具栏图片按钮 → 弹出系统相册选择器
- [ ] 图片插入：选图后图片出现在编辑器中
- [ ] 图片持久化：保存笔记 → 退出 → 重新打开 → 图片仍显示
- [ ] 新建笔记图片：新笔记未保存状态点图片 → 先自动保存 → 再选图
- [ ] 清单外观：checked 时文字显示删除线 + 灰色
- [ ] 清单 toggle：点工具栏清单按钮当前是 todo → 变回普通段落
- [ ] 清单 toggle：普通段落空行 → 变为 todo_list
- [ ] 孤儿清理：插入图片 → 删除图片节点 → 保存 → 检查图片目录中文件已删除
