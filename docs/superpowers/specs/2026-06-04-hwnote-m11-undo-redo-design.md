# HwNote M11 撤销 / 重做 设计稿

**日期：** 2026-06-04
**里程碑：** M11（PRD §13 第 4/4 项，最后一个 §13 扩展）
**前置：** M1-M10 已完成（HEAD `369ca6a`，85 单测全绿，13 条 M10 真机走查通过）
**PRD 锚点：** `docs/superpowers/specs/2026-05-22-hwnote-design.md` §13.1（line 461）+ line 484 + line 497

---

## 0. 范围与决策摘要

**核心决策（brainstorm 阶段全部确认）：**

| # | 决策点 | 选择 |
|---|---|---|
| 1 | 最小撤销单元 | **块级 + 文本防抖 800ms** — 块的增/删/移/转 = 各 1 步；TextBlock 文字编辑停顿 800ms 落 1 步；样式应用单独 1 步 |
| 2 | 栈生命周期边界 | **onSaveSuccess 清栈** — saveNote 成功 → `history.clear()`；onPause 触发的 save 也走此路径 |
| 3 | 按钮位置 | **顶部 AppBar 右侧 2 个 MenuItem**（对齐华为 Note 体验，不挤底部 5 键工具栏） |
| 4 | 手写 Overlay 是否合栈 | **不接管，手写保持独立** — M6 ArrayDeque<Action> 完全保留，与 EditHistoryManager 物理隔离 |
| 5 | 栈深度上限 | **50 步** — FIFO 淘汰最老条目 |
| 6 | 删图/删音文件清理 | **删块不动文件，saveNote 后异步扫遗孤清理** — undo 零成本可复活 |
| 7 | Command 实现风格 | **Inverse Op（逆操作）** — 每个 Command 自带 apply() / revert() |

**范围内：** 文本输入（防抖）、样式（B/I/U/S/Size/Color/H1/H2）、块增删（图片/清单/语音/手写块）、块顺序变更、清单↔文本转换。

**范围外：** 手写 Overlay 内笔画级 undo（M6 已内置且与外栈隔离）、列表页 / 分类 / 软删除回收站的撤销（M9 已有 30 天回收站语义）。

---

## 1. 架构总览

**核心新组件：** `model/history/EditHistoryManager.kt` —— 持有 undo + redo 两个 `ArrayDeque<Command>`，上限 50，对外暴露 `push(cmd)` / `undo()` / `redo()` / `clear()` / `canUndo()` / `canRedo()` 和 `listener: (canUndo: Boolean, canRedo: Boolean) -> Unit`。

**Command 模型：** `interface Command { fun apply(); fun revert(); val label: String }` —— 每个具体 Command 类自带 `apply()` / `revert()`，构造时已绑定到 `EditorPresenter` 引用 + 目标 blockId / index / 操作数据。**Inverse Op 风格**：不存整块快照，只存"如何撤回"的最小信息。

**Presenter 接入：** `EditorPresenter` 持有 `EditHistoryManager` 实例。所有"会改 currentBlocks 或 spans"的方法（toggleInline / toggleSize / pickColor / toggleHeading / insertImageBlocksAtFocus / insertChecklistBlockAtFocus / insertAudioBlockAtFocus / removeBlock / replaceBlockText[防抖]）改成"先调 silent mutator + 再 history.push(cmd)"两步。

**关键不变量：** `apply()` / `revert()` 都经过"低层 silent mutator"（如 `silentInsertBlock(index, block)` / `silentRemoveBlock(blockId)` / `silentApplySpan(blockId, range, span)`），这些 silent mutator **不调用 history.push**，避免无限递归。

**工具栏：** `activity_note_editor.xml` 顶部 AppBar 右侧加 2 个 `MenuItem`（ic_undo / ic_redo），`NoteEditorActivity.onCreateOptionsMenu` 注册；通过 `manager.listener` 同步 enabled 状态触发 `invalidateOptionsMenu()`。

**生命周期：**
- `EditorPresenter.loadNote()` 完成后实例化（一笔记一栈，自动满足"换笔记新栈"语义）
- `saveNote()` 成功路径末尾 `history.clear()` 实现"跨保存清栈"
- Activity onCreate（含 process death 重建）= 新 Presenter = 新 Manager = 空栈

**文件遗孤清理：** `NoteRepository.save(note)` 后 `lifecycleScope.launch(Dispatchers.IO)` 异步扫描 `notes/<id>/images/` + `notes/<id>/audio/`，与 currentBlocks 中引用的 fileName 差集 → `file.delete()`。**幂等容错**（IOException 仅 log）。

**手写：** 不接管。M6 的 HandwritingOverlayView 内部 ArrayDeque<Action> 完全保留，与 EditHistoryManager 无关。Overlay 退出回写 HandwritingBlock 的动作**不入** EditHistoryManager（保持 M6 既有契约）。

---

## 2. 组件清单

### 2.1 新增文件（5 个）

| 文件 | 职责 |
|---|---|
| `model/history/Command.kt` | `interface Command { fun apply(); fun revert(); val label: String }` |
| `model/history/EditHistoryManager.kt` | undo + redo `ArrayDeque<Command>` (cap=50)、`push/undo/redo/clear/canUndo/canRedo` + listener |
| `model/history/commands/BlockCommands.kt` | `AddBlockCommand` / `RemoveBlockCommand` / `MoveBlockCommand` / `ReplaceBlockCommand`（清单↔文本转换） |
| `model/history/commands/StyleCommands.kt` | `ApplySpanCommand`（B/I/U/S/Size/Color）/ `ApplyHeadingCommand`（H1/H2 段落级） |
| `model/history/commands/TextCommands.kt` | `ReplaceTextCommand`（blockId, beforeText+beforeSpans, afterText+afterSpans）— 文本防抖落栈 |

### 2.2 Presenter 改造（同文件 EditorPresenter.kt）

- 新增 `val history = EditHistoryManager()`（loadNote 完成后实例化）
- 新增 silent mutator 私有方法（**不入栈**，apply/revert 共用入口）：
  - `silentInsertBlock(index: Int, block: Block)` / `silentRemoveBlock(blockId: String)`
  - `silentMoveBlock(from: Int, to: Int)` / `silentReplaceBlock(blockId: String, newBlock: Block)`
  - `silentApplySpan(blockId, range, span)` / `silentRemoveSpan(blockId, range, spanType)`
  - `silentReplaceText(blockId, text, spans)`
  - `silentRequestFocus(blockId, cursorIndex)`
- 现有公共方法改造为"调 silent mutator + history.push(对应 Command)"两步
- 新增公共入口：
  - `undo()` → `flushPendingTextEdits()` + `history.undo()`
  - `redo()` → `flushPendingTextEdits()` + `history.redo()`
  - `recordTextEdit(blockId, before, after)` → push ReplaceTextCommand
  - `flushPendingTextEdits()` → 遍历当前所有 TextBlockView 调 `flushPendingTextEdit()`，把防抖窗口未落栈的变更立即落栈
  - `stopAllPlayback()` 已存在，不动

### 2.3 TextBlockView 改造

- 现有 TextWatcher 增加防抖逻辑：
  - 每次 onTextChanged 取消上一次 `handler.postDelayed`
  - 若是防抖窗口第一次变更 → 记 `beforeText` / `beforeSpans` 快照
  - `handler.postDelayed(800ms)` 内调 `presenter.recordTextEdit(blockId, before, after)` + 清防抖状态
- 失焦时（`onFocusChange(false)`）立即 flush
- 新增 `internal fun flushPendingTextEdit()` 供 Presenter 在 save / undo / redo / 块操作前调用

### 2.4 Activity 改造（NoteEditorActivity.kt）

- 重写 `onCreateOptionsMenu(menu)` —— inflate `R.menu.menu_editor`
- 实现 `onOptionsItemSelected`：分发 `R.id.action_undo` → `presenter.undo()`、`R.id.action_redo` → `presenter.redo()`
- 实现 `onPrepareOptionsMenu`：从 `presenter.history.canUndo()/canRedo()` 同步 enabled
- onCreate 完成后注册 `presenter.history.listener = { _, _ -> invalidateOptionsMenu() }`
- onPause 已有路径在 `saveNote` 之前先 `presenter.flushPendingTextEdits()`

### 2.5 新增资源

| 路径 | 用途 |
|---|---|
| `res/drawable/ic_undo.xml` | Material vector 撤销图标 |
| `res/drawable/ic_redo.xml` | Material vector 重做图标 |
| `res/menu/menu_editor.xml` | 2 个 MenuItem（action_undo / action_redo，icon + showAsAction=always） |
| `res/values/strings.xml` 新增 | `action_undo_cd` = "撤销"、`action_redo_cd` = "重做" |

### 2.6 NoteRepository 微调

- `save(note: Note)` 末尾追加 `cleanOrphanFiles(noteId, note)` 调用
- 新增 `private fun cleanOrphanFiles(noteId: Long, note: Note)`：
  - 集合 1：扫 `fileStorage.imageDir(noteId)` 的所有文件名
  - 集合 2：扫 `fileStorage.audioDir(noteId)` 的所有文件名
  - 引用集：note.content 中所有 ImageBlock.fileName + AudioBlock.fileName
  - 差集 = 遗孤 → `file.delete()`
  - 任何 IOException 仅 log，不抛
- 调用位置：`lifecycleScope.launch(Dispatchers.IO) { ... }` 在 saveNote 成功后

---

## 3. 数据流

### 流 A：用户操作 → 入栈

```
用户点 [B] 加粗
  → Activity.onStyleClicked (打开 StylePickerBottomSheet)
  → BottomSheet 内 B 按钮 click
  → presenter.toggleInline(SpanType.BOLD)
       ├── 1. 计算 targetRange (focusedTextBlock.selection)
       ├── 2. silentApplySpan(blockId, range, BOLD)   ← 真实改 spans + 刷 TextBlockView
       └── 3. history.push(ApplySpanCommand(presenter, blockId, range, BOLD))
                ↓
            EditHistoryManager.push:
               redoStack.clear()    ← 新操作清 redo
               undoStack.addLast(cmd)
               if (undoStack.size > 50) undoStack.removeFirst()
               listener?.invoke(canUndo=true, canRedo=false)
                ↓
            Activity 收到回调 → invalidateOptionsMenu() → undo enabled / redo disabled
```

### 流 B：用户点 ↶ undo

```
用户点顶部 ↶
  → Activity.onOptionsItemSelected(R.id.action_undo)
  → presenter.undo()
       ├── flushPendingTextEdits()       ← 兜底防抖文本先落栈
       └── history.undo()
              ├── if (undoStack.isEmpty) return
              ├── cmd = undoStack.removeLast()
              ├── try cmd.revert()       ← 内部调 presenter.silentRemoveSpan(...) 等
              ├── redoStack.addLast(cmd)
              └── listener?.invoke(canUndo=..., canRedo=true)
                  ↓
              Activity invalidateOptionsMenu
```

### 流 C：文本防抖落栈

```
TextBlockView TextWatcher.onTextChanged 每次回调:
  ├── 取消上一次 800ms handler.postDelayed
  ├── 若是防抖窗口的第一次变更 → 记 beforeText / beforeSpans 快照
  └── handler.postDelayed(800ms) {
         afterText/afterSpans = 当前 EditText 内容
         if (before != after) presenter.recordTextEdit(blockId, before, after)
            → push ReplaceTextCommand(presenter, blockId, before, after)
         清防抖状态
      }

特殊触发立即 flush（避免落栈滞后丢操作）：
  ├── 用户失焦该 TextBlock (onFocusChange false)
  ├── 用户切到别的 TextBlock
  ├── presenter.saveNote() 入口（保存前必须 flush，否则最后一段字会丢 undo）
  ├── 用户点 undo / redo 按钮
  └── 用户主动插入/删除其他块（让历史顺序保持线性）
```

### 流 D：保存清栈 + 遗孤清理

```
用户返回 / onPause
  → saveNote()
      ├── flushPendingTextEdits()         ← 流 C 的兜底
      ├── presenter.collectCurrentNote()
      ├── repository.save(note)           ← 成功
      ├── history.clear()                 ← 跨保存清栈，按钮立即灰
      └── lifecycleScope.launch(IO) {
            repository.cleanOrphanFiles(noteId, note)
          }
```

### 流 E：换笔记 / Activity 重建

```
进入新笔记编辑器 → onCreate → presenter.loadNote(id)
  → EditorPresenter 内 new EditHistoryManager()
  → 空栈，按钮初始 disabled
（process death + savedInstanceState 路径不恢复 history —— 用户切后台再回来，按钮重置为空栈是可接受的简化语义）
```

---

## 4. 边界 + 错误处理

### 4.1 入栈守卫

| 场景 | 处理 |
|---|---|
| silent mutator 内抛异常（如 blockId 不存在） | 不入栈、不动 history，Toast 提示"操作失败"，栈保持一致 |
| `Command.revert()` 内目标 block 已不存在 | revert 内 null check，no-op + log；cmd 仍 push 进 redoStack（保持配对），后续 redo 时再 no-op |
| revert 抛异常 | catch 后弹 Toast "撤销失败"，丢弃该 cmd（不入 redoStack），栈状态仍一致 |
| undo 期间用户点击其他按钮 | undo 同步执行（毫秒级、无 IO），不会异步竞态 |
| 文本防抖 800ms 未到、用户点 undo | 流 C 内显式 `flushPendingTextEdits()` 兜底先落栈再 undo |
| 防抖未到、用户点 redo | 同上 flush；防抖产生的 ReplaceText 先入 undoStack，redo 仍走原 redoStack（防抖 push 时 redoStack 已清，所以 redo 立即变空 = disabled，正确语义） |

### 4.2 撤销越界

- `canUndo() = undoStack.isNotEmpty()` —— 按钮 enabled 与之绑定，灰按钮点不动
- 防御性：`undo()` / `redo()` 入口都先判空 return，多一层保险

### 4.3 栈超限淘汰

- `push()` 内 `if (undoStack.size > 50) undoStack.removeFirst()`
- 最老那条出栈 = 真的没法回了；无 UI 提示（用户感知"撤销到底了"自然）

### 4.4 焦点 / 光标恢复

- `ApplySpanCommand.revert()` 后调 `silentRequestFocus(blockId, range.last)`
- `AddBlockCommand.revert()`（即移除新加块）后焦点回到 index-1 的 TextBlock 末尾；若 index=0 焦点到 index=0 的新首块
- `RemoveBlockCommand.apply()` 取消该块焦点；`.revert()`（即恢复）后焦点回到该块末尾
- `ReplaceTextCommand.revert()` 后焦点回到该 block、光标放 `beforeText.length`
- 统一辅助：`presenter.silentRequestFocus(blockId, cursorIndex)`，找到 BlockView 调 `focusEditAt(cursorIndex)`；BlockView 不存在则 no-op

### 4.5 跨 Activity 边界（清栈时机）

- `onSaveSuccess` 是唯一清栈入口；onPause 触发的 saveNote 也走这条
- 切后台再回前台 = 同一个 EditorPresenter 实例 = 同一个 EditHistoryManager；若期间触发了 onPause → save 则栈已空
- process death 重建：onCreate 新 Presenter + 新 Manager = 空栈。**savedInstanceState 不持久化 history**（YAGNI；用户主动 kill 进程后丢撤销栈可接受）

### 4.6 遗孤清理失败

- `cleanOrphanFiles` 在 IO 线程异步跑、catch 所有 IOException、只 log
- 失败不影响 save 主路径，下次 save 再清

### 4.7 手写 Overlay 隔离

- HandwritingOverlayView 内 `addStroke` / `eraseStroke` 完全不经 EditHistoryManager
- 顶部 ↶ 按钮点击不影响 Overlay 内部栈
- Overlay 退出时把 strokesPng / strokesJson 写回 `currentBlocks` 的 HandwritingBlock —— 这个"写回"动作**不入** EditHistoryManager（Overlay 退出即视为"用户已确认"，保持 M6 既有契约）

---

## 5. 测试策略

### 5.1 单元测试（纯 JVM, JUnit 5）

**`EditHistoryManagerTest`** —— Manager 自身行为（不依赖 Android）：
- `push 后 canUndo=true / canRedo=false`
- `push 多次再 undo 一次 → undoStack -1 / redoStack +1`
- `undo → redo 后 stack 状态对称`
- `push 后 redoStack 自动清空`
- `超过 50 上限时最老那条出栈，新 push 进尾`
- `clear() 后两栈都空 + listener 触发 (false, false)`
- `空栈 undo() / redo() no-op 不抛`
- Listener 调用次数与状态变更对齐

**`BlockCommandsTest`** —— 用假 Presenter（接口 mock / 简化实现）：
- `AddBlockCommand.apply → revert 后 blocks 恢复原序`
- `RemoveBlockCommand.apply → revert 后 block 复活到原 index`
- `MoveBlockCommand.apply(from=2, to=0) → revert 后回 index=2`
- `ReplaceBlockCommand`（文本↔清单转换）apply / revert 对称

**`StyleCommandsTest`**：
- `ApplySpanCommand(BOLD, range) apply → spans 加 BOLD；revert → spans 移除 BOLD`
- `ApplyHeadingCommand(H1) apply → blockType=H1；revert → 原 type`
- 多 span 叠加（先 BOLD 后 ITALIC）→ 逆序 revert 还原

**`TextCommandsTest`**：
- `ReplaceTextCommand(before="hello", after="hello world") apply → text=after；revert → text=before`
- spans 同步替换

预期增量：约 20 个新单测，总数 85 → ~105。

### 5.2 不写自动化的部分（沿"写完即手测"约定）

- 防抖 800ms 落栈（需要真实 TextWatcher + Handler，Robolectric 可做但 ROI 低）
- 顶部 MenuItem enabled 同步刷新（需要真实 Toolbar）
- 焦点恢复（需要真实 View 树）
- 文件遗孤清理（需要真实 fileStorage IO）
- 这些都进真机走查清单

### 5.3 真机走查清单（≥10 条，验收门）

1. 输入"hello" → ↶ → 文字消失为空块；↷ → "hello"回来
2. 输入一段文字、暂停 1s 后再输入 → 两次都进栈，连按 ↶ 两次能分两段回退
3. 加粗某段 → ↶ → 加粗撤销；↷ → 加粗回来
4. 插入图片 → ↶ → 图片消失；↷ → 图片回来
5. 录音生成 AudioBlock → ↶ → AudioBlock 消失；↷ → 回来
6. 删除图片块 → ↶ → 图片块复活（图片正确显示，文件未丢）
7. 转清单块 ↔ 文本块 → ↶/↷ 切回
8. 应用 H1 → ↶ → H1 退回普通段
9. 切颜色到红色 → ↶ → 颜色恢复
10. 连续 51 次操作 → 最老那条无法 undo（栈满淘汰）
11. 保存后返回再进笔记 → ↶ 按钮灰（清栈生效）
12. 切到别的笔记再回来 → 撤销栈是新的（不会撤掉旧笔记的操作）
13. 手写 Overlay 内画 → 退出 → 编辑器顶部 ↶ 不影响手写块（隔离生效）
14. 删一张图后保存 → 重新进笔记 → 检查 `notes/<id>/images/` 该 fileName 已被遗孤清理

---

## 6. 不变量小结

- 所有"会改 currentBlocks/spans"的入口经过 silent mutator
- silent mutator 不入栈
- 公共入口 = silent mutator + history.push 配对
- onSaveSuccess 是唯一清栈点
- Manager 50 步 FIFO 淘汰
- 手写 Overlay 与外层 history 物理隔离
- 防抖 800ms，特殊触发立即 flush（失焦 / 切块 / save / undo / redo）
- revert 异常 catch + Toast，不破坏栈一致性

---

## 7. 与 PRD 的对齐

| PRD 条目 | 本设计如何覆盖 |
|---|---|
| §13.1 line 461「文本输入」 | TextCommands.ReplaceTextCommand + 防抖 800ms |
| §13.1 line 461「样式」 | StyleCommands.ApplySpanCommand / ApplyHeadingCommand |
| §13.1 line 461「块增删」 | BlockCommands.AddBlockCommand / RemoveBlockCommand |
| §13.1 line 461「图片清单语音操作」 | 同上块增删（涵盖 ImageBlock/ChecklistBlock/AudioBlock） |
| line 484「EditHistoryManager 维护 undo/redo 栈」 | §1 / §2 EditHistoryManager.kt |
| line 484「操作模型按 command pattern」 | §1 Command 接口 + Inverse Op 风格 |
| line 484「保存时清栈」 | §1 流 D + §4.5 onSaveSuccess 唯一清栈点 |

---

## 8. 工作量估算

- 7 个核心实现任务（Command 接口 + Manager + 3 个 Command 文件 + Presenter 接入 + TextBlockView 防抖 + Activity menu 接入 + Repository 遗孤清理）
- 2 个测试任务（Manager 单测 + Commands 单测）
- 1 个真机走查任务
- ≤ 15 任务实施计划（下一步 writing-plans 阶段细化）
- 预估 1-2 天

---

## 9. 不动的东西（明确边界）

- M1-M10 所有代码、布局、资源 —— 一行不改，除非 §2 明确列出
- HandwritingOverlayView / BrushPainter / StrokeEraser —— 完全保留
- M10 AudioRecorder / AudioPlayer / AudioRecordingBottomSheet —— 完全保留
- M9 分类系统 / 软删除回收站 / DeleteConfirmBottomSheet —— 完全保留
- 数据层 schema —— 不动（DB 仍 v2，无 v3 迁移）
- NoteJson 序列化格式 —— 不动（撤销栈纯内存，不持久化）

---

**审稿状态：** brainstorm § 1-5 五节呈现已用户逐节确认，进入 spec 写作 + 自审 + 用户审阅环节。

---

## 10. 交付实况（2026-06-04 收尾回填）

**状态：** ✅ 全部交付。HEAD `4a3e2e5`，21 commit（含 2 docs），114 单测全绿，13 条真机走查 + 1 条文件遗孤 adb 验证全过。

### 10.1 与设计对齐情况

| 决策 / 组件 | 设计预期 | 实际交付 | 一致性 |
|---|---|---|---|
| 1. 块级 + 文本 800ms 防抖 | TextBlock 防抖落 1 步 ReplaceText | 实现一致：`TextBlockView` `Handler.postDelayed(800ms)` + `flushPendingTextEdit()` + `setOnFocusChangeListener` 焦失也 flush | ✅ |
| 2. onSaveSuccess 唯一清栈入口 | saveNote 成功 → history.clear() | 实现一致 + 加 `newId > 0L` gate（避免 insert 失败误清，spec §4.5） | ✅（加固） |
| 3. 顶部 AppBar 2 MenuItem | menu_editor.xml + invalidateOptionsMenu | 实现一致 + `applyMenuIconAlpha(255/102)` 视觉灰态（Drawable.mutate 防 ConstantState 污染） | ✅（加细节） |
| 4. 手写 Overlay 隔离 | M6 ArrayDeque 完全保留 | 实现一致，0 行 Overlay 代码改动 | ✅ |
| 5. 双栈 cap 50 FIFO | EditHistoryManager | 实现一致 + listener 在四种入口（push/undo/redo/clear）触发；undo/redo `revert/apply` 异常吞咽不污染对面栈 | ✅（加固） |
| 6. 删块不动文件 + 异步清孤 | NoteRepository.cleanOrphanFiles | 实现一致：`runCatching{}.onFailure{Log.w}` 容错 + `noteId<=0` early return + sealed when 表达式形式（编译期穷尽） | ✅（加固） |
| 7. Inverse Op 风格 | 每 Command 自带 apply/revert | 实现一致，但 `RemoveBlockCommand` / `ReplaceBlockCommand` 额外引入 `preSnapshot` / `preOldBlock` 构造参数支持"先 mutate 再 push"模式（零成本入栈） | ✅（扩展） |

### 10.2 设计未覆盖、实施中新增的概念

| 新增 | 起因 | 落点 |
|---|---|---|
| **`CompositeCommand`** | T7 spec reviewer 走查发现"插图/录音/转清单/图片加载失败"4 个站点 push 多个 cmd，导致用户 1 操作需多次 ↶ 才能完全撤销；与"块级 = 1 步"决策违背 | `model/history/CompositeCommand.kt`（list 包装 + apply 顺序 + revert 反序 + 防御性 toList 拷贝 + KDoc 显式说明"非原子"边界） |
| **`suppressDebounceWhile { block }` 守卫** | T8 实施中发现 silent 路径调 `view.edit.setText` 会触发 TextWatcher 自激入栈无限循环；T8-fix 发现 bind() 加载笔记的初始 setText 800ms 后冒幻影 ReplaceText push 违反"空栈"语义 | `TextBlockView` 公开 `suppressDebounceWhile { ... }` try/finally 守卫；silent 路径与 bind 都必须包 |
| **`presnapshot` / `preOldBlock` 构造参数** | T2 实施中 Presenter 已经手工 silentRemove 后才能拿到旧 block 引用，Command 构造时回拉成本高 | `RemoveBlockCommand(idx, preSnapshot)` / `ReplaceBlockCommand(idx, newBlock, preOldBlock)` 让"已 mutate 后再 push"零成本（注：apply 必须 `block.copy()` 深拷贝存 snapshot，否则 revert 拿到的是后续被 mutate 的引用） |
| **`presenter.flushPendingTextEdits()` 在 onPause** | T9 实施中发现 onPause 触发 saveNote 时，800ms 期内未落栈的文本编辑会丢失 | Activity `onPause` 在 saveNote 之前调，把暂停期编辑 flush 入栈再持久化 |
| **`is Block.TextBlock, is Block.ChecklistBlock -> Unit` 替代 `else -> Unit`** | T10 code reviewer 指出 sealed `when` 用 `else` 失去编译期穷尽校验，新增 Block 子类会被静默漏掉 | `cleanOrphanFiles` 内 when 改显式列举所有 sealed 子类 |

### 10.3 测试增量（设计 §5.1 预期对照）

| 测试文件 | 设计预期项数 | 实际项数 | 增项原因 |
|---|---|---|---|
| `EditHistoryManagerTest` | 8 | 10 | T1-fix 补 undo/redo 异常路径（revert/apply 抛异常不污染对面栈） |
| `BlockCommandsTest` | 6 | 8 | T2-fix 补 RemoveBlock snapshot 深拷贝 + AddBlock revert 焦点 |
| `StyleCommandsTest` | 4 | 4 | 一致（含焦点 / 光标位置断言由 T3-fix 补） |
| `TextCommandsTest` | 4 | 4 | 一致 |
| `CompositeCommandTest` | — | 3 | T7-fix 引入 CompositeCommand 时新增（apply 顺序 / revert 反序 / 空 list 抛异常） |
| **合计** | 22 | **29** | +7（T1-fix +2 / T2-fix +2 / CompositeCommand +3） |

### 10.4 工作量实况

- 设计预估：1-2 天 / ≤15 任务
- 实际：11 任务串行 + 5 个 fix/refactor 修补 commit（T2-fix / T3-fix / T7-fix×2 / T8-fix / T9-fix / T10-fix×2）
- 21 commit 合计（19 code + 2 docs），约 1.5 天

### 10.5 关键 commit 索引（与 STATUS.md M11 章节一致）

`bf4bcf7`(T1) → `bf1932e`(T1-fix) → `47e294f`(T2) → `475d5cd`(T2-fix) → `790807c`(T3) → `2ed6248`(T3-fix) → `0ef9c64`(T4) → `bc311df`(T5) → `e012534`(T6) → `72a4b24`(T7) → `13c745a`(T7-fix) → `280779f`(T7-fix-polish) → `1e1eeae`(T8) → `534390f`(T8-fix) → `86a6fd2`(T9) → `9beae52`(T9-fix) → `d6b67c2`(T10) → `837aa7b`(T10-fix-gate) → `2348fa0`(T10-fix-import) → `4a3e2e5`(收尾 docs)

详见 `docs/superpowers/STATUS.md` "## M11 完成详情（2026-06-04）" 章节。
