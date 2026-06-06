# Phase 2B-2 设计文档 — 图片块插入 + 清单块完善

> 对标 Android M5 ImageBlockView + ChecklistBlockView

**目标：** 让编辑器的图片插入和清单交互完整可用。利用 appflowy_editor 6.0.0 内置的 `ImageBlockComponentBuilder` 和 `TodoListBlockComponentBuilder`，补齐图片拾取/压缩/本地存储/工具栏接通，以及清单外观定制。

**技术栈：** Flutter + appflowy_editor 6.0.0 + image_picker + flutter_image_compress + path_provider

---

## 1. 架构决策

### 1.1 图片块策略

appflowy_editor 内置 `ImageBlockComponentBuilder` + `ResizableImage`，支持三种图片源：
- 网络 URL（`isURL(src)` → `Image.network`）
- Base64（`isBase64(src)` → `Image.memory`）
- **本地文件路径**（else → `Image.file(File(src))`）

我们采用本地文件路径方案：
1. 用户选择图片（相册/拍照）
2. 压缩到长边 ≤1920px、JPEG 85% 质量
3. 存储到 `{appDocDir}/notes/{noteId}/images/{uuid}.jpg`
4. 插入 `imageNode(url: absoluteFilePath)` 到文档

**关键：** 新建笔记（noteId=0）时需先保存笔记获取 id，再存图片。沿 Android `ensureNoteSavedAndThen` 模式。

### 1.2 清单块策略

appflowy_editor 内置 `TodoListBlockComponentBuilder` 已提供：
- Checkbox toggle（checked/unchecked）
- 文本编辑（Delta 格式）
- Enter 键新建下一项（`insertNewLineAfterTodoList` shortcut）
- Backspace 空项退出为 paragraph（`convertToParagraphCommand`）

我们只需：
1. 自定义外观：checked 时文字加删除线 + 灰色
2. 验证标准快捷键在移动端是否生效（移动端无物理 Enter/Backspace，靠软键盘）
3. 确保工具栏清单按钮的 toggle 逻辑正确（当前块是 todo_list → 转回 paragraph）

### 1.3 不做的事

- ❌ 自定义 BlockComponentBuilder（用内置即可）
- ❌ 图片裁剪/编辑
- ❌ 多选图片（一次选一张，可连续选多次）
- ❌ 拍照功能（移动端 image_picker 统一入口已含相机）
- ❌ 图片圆角定制（appflowy_editor 默认渲染已足够美观）

---

## 2. 图片块详细设计

### 2.1 依赖

```yaml
dependencies:
  image_picker: ^1.1.2
  flutter_image_compress: ^2.3.0
```

### 2.2 文件存储

新建 `lib/utils/note_file_storage.dart`：

```
class NoteFileStorage {
  static Future<Directory> imageDir(int noteId) → {appDocDir}/notes/{noteId}/images/
  static Future<File> saveImage(int noteId, Uint8List bytes) → 生成 uuid.jpg，写入 imageDir，返回 File
  static Future<void> deleteImage(String filePath) → 删除单个图片文件
  static Future<void> deleteNoteDir(int noteId) → 删除整个笔记目录
}
```

目录布局与 Android 一致：`files/notes/<noteId>/images/<uuid>.jpg`

### 2.3 图片压缩

新建 `lib/utils/image_compressor.dart`：

```
class ImageCompressor {
  static Future<Uint8List?> compress(File source, {int maxDimension = 1920, int quality = 85})
}
```

- 使用 `flutter_image_compress` 包
- 长边 ≤1920px，短边等比缩放
- JPEG 85% 质量
- 返回 null 表示压缩失败

### 2.4 图片插入流程

```
用户点工具栏"图片"按钮
  → NoteEditorNotifier.ensureNoteSaved()（新笔记先保存获取 noteId）
  → ImagePicker.pickImage(source: ImageSource.gallery)
  → ImageCompressor.compress(file)
  → NoteFileStorage.saveImage(noteId, compressedBytes)
  → EditorState.transaction.insertNode(path, imageNode(url: savedFile.path))
  → EditorState.apply(transaction)
```

### 2.5 图片删除

appflowy_editor 的 `ImageBlockComponentBuilder` 通过 `showMenu: true` + `menuBuilder` 可以显示自定义菜单。但默认行为是 Backspace/Delete 键即可删除节点。

移动端策略：
- 长按图片弹出"删除"确认
- 或：利用 appflowy_editor 自带的 selection + backspace 删除节点

实施时优先验证默认删除行为是否满足需求，如不满足再加长按删除。

### 2.6 图片文件清理

与 Android `cleanOrphanFiles` 同策略：保存笔记时扫描 imageDir 与文档中实际引用的 url 差集，删除孤儿文件。

在 `NoteEditorNotifier.saveNote()` 成功后调用 `NoteFileStorage.cleanOrphanImages(noteId, document)`。

---

## 3. 清单块详细设计

### 3.1 外观定制

通过 `TodoListBlockComponentBuilder(textStyleBuilder: ...)` 自定义：

```dart
TodoListBlockComponentBuilder(
  textStyleBuilder: (checked) => TextStyle(
    decoration: checked ? TextDecoration.lineThrough : null,
    color: checked ? AppColors.textHint : AppColors.textPrimary,
  ),
)
```

需要在 `AppFlowyEditor` 中显式传入自定义的 `blockComponentBuilders` map，覆盖 todo_list 的 builder。

### 3.2 工具栏清单按钮 toggle 逻辑

当前实现已有 `_insertTodoList` 方法。需增加 toggle 逻辑：
- 当前节点是 `todo_list` → 转为 `paragraph`（保留文字）
- 当前节点是 `paragraph` 且为空 → 原地替换为 `todo_list`
- 当前节点是 `paragraph` 且非空 → 下方插入新 `todo_list`

### 3.3 移动端交互验证

appflowy_editor 的 `insertNewLineAfterTodoList` 是 `CharacterShortcutEvent`（响应 `\n` 字符），移动端软键盘回车即触发。`convertToParagraphCommand` 是 `CommandShortcutEvent`（响应物理按键），移动端需验证 Backspace 是否触发。

如 Backspace 退出清单在移动端不生效，可通过添加自定义 `CharacterShortcutEvent` 或在 `onKeyEvent` 中补充处理。

---

## 4. EditorState 定制

当前 `AppFlowyEditor` 使用默认 `standardBlockComponentBuilderMap`。为了自定义 todo_list 外观，需要：

```dart
AppFlowyEditor(
  editorState: editorState,
  blockComponentBuilders: {
    ...standardBlockComponentBuilderMap,
    TodoListBlockKeys.type: TodoListBlockComponentBuilder(
      textStyleBuilder: (checked) => TextStyle(...),
    ),
  },
)
```

这样既保留所有标准块，又覆盖了 todo_list 的外观。

---

## 5. NoteFileStorage 与 cleanOrphanImages

### 5.1 清理逻辑

```dart
static Future<void> cleanOrphanImages(int noteId, Document document) async {
  final dir = await imageDir(noteId);
  if (!dir.existsSync()) return;
  
  // 递归提取文档中所有 image node 的 url
  final referencedPaths = <String>{};
  void collectImageUrls(Node node) {
    if (node.type == ImageBlockKeys.type) {
      final url = node.attributes[ImageBlockKeys.url] as String?;
      if (url != null) referencedPaths.add(url);
    }
    for (final child in node.children) {
      collectImageUrls(child);
    }
  }
  collectImageUrls(document.root);
  
  // 扫描目录，删除不在引用集中的文件
  for (final file in dir.listSync().whereType<File>()) {
    if (!referencedPaths.contains(file.path)) {
      file.deleteSync();
    }
  }
}
```

### 5.2 调用时机

在 `NoteEditorNotifier.saveNote()` 返回成功后异步调用，不阻塞 UI。

---

## 6. 文件清单

### 新建

| 文件 | 职责 |
|------|------|
| `lib/utils/note_file_storage.dart` | 笔记图片/音频文件目录管理 |
| `lib/utils/image_compressor.dart` | 图片压缩（长边 1920 / JPEG 85） |
| `test/utils/image_compressor_test.dart` | 压缩逻辑单测 |
| `test/utils/note_file_storage_test.dart` | 文件存储单测 |

### 修改

| 文件 | 改动 |
|------|------|
| `pubspec.yaml` | 添加 image_picker + flutter_image_compress |
| `lib/pages/note_editor_page.dart` | 传入自定义 blockComponentBuilders；图片插入入口 |
| `lib/widgets/editor/text_toolbar.dart` | 图片按钮从 SnackBar → 调用图片选择流程；清单按钮增加 toggle 逻辑 |
| `lib/providers/note_editor_provider.dart` | 新增 `ensureNoteSaved()` 方法；saveNote 后调 cleanOrphanImages |

---

## 7. 测试策略

- `image_compressor_test.dart`：压缩逻辑（尺寸计算、边界条件）
- `note_file_storage_test.dart`：目录创建/文件保存/删除/孤儿清理
- 手动验证：图片选择→压缩→插入→显示→保存→重新打开→图片仍在
- 手动验证：清单 Enter 新建项 / 空项 Backspace 退出 / checkbox toggle / 删除线样式

---

## 8. 范围边界

### 包含
- ✅ 图片选择（相册，含相机入口）
- ✅ 图片压缩
- ✅ 本地文件存储
- ✅ 图片节点插入
- ✅ 图片文件孤儿清理
- ✅ 清单外观定制（删除线+灰色）
- ✅ 清单工具栏 toggle 完善
- ✅ 自定义 blockComponentBuilders 注入

### 不包含
- ❌ 图片裁剪/编辑/滤镜
- ❌ 多图批量选择（逐张选）
- ❌ 图片圆角定制
- ❌ 音频块（Phase 2B-3）
- ❌ 手写块（Phase 2B-4）
