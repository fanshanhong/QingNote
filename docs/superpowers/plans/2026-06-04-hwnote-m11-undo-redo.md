# HwNote M11 撤销 / 重做 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在编辑器顶部 AppBar 加一对 ↶/↷ 按钮，实现块级 + 文本防抖 800ms 粒度的撤销 / 重做，跨保存清栈，手写 Overlay 隔离。

**Architecture:** 新增 `model/history/` 包持有 `EditHistoryManager`（双 `ArrayDeque<Command>` 上限 50）与 5 个 Command 子类（Inverse Op 风格）。`EditorPresenter` 抽出"silent mutator"私有入口，原有公共方法改造为"调 silent mutator + push Command"两步；TextBlockView TextWatcher 加 800ms 防抖落 ReplaceTextCommand。Activity 顶部 menu 注册 undo/redo + listener 同步 enabled。`NoteRepository.save` 成功后异步扫遗孤 .jpg/.m4a 删除。

**Tech Stack:** Kotlin / SQLiteOpenHelper / Material 1.12.0 AppBar MenuItem / coroutines / JUnit 5 (Jupiter) for pure-JVM unit tests / Robolectric 4.13 for Android-touching tests / Manual real-device walkthrough.

**Spec:** `docs/superpowers/specs/2026-06-04-hwnote-m11-undo-redo-design.md`

**Build command (用于所有 gradle 任务):**
```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

**Commit 约定:**
- 中文动宾，无 Co-Authored-By trailer
- 仅 `git add <file>` 显式按文件加，不用 `-A` / `.`
- 直接在 master 上推

---

## File Structure

**新增 5 个文件（model 层）：**

```
app/src/main/java/com/fan/hwnote/app/model/history/
  ├── Command.kt                # interface Command { fun apply(); fun revert(); val label: String }
  ├── EditHistoryManager.kt     # 双栈、上限 50、listener
  └── commands/
       ├── BlockCommands.kt     # AddBlockCommand / RemoveBlockCommand / MoveBlockCommand / ReplaceBlockCommand
       ├── StyleCommands.kt     # ApplySpanCommand / ApplyHeadingCommand
       └── TextCommands.kt      # ReplaceTextCommand
```

**新增资源（1 个 + 2 个 string）：**

```
app/src/main/res/menu/menu_editor.xml       # action_undo / action_redo, ic_undo / ic_redo, always
app/src/main/res/values/strings.xml         # +action_undo_cd = "撤销"  +action_redo_cd = "重做"
```

（ic_undo.xml / ic_redo.xml 已存在于 res/drawable/，无需新增）

**改动既有文件（4 个）：**

```
app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt    # 加 silent mutator + history 字段 + 改造既有公共方法
app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt              # TextWatcher 加 800ms 防抖 + flushPendingTextEdit
app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt # onCreateOptionsMenu + onOptionsItemSelected + history.listener + onPause flush + saveNote 成功 history.clear + 触发 cleanOrphan
app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt                  # 新增 cleanOrphanFiles(noteId, note)
```

**新增 5 个测试文件（pure JVM, JUnit 5）：**

```
app/src/test/java/com/fan/hwnote/app/model/history/
  ├── EditHistoryManagerTest.kt
  └── commands/
       ├── BlockCommandsTest.kt
       ├── StyleCommandsTest.kt
       └── TextCommandsTest.kt
```

---

## 任务清单（11 任务串行）

### Task 1: Command 接口 + EditHistoryManager

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/model/history/Command.kt`
- Create: `app/src/main/java/com/fan/hwnote/app/model/history/EditHistoryManager.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/history/EditHistoryManagerTest.kt`

- [ ] **Step 1: 写 Command interface**

`app/src/main/java/com/fan/hwnote/app/model/history/Command.kt`：

```kotlin
package com.fan.hwnote.app.model.history

/**
 * 撤销 / 重做最小单元。Inverse Op 风格：每个 Command 自带 apply() 与 revert()。
 *
 * label 仅用于调试 / 日志，UI 不展示。
 */
interface Command {
    fun apply()
    fun revert()
    val label: String
}
```

- [ ] **Step 2: 写 EditHistoryManagerTest（8 个用例，全部预期 FAIL）**

`app/src/test/java/com/fan/hwnote/app/model/history/EditHistoryManagerTest.kt`：

```kotlin
package com.fan.hwnote.app.model.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditHistoryManagerTest {

    /** 测试用假命令：apply/revert 仅记录调用次数。 */
    private class FakeCommand(override val label: String = "fake") : Command {
        var applied = 0
        var reverted = 0
        override fun apply() { applied++ }
        override fun revert() { reverted++ }
    }

    @Test fun `初始状态两栈都空`() {
        val m = EditHistoryManager()
        assertFalse(m.canUndo())
        assertFalse(m.canRedo())
    }

    @Test fun `push 后可 undo 不可 redo`() {
        val m = EditHistoryManager()
        m.push(FakeCommand())
        assertTrue(m.canUndo())
        assertFalse(m.canRedo())
    }

    @Test fun `undo 后调用 revert 且 cmd 进 redo 栈`() {
        val m = EditHistoryManager()
        val c = FakeCommand()
        m.push(c)
        m.undo()
        assertEquals(1, c.reverted)
        assertFalse(m.canUndo())
        assertTrue(m.canRedo())
    }

    @Test fun `redo 后调用 apply 且 cmd 回 undo 栈`() {
        val m = EditHistoryManager()
        val c = FakeCommand()
        m.push(c)
        m.undo()
        m.redo()
        assertEquals(1, c.applied)  // redo 走 apply
        assertTrue(m.canUndo())
        assertFalse(m.canRedo())
    }

    @Test fun `push 后 redo 栈被清空`() {
        val m = EditHistoryManager()
        m.push(FakeCommand("a"))
        m.undo()
        assertTrue(m.canRedo())
        m.push(FakeCommand("b"))
        assertFalse(m.canRedo())
    }

    @Test fun `超过 50 上限最老条目淘汰`() {
        val m = EditHistoryManager()
        val first = FakeCommand("first")
        m.push(first)
        repeat(50) { m.push(FakeCommand("x$it")) }  // 共 51 push
        // undo 51 次：first 应该已被淘汰，所以只能 undo 50 次
        var count = 0
        while (m.canUndo()) { m.undo(); count++ }
        assertEquals(50, count)
        assertEquals(0, first.reverted)  // first 没机会 revert
    }

    @Test fun `clear 后两栈都空 + listener 触发 false false`() {
        val m = EditHistoryManager()
        var lastCanUndo: Boolean? = null
        var lastCanRedo: Boolean? = null
        m.listener = { u, r -> lastCanUndo = u; lastCanRedo = r }
        m.push(FakeCommand())
        m.undo()                          // 此时 canUndo=false, canRedo=true
        m.push(FakeCommand())              // push 又清 redo
        m.clear()
        assertFalse(m.canUndo())
        assertFalse(m.canRedo())
        assertEquals(false, lastCanUndo)
        assertEquals(false, lastCanRedo)
    }

    @Test fun `空栈 undo 与 redo 是 no-op 不抛`() {
        val m = EditHistoryManager()
        m.undo()  // 不抛
        m.redo()  // 不抛
        assertFalse(m.canUndo())
        assertFalse(m.canRedo())
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.history.EditHistoryManagerTest"
```

Expected: 编译失败 (EditHistoryManager 类不存在)。

- [ ] **Step 4: 写 EditHistoryManager 实现**

`app/src/main/java/com/fan/hwnote/app/model/history/EditHistoryManager.kt`：

```kotlin
package com.fan.hwnote.app.model.history

/**
 * 撤销 / 重做双栈管理器。
 * - 上限 50：超过时淘汰最老条目（队首出队）
 * - push 会清 redo 栈（新操作发生 → 之前 redo 路径作废）
 * - listener 在任一栈大小变化时回调（含 push / undo / redo / clear）
 *
 * 线程模型：所有方法仅主线程调用（Presenter / Activity 调用方保证）。
 */
class EditHistoryManager(private val cap: Int = 50) {

    private val undoStack: ArrayDeque<Command> = ArrayDeque()
    private val redoStack: ArrayDeque<Command> = ArrayDeque()

    /** 状态变化回调。参数：(canUndo, canRedo)。 */
    var listener: ((Boolean, Boolean) -> Unit)? = null

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** 入栈一个新操作（操作真实变更已由调用方完成）。会清空 redo 栈、并按 cap 淘汰最老。 */
    fun push(cmd: Command) {
        redoStack.clear()
        undoStack.addLast(cmd)
        while (undoStack.size > cap) undoStack.removeFirst()
        notifyListener()
    }

    /** 撤销一步。空栈 no-op。revert 抛异常时丢弃该 cmd（不入 redo 栈），保持栈一致。 */
    fun undo() {
        if (undoStack.isEmpty()) return
        val cmd = undoStack.removeLast()
        try {
            cmd.revert()
            redoStack.addLast(cmd)
        } catch (t: Throwable) {
            android.util.Log.w("EditHistoryManager", "undo revert failed: ${cmd.label}", t)
        }
        notifyListener()
    }

    /** 重做一步。空栈 no-op。apply 抛异常时丢弃该 cmd（不入 undo 栈）。 */
    fun redo() {
        if (redoStack.isEmpty()) return
        val cmd = redoStack.removeLast()
        try {
            cmd.apply()
            undoStack.addLast(cmd)
        } catch (t: Throwable) {
            android.util.Log.w("EditHistoryManager", "redo apply failed: ${cmd.label}", t)
        }
        notifyListener()
    }

    /** 清空两栈（用于 onSaveSuccess 等"跨保存清栈"语义）。 */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyListener()
    }

    private fun notifyListener() {
        listener?.invoke(canUndo(), canRedo())
    }
}
```

- [ ] **Step 5: 运行测试确认全过**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.history.EditHistoryManagerTest"
```

Expected: `8 tests, 0 failures`。

- [ ] **Step 6: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/Command.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/EditHistoryManager.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/history/EditHistoryManagerTest.kt && \
git commit -m "feat(m11): 落地 Command 接口与 EditHistoryManager（双栈+50 上限+listener）"
```

---

### Task 2: BlockCommands.kt（4 个块级 Command）

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/model/history/commands/BlockCommands.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/history/commands/BlockCommandsTest.kt`

**前提：** Task 5 才会真正实现 Presenter 的 silent mutator。本 task 用 mutator 接口 + 假实现做单测；Presenter 内的 silent mutator 实际签名见 Task 5。

- [ ] **Step 1: 定义 Mutator 接口（让 Command 与 Presenter 解耦，便于单测）**

`app/src/main/java/com/fan/hwnote/app/model/history/commands/BlockCommands.kt` 开头：

```kotlin
package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.history.Command

/**
 * Block 级别 silent mutator 接口。EditorPresenter 实现这组方法；Command 持有该接口引用便于测试。
 * 实现方保证：这些方法不调用 history.push（避免无限递归）。
 */
interface BlockMutator {
    /** 在 index 处插入 block；index 越界时插到末尾。 */
    fun silentInsertBlock(index: Int, block: Block)
    /** 按 blockId 移除；不存在则 no-op。 */
    fun silentRemoveBlock(blockId: String)
    /** 从 from 移到 to；任一越界则 no-op。 */
    fun silentMoveBlock(from: Int, to: Int)
    /** 替换某 blockId 处的整块为 newBlock；不存在则 no-op。 */
    fun silentReplaceBlock(blockId: String, newBlock: Block)
    /** 把焦点拉到某 blockId（光标可选位置）；不存在或非 TextBlock 则 no-op。 */
    fun silentRequestFocus(blockId: String, cursorIndex: Int = Int.MAX_VALUE)
    /** 查询某 blockId 当前 index；不存在返回 -1。 */
    fun indexOfBlock(blockId: String): Int
    /** 取某 blockId 当前 Block 快照（深拷贝语义由 data class copy 给出）。不存在返回 null。 */
    fun snapshotBlock(blockId: String): Block?
}
```

- [ ] **Step 2: 接着同一文件写 4 个 Command 类**

接在 BlockMutator 之后：

```kotlin
/**
 * 在 index 处插入 block。
 * apply = insert；revert = removeByBlockId。
 */
class AddBlockCommand(
    private val mutator: BlockMutator,
    private val index: Int,
    private val block: Block,
) : Command {
    override val label = "AddBlock(${block.id}@$index)"
    override fun apply() {
        mutator.silentInsertBlock(index, block)
        mutator.silentRequestFocus(block.id)
    }
    override fun revert() {
        mutator.silentRemoveBlock(block.id)
    }
}

/**
 * 移除某 block。需要在 apply 前抓快照（Block 数据 + 当前 index）以便 revert 还原。
 */
class RemoveBlockCommand(
    private val mutator: BlockMutator,
    private val blockId: String,
) : Command {
    override val label = "RemoveBlock($blockId)"
    private var snapshot: Block? = null
    private var savedIndex: Int = -1
    override fun apply() {
        snapshot = mutator.snapshotBlock(blockId)
        savedIndex = mutator.indexOfBlock(blockId)
        mutator.silentRemoveBlock(blockId)
    }
    override fun revert() {
        val s = snapshot ?: return
        mutator.silentInsertBlock(savedIndex.coerceAtLeast(0), s)
        mutator.silentRequestFocus(s.id)
    }
}

/**
 * 把第 from 个块移到第 to 个位置。
 * revert = 反向移动。
 */
class MoveBlockCommand(
    private val mutator: BlockMutator,
    private val from: Int,
    private val to: Int,
) : Command {
    override val label = "MoveBlock($from->$to)"
    override fun apply() {
        mutator.silentMoveBlock(from, to)
    }
    override fun revert() {
        mutator.silentMoveBlock(to, from)
    }
}

/**
 * 把某 blockId 的整块替换为 newBlock（用于清单↔文本转换 / 改变 block 类型场景）。
 * 需要在 apply 前抓 oldBlock 快照以便 revert。
 */
class ReplaceBlockCommand(
    private val mutator: BlockMutator,
    private val blockId: String,
    private val newBlock: Block,
) : Command {
    override val label = "ReplaceBlock($blockId -> ${newBlock.id})"
    private var oldBlock: Block? = null
    override fun apply() {
        oldBlock = mutator.snapshotBlock(blockId)
        mutator.silentReplaceBlock(blockId, newBlock)
        mutator.silentRequestFocus(newBlock.id)
    }
    override fun revert() {
        val o = oldBlock ?: return
        mutator.silentReplaceBlock(newBlock.id, o)
        mutator.silentRequestFocus(o.id)
    }
}
```

- [ ] **Step 3: 写 BlockCommandsTest（6 个用例）**

`app/src/test/java/com/fan/hwnote/app/model/history/commands/BlockCommandsTest.kt`：

```kotlin
package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Block
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BlockCommandsTest {

    /** 假 mutator：内部维护一个 MutableList<Block>，无 View，无焦点处理。 */
    private class FakeMutator(initial: List<Block> = emptyList()) : BlockMutator {
        val blocks: MutableList<Block> = initial.toMutableList()
        var lastFocus: String? = null
        override fun silentInsertBlock(index: Int, block: Block) {
            val safe = index.coerceIn(0, blocks.size)
            blocks.add(safe, block)
        }
        override fun silentRemoveBlock(blockId: String) {
            blocks.removeAll { it.id == blockId }
        }
        override fun silentMoveBlock(from: Int, to: Int) {
            if (from !in blocks.indices || to !in blocks.indices) return
            val b = blocks.removeAt(from)
            blocks.add(to, b)
        }
        override fun silentReplaceBlock(blockId: String, newBlock: Block) {
            val i = indexOfBlock(blockId)
            if (i < 0) return
            blocks[i] = newBlock
        }
        override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
            lastFocus = blockId
        }
        override fun indexOfBlock(blockId: String): Int =
            blocks.indexOfFirst { it.id == blockId }
        override fun snapshotBlock(blockId: String): Block? =
            blocks.find { it.id == blockId }
    }

    @Test fun `AddBlockCommand apply 后块在指定位置 revert 后移除`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "a"), Block.TextBlock(id = "c")))
        val cmd = AddBlockCommand(m, 1, Block.TextBlock(id = "b"))
        cmd.apply()
        assertEquals(listOf("a", "b", "c"), m.blocks.map { it.id })
        assertEquals("b", m.lastFocus)
        cmd.revert()
        assertEquals(listOf("a", "c"), m.blocks.map { it.id })
    }

    @Test fun `RemoveBlockCommand apply 后块消失 revert 后复活到原 index`() {
        val m = FakeMutator(listOf(
            Block.TextBlock(id = "a"), Block.TextBlock(id = "b"), Block.TextBlock(id = "c"),
        ))
        val cmd = RemoveBlockCommand(m, "b")
        cmd.apply()
        assertEquals(listOf("a", "c"), m.blocks.map { it.id })
        cmd.revert()
        assertEquals(listOf("a", "b", "c"), m.blocks.map { it.id })
        assertEquals("b", m.lastFocus)
    }

    @Test fun `RemoveBlockCommand 对不存在 blockId apply 是 no-op revert 也是 no-op`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "a")))
        val cmd = RemoveBlockCommand(m, "nope")
        cmd.apply()
        cmd.revert()
        assertEquals(listOf("a"), m.blocks.map { it.id })
    }

    @Test fun `MoveBlockCommand apply 后顺序变 revert 后还原`() {
        val m = FakeMutator(listOf(
            Block.TextBlock(id = "a"), Block.TextBlock(id = "b"), Block.TextBlock(id = "c"),
        ))
        val cmd = MoveBlockCommand(m, from = 2, to = 0)
        cmd.apply()
        assertEquals(listOf("c", "a", "b"), m.blocks.map { it.id })
        cmd.revert()
        assertEquals(listOf("a", "b", "c"), m.blocks.map { it.id })
    }

    @Test fun `ReplaceBlockCommand apply 后块换型 revert 后还原`() {
        val m = FakeMutator(listOf(
            Block.TextBlock(id = "a", text = "hi"),
        ))
        val newBlock = Block.ChecklistBlock(id = "x")
        val cmd = ReplaceBlockCommand(m, "a", newBlock)
        cmd.apply()
        assertEquals("x", m.blocks[0].id)
        assertEquals(true, m.blocks[0] is Block.ChecklistBlock)
        assertEquals("x", m.lastFocus)
        cmd.revert()
        assertEquals("a", m.blocks[0].id)
        assertEquals("hi", (m.blocks[0] as Block.TextBlock).text)
        assertEquals("a", m.lastFocus)
    }

    @Test fun `ReplaceBlockCommand 对不存在 blockId apply 是 no-op`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "a")))
        val cmd = ReplaceBlockCommand(m, "nope", Block.TextBlock(id = "x"))
        cmd.apply()
        assertEquals(listOf("a"), m.blocks.map { it.id })
        // snapshot 为 null，revert 也是 no-op
        cmd.revert()
        assertEquals(listOf("a"), m.blocks.map { it.id })
    }
}
```

- [ ] **Step 4: 运行测试确认全过**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.history.commands.BlockCommandsTest"
```

Expected: `6 tests, 0 failures`。

- [ ] **Step 5: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/BlockCommands.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/history/commands/BlockCommandsTest.kt && \
git commit -m "feat(m11): 实现 BlockMutator 接口与四个块级 Command（Add/Remove/Move/Replace）"
```

---

### Task 3: StyleCommands.kt（样式 Command）

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/model/history/commands/StyleCommands.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/history/commands/StyleCommandsTest.kt`

- [ ] **Step 1: 写 StyleMutator 接口 + 两个 Command**

`app/src/main/java/com/fan/hwnote/app/model/history/commands/StyleCommands.kt`：

```kotlin
package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import com.fan.hwnote.app.model.history.Command

/**
 * Style 级 silent mutator 接口。
 *
 * "替换 spans" 模型：apply / revert 都是用 setBlockSpans 把目标块的整段 spans 列表换掉，
 * 不做增量增删 — 简化语义、避免 span 重叠歧义。
 */
interface StyleMutator {
    /** 取某 blockId 的当前 spans 快照（深拷贝；data class List 已是只读引用）。不存在返回 null。 */
    fun snapshotSpans(blockId: String): List<TextSpan>?
    /** 用 newSpans 整段替换 blockId 的 spans。不存在则 no-op。 */
    fun setBlockSpans(blockId: String, newSpans: List<TextSpan>)
    /** 取某 blockId 的 heading。不存在或非 TextBlock 返回 null。 */
    fun snapshotHeading(blockId: String): Heading?
    /** 设置 blockId 的 heading（null = 普通段）。 */
    fun setBlockHeading(blockId: String, heading: Heading?)
    fun silentRequestFocus(blockId: String, cursorIndex: Int = Int.MAX_VALUE)
}

/**
 * 应用 / 翻转某个 inline span 类型（B/I/U/S/FONT_SIZE/COLOR）到某 block 的指定 range。
 * 用 before/after 快照对：apply 写 after、revert 写 before。
 *
 * 调用方负责在构造前算好"翻转后的 spans"。
 */
class ApplySpanCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: List<TextSpan>,
    private val after: List<TextSpan>,
    private val focusCursor: Int,
    private val typeForLabel: SpanType,
) : Command {
    override val label = "ApplySpan($blockId, $typeForLabel)"
    override fun apply() {
        mutator.setBlockSpans(blockId, after)
        mutator.silentRequestFocus(blockId, focusCursor)
    }
    override fun revert() {
        mutator.setBlockSpans(blockId, before)
        mutator.silentRequestFocus(blockId, focusCursor)
    }
}

/**
 * 切换某 block 的 heading（H1/H2/普通段）。
 */
class ApplyHeadingCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: Heading?,
    private val after: Heading?,
) : Command {
    override val label = "ApplyHeading($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockHeading(blockId, after)
        mutator.silentRequestFocus(blockId)
    }
    override fun revert() {
        mutator.setBlockHeading(blockId, before)
        mutator.silentRequestFocus(blockId)
    }
}
```

- [ ] **Step 2: 写 StyleCommandsTest（4 个用例）**

`app/src/test/java/com/fan/hwnote/app/model/history/commands/StyleCommandsTest.kt`：

```kotlin
package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StyleCommandsTest {

    private class FakeMutator : StyleMutator {
        val spans: MutableMap<String, List<TextSpan>> = mutableMapOf()
        val headings: MutableMap<String, Heading?> = mutableMapOf()
        var lastFocus: String? = null
        override fun snapshotSpans(blockId: String) = spans[blockId]
        override fun setBlockSpans(blockId: String, newSpans: List<TextSpan>) {
            spans[blockId] = newSpans
        }
        override fun snapshotHeading(blockId: String) = headings[blockId]
        override fun setBlockHeading(blockId: String, heading: Heading?) {
            headings[blockId] = heading
        }
        override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
            lastFocus = blockId
        }
    }

    @Test fun `ApplySpanCommand apply 写 after revert 写 before`() {
        val m = FakeMutator().apply { spans["a"] = emptyList() }
        val before: List<TextSpan> = emptyList()
        val after = listOf(TextSpan(0, 5, SpanType.BOLD))
        val cmd = ApplySpanCommand(m, "a", before, after, focusCursor = 5, typeForLabel = SpanType.BOLD)
        cmd.apply()
        assertEquals(after, m.spans["a"])
        cmd.revert()
        assertEquals(before, m.spans["a"])
    }

    @Test fun `ApplyHeadingCommand apply 写 after revert 写 before`() {
        val m = FakeMutator().apply { headings["a"] = null }
        val cmd = ApplyHeadingCommand(m, "a", before = null, after = Heading.H1)
        cmd.apply()
        assertEquals(Heading.H1, m.headings["a"])
        cmd.revert()
        assertEquals(null, m.headings["a"])
    }

    @Test fun `ApplyHeadingCommand 从 H1 切到普通段 - revert 回 H1`() {
        val m = FakeMutator().apply { headings["a"] = Heading.H1 }
        val cmd = ApplyHeadingCommand(m, "a", before = Heading.H1, after = null)
        cmd.apply()
        assertEquals(null, m.headings["a"])
        cmd.revert()
        assertEquals(Heading.H1, m.headings["a"])
    }

    @Test fun `多个 ApplySpanCommand 链式应用 - 逆序 revert 还原`() {
        val m = FakeMutator().apply { spans["a"] = emptyList() }
        val s0: List<TextSpan> = emptyList()
        val s1 = listOf(TextSpan(0, 3, SpanType.BOLD))
        val s2 = listOf(TextSpan(0, 3, SpanType.BOLD), TextSpan(0, 3, SpanType.ITALIC))
        val c1 = ApplySpanCommand(m, "a", s0, s1, focusCursor = 3, typeForLabel = SpanType.BOLD)
        val c2 = ApplySpanCommand(m, "a", s1, s2, focusCursor = 3, typeForLabel = SpanType.ITALIC)
        c1.apply(); c2.apply()
        assertEquals(s2, m.spans["a"])
        c2.revert()
        assertEquals(s1, m.spans["a"])
        c1.revert()
        assertEquals(s0, m.spans["a"])
    }
}
```

- [ ] **Step 3: 运行测试确认全过**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.history.commands.StyleCommandsTest"
```

Expected: `4 tests, 0 failures`。

- [ ] **Step 4: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/StyleCommands.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/history/commands/StyleCommandsTest.kt && \
git commit -m "feat(m11): 实现 StyleMutator 与 ApplySpan/ApplyHeading Command"
```

---

### Task 4: TextCommands.kt（文本防抖 Command）

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/model/history/commands/TextCommands.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/history/commands/TextCommandsTest.kt`

- [ ] **Step 1: 写 TextMutator 接口 + ReplaceTextCommand**

`app/src/main/java/com/fan/hwnote/app/model/history/commands/TextCommands.kt`：

```kotlin
package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.TextSpan
import com.fan.hwnote.app.model.history.Command

/**
 * Text 防抖 silent mutator 接口。
 */
interface TextMutator {
    /** 用 text + spans 整段替换 blockId 的内容。不存在则 no-op。 */
    fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>)
    /** 把焦点拉到某 blockId 的 cursor 位置。 */
    fun silentRequestFocus(blockId: String, cursorIndex: Int = Int.MAX_VALUE)
}

/**
 * 文本块文字替换。TextBlockView 防抖 800ms 后捕捉 before / after 快照，构造本 Command 入栈。
 *
 * apply = 写 after（用于 redo 路径，初始 push 时调用方已先调 silent mutator，无需再 apply 一次）
 *   ↑ 调用约定：调用方 push 前已经把 after 写入；EditHistoryManager.push 不调 apply。
 *     redo 路径才会调 apply。
 *
 * revert = 写 before。
 */
class ReplaceTextCommand(
    private val mutator: TextMutator,
    private val blockId: String,
    private val beforeText: String,
    private val beforeSpans: List<TextSpan>,
    private val afterText: String,
    private val afterSpans: List<TextSpan>,
) : Command {
    override val label = "ReplaceText($blockId, len ${beforeText.length}->${afterText.length})"
    override fun apply() {
        mutator.silentReplaceText(blockId, afterText, afterSpans)
        mutator.silentRequestFocus(blockId, afterText.length)
    }
    override fun revert() {
        mutator.silentReplaceText(blockId, beforeText, beforeSpans)
        mutator.silentRequestFocus(blockId, beforeText.length)
    }
}
```

- [ ] **Step 2: 写 TextCommandsTest（4 个用例）**

`app/src/test/java/com/fan/hwnote/app/model/history/commands/TextCommandsTest.kt`：

```kotlin
package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextCommandsTest {

    private class FakeMutator : TextMutator {
        val texts: MutableMap<String, String> = mutableMapOf()
        val spans: MutableMap<String, List<TextSpan>> = mutableMapOf()
        var lastFocusBlock: String? = null
        var lastFocusCursor: Int = -1
        override fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>) {
            texts[blockId] = text
            this.spans[blockId] = spans
        }
        override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
            lastFocusBlock = blockId
            lastFocusCursor = cursorIndex
        }
    }

    @Test fun `ReplaceTextCommand apply 写 after revert 写 before`() {
        val m = FakeMutator().apply { texts["a"] = "hello"; spans["a"] = emptyList() }
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hello", beforeSpans = emptyList(),
            afterText = "hello world", afterSpans = emptyList(),
        )
        cmd.apply()
        assertEquals("hello world", m.texts["a"])
        cmd.revert()
        assertEquals("hello", m.texts["a"])
    }

    @Test fun `ReplaceTextCommand 同步替换 spans`() {
        val m = FakeMutator()
        val beforeSpans = listOf(TextSpan(0, 5, SpanType.BOLD))
        val afterSpans = listOf(TextSpan(0, 11, SpanType.BOLD))
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hello", beforeSpans = beforeSpans,
            afterText = "hello world", afterSpans = afterSpans,
        )
        cmd.apply()
        assertEquals(afterSpans, m.spans["a"])
        cmd.revert()
        assertEquals(beforeSpans, m.spans["a"])
    }

    @Test fun `apply 后焦点移到 afterText 末尾`() {
        val m = FakeMutator()
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hi", beforeSpans = emptyList(),
            afterText = "hi there", afterSpans = emptyList(),
        )
        cmd.apply()
        assertEquals("a", m.lastFocusBlock)
        assertEquals("hi there".length, m.lastFocusCursor)
    }

    @Test fun `revert 后焦点移到 beforeText 末尾`() {
        val m = FakeMutator()
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hi", beforeSpans = emptyList(),
            afterText = "hi there", afterSpans = emptyList(),
        )
        cmd.apply()
        cmd.revert()
        assertEquals("a", m.lastFocusBlock)
        assertEquals("hi".length, m.lastFocusCursor)
    }
}
```

- [ ] **Step 3: 运行测试确认全过**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.history.commands.TextCommandsTest"
```

Expected: `4 tests, 0 failures`。

- [ ] **Step 4: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/TextCommands.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/history/commands/TextCommandsTest.kt && \
git commit -m "feat(m11): 实现 TextMutator 与 ReplaceTextCommand（防抖落栈）"
```

---

### Task 5: Presenter 接口实现 + silent mutator + history 字段 + undo/redo/flush 入口

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

本 task 不改任何既有公共方法的行为；只加新东西。完成后跑现有 85 单测必须全过。

- [ ] **Step 1: import 新 model + 让 EditorPresenter 实现三个 Mutator 接口**

修改 `EditorPresenter.kt` 顶部 import：

```kotlin
import com.fan.hwnote.app.model.history.EditHistoryManager
import com.fan.hwnote.app.model.history.commands.AddBlockCommand
import com.fan.hwnote.app.model.history.commands.ApplyHeadingCommand
import com.fan.hwnote.app.model.history.commands.ApplySpanCommand
import com.fan.hwnote.app.model.history.commands.BlockMutator
import com.fan.hwnote.app.model.history.commands.RemoveBlockCommand
import com.fan.hwnote.app.model.history.commands.ReplaceBlockCommand
import com.fan.hwnote.app.model.history.commands.ReplaceTextCommand
import com.fan.hwnote.app.model.history.commands.StyleMutator
import com.fan.hwnote.app.model.history.commands.TextMutator
```

把类签名改成：

```kotlin
class EditorPresenter(
    private val context: Context,
    private val container: LinearLayout,
    private val overlay: com.fan.hwnote.app.view.handwriting.HandwritingOverlayView,
) : BlockView.Callback, BlockMutator, StyleMutator, TextMutator {
```

- [ ] **Step 2: 加 history 字段**

在 `audioPlayer` 字段下方加：

```kotlin
    /** M11 撤销 / 重做管理器。loadNote 完成 = 起点；saveNote 成功后 clear。 */
    val history = EditHistoryManager()
```

- [ ] **Step 3: 实现 BlockMutator 接口（在 class 末尾、companion 之前加 // ----- M11 silent mutators ----- 区域）**

```kotlin
    // ----- M11 silent mutators (不入栈，apply/revert 共用入口) -----

    override fun silentInsertBlock(index: Int, block: Block) {
        val safe = index.coerceIn(0, currentBlocks.size)
        when (block) {
            is Block.TextBlock -> addTextBlockView(block, insertAt = safe)
            is Block.ImageBlock -> addImageBlockView(block, insertAt = safe)
            is Block.ChecklistBlock -> addChecklistBlockView(block, insertAt = safe)
            is Block.AudioBlock -> addAudioBlockView(block, insertAt = safe)
        }
    }

    override fun silentRemoveBlock(blockId: String) {
        val idx = currentBlocks.indexOfFirst { it.toBlock().id == blockId }
        if (idx < 0) return
        val view = currentBlocks[idx]
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
    }

    override fun silentMoveBlock(from: Int, to: Int) {
        if (from !in currentBlocks.indices || to !in currentBlocks.indices) return
        val view = currentBlocks.removeAt(from)
        container.removeView(view)
        currentBlocks.add(to, view)
        container.addView(view, to)
    }

    override fun silentReplaceBlock(blockId: String, newBlock: Block) {
        val idx = currentBlocks.indexOfFirst { it.toBlock().id == blockId }
        if (idx < 0) return
        val old = currentBlocks[idx]
        container.removeView(old)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === old) focusedTextBlock = null
        silentInsertBlock(idx, newBlock)
    }

    override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        view.focusEditEnd()
        val safeCursor = cursorIndex.coerceIn(0, view.edit.text.length)
        view.edit.setSelection(safeCursor)
    }

    override fun indexOfBlock(blockId: String): Int =
        currentBlocks.indexOfFirst { it.toBlock().id == blockId }

    override fun snapshotBlock(blockId: String): Block? =
        currentBlocks.firstOrNull { it.toBlock().id == blockId }?.toBlock()

    // ----- StyleMutator -----

    override fun snapshotSpans(blockId: String): List<TextSpan>? =
        (snapshotBlock(blockId) as? Block.TextBlock)?.spans

    override fun setBlockSpans(blockId: String, newSpans: List<TextSpan>) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        val sp = SpannableString(view.edit.text.toString())
        newSpans.applyTo(sp)
        view.edit.setText(sp)
    }

    override fun snapshotHeading(blockId: String): Heading? =
        (snapshotBlock(blockId) as? Block.TextBlock)?.heading

    override fun setBlockHeading(blockId: String, heading: Heading?) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        view.setHeading(heading)
    }

    // ----- TextMutator -----

    override fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        val sp = SpannableString(text)
        spans.applyTo(sp)
        view.suppressDebounceWhile {
            view.edit.setText(sp)
        }
    }
```

`suppressDebounceWhile` 是 Task 8 在 TextBlockView 内加的钩子（一个 block 把"我自己改 text，别落栈"标志在改完后清掉）；本 task 内允许临时声明为占位 — 但更稳妥：本 task 先用直接 setText，Task 8 把所有 silentReplaceText 调用替换成 suppressDebounceWhile 版本。**采用占位策略，本 task 仅写 `view.edit.setText(sp)` 不带 suppress；Task 8 再回来加。**

把上面的 `silentReplaceText` 改成（本 task 版本）：

```kotlin
    override fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        val sp = SpannableString(text)
        spans.applyTo(sp)
        view.edit.setText(sp)
        // 注：Task 8 引入 TextWatcher 防抖后，本调用会触发新的入栈循环。
        //     Task 8 内会改成 view.suppressDebounceWhile { view.edit.setText(sp) }。
    }
```

需要 import：
```kotlin
import android.text.SpannableString
```

- [ ] **Step 4: 加新公共入口 undo/redo/recordTextEdit/flushPendingTextEdits**

在 `stopAllPlayback()` 上方（接近文件末尾）加：

```kotlin
    // ----- M11 公共入口 -----

    /** Activity 顶部 ↶ 按钮入口。 */
    fun undo() {
        flushPendingTextEdits()
        history.undo()
    }

    /** Activity 顶部 ↷ 按钮入口。 */
    fun redo() {
        flushPendingTextEdits()
        history.redo()
    }

    /** TextBlockView 防抖窗口结束时调，落 ReplaceTextCommand 入栈。 */
    fun recordTextEdit(
        blockId: String,
        beforeText: String,
        beforeSpans: List<TextSpan>,
        afterText: String,
        afterSpans: List<TextSpan>,
    ) {
        if (beforeText == afterText && beforeSpans == afterSpans) return
        history.push(ReplaceTextCommand(
            this, blockId, beforeText, beforeSpans, afterText, afterSpans,
        ))
    }

    /** 遍历当前所有 TextBlockView 强制 flush 防抖窗口未落栈的变更。 */
    fun flushPendingTextEdits() {
        for (v in currentBlocks) {
            if (v is TextBlockView) v.flushPendingTextEdit()
        }
    }
```

- [ ] **Step 5: 编译 + 跑全量测试确认不破坏既有行为**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

Expected: 全部既有测试通过 + Task 1-4 新增测试通过。
**TextBlockView.flushPendingTextEdit / suppressDebounceWhile 引用会编译失败 — Task 8 才会加。本 step 先不调用 view.flushPendingTextEdit，而是在 flushPendingTextEdits() 内用 `// TODO Task 8` 占位 + 留空循环：**

把上一步的 `flushPendingTextEdits()` 改成：

```kotlin
    fun flushPendingTextEdits() {
        // Task 8 内接通：for (v in currentBlocks) if (v is TextBlockView) v.flushPendingTextEdit()
    }
```

再编译跑测试，确认全过。

- [ ] **Step 6: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt && \
git commit -m "feat(m11): EditorPresenter 实现 BlockMutator/StyleMutator/TextMutator 与 history 入口"
```

---

### Task 6: 既有"样式"公共方法接 history.push

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

改造目标方法：`toggleInline` / `toggleSize` / `pickColor` / `toggleHeading`。模式：先快照 before → 调既有逻辑 → 取 after → push Command。

- [ ] **Step 1: 改造 toggleInline**

把现有的 `toggleInline` 内"有选区分支"改成快照+push：

```kotlin
    fun toggleInline(type: SpanType): Boolean {
        val v = focusedTextBlock ?: return false
        val edit = v.edit
        val start = edit.selectionStart
        val end = edit.selectionEnd
        if (start in 0 until end) {
            val blockId = (v.toBlock()).id
            val before = (v.toBlock()).spans
            applyInlineToRange(edit.text as Spannable, type, start, end, null)
            val after = (v.toBlock()).spans
            history.push(ApplySpanCommand(this, blockId, before, after, focusCursor = end, typeForLabel = type))
            return false
        }
        if (pendingInline.contains(type)) pendingInline.remove(type) else pendingInline.add(type)
        return pendingInline.contains(type)
    }
```

- [ ] **Step 2: 同样改造 toggleSize 与 pickColor**

```kotlin
    fun toggleSize(value: String): String? {
        val v = focusedTextBlock ?: return null
        val edit = v.edit
        val start = edit.selectionStart; val end = edit.selectionEnd
        if (start in 0 until end) {
            val blockId = v.toBlock().id
            val before = v.toBlock().spans
            applyInlineToRange(edit.text as Spannable, SpanType.FONT_SIZE, start, end, value)
            val after = v.toBlock().spans
            history.push(ApplySpanCommand(this, blockId, before, after, focusCursor = end, typeForLabel = SpanType.FONT_SIZE))
            return null
        }
        pendingSize = if (pendingSize == value) null else value
        return pendingSize
    }

    fun pickColor(hex: String): String? {
        val v = focusedTextBlock ?: return null
        val edit = v.edit
        val start = edit.selectionStart; val end = edit.selectionEnd
        if (start in 0 until end) {
            val blockId = v.toBlock().id
            val before = v.toBlock().spans
            applyInlineToRange(edit.text as Spannable, SpanType.COLOR, start, end, hex)
            val after = v.toBlock().spans
            history.push(ApplySpanCommand(this, blockId, before, after, focusCursor = end, typeForLabel = SpanType.COLOR))
            return null
        }
        pendingColor = if (pendingColor == hex) null else hex
        return pendingColor
    }
```

- [ ] **Step 3: 改造 toggleHeading**

```kotlin
    fun toggleHeading(target: Heading) {
        val v = focusedTextBlock ?: return
        val blockId = v.toBlock().id
        val before = v.currentHeading()
        val after = if (before == target) null else target
        v.setHeading(after)
        history.push(ApplyHeadingCommand(this, blockId, before, after))
    }
```

- [ ] **Step 4: 编译 + 跑全量测试**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

Expected: 全过。

- [ ] **Step 5: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt && \
git commit -m "feat(m11): 样式入口（toggleInline/toggleSize/pickColor/toggleHeading）接 history.push"
```

---

### Task 7: 既有"块"公共方法接 history.push

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

改造方法：`onRequestSplitAfter` / `onRequestDelete` / `onChecklistConvertBlockToText` / `onChecklistAppendTextAfter` / `insertImageBlocksAtFocus` / `insertChecklistBlockAtFocus` / `insertAudioBlockAtFocus` / `convertTextBlockToChecklist` / `convertChecklistItemToText` / `onImageLoadFailed`。

模式：先抓 snapshot / index → 让既有逻辑跑（保持现 UI 行为 / 焦点处理不变） → 用 snapshot 构造 Command push。
**关键：避免双重副作用 — push 的 Command 已经包含 apply 语义，但我们已经手工执行了真实变更，所以只 push 不调 apply（沿用 EditHistoryManager 的 push 不调 apply 约定）。**

- [ ] **Step 1: 改造 onRequestSplitAfter**

```kotlin
    override fun onRequestSplitAfter(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val newBlock = emptyTextBlock()
        addTextBlockView(newBlock, insertAt = idx + 1)
        (currentBlocks[idx + 1] as TextBlockView).focusEditEnd()
        history.push(AddBlockCommand(this, index = idx + 1, block = newBlock))
    }
```

- [ ] **Step 2: 改造 onRequestDelete（注意：现版会顺手 purge 本地文件 — 改 Inverse Op 后不再 purge，遗孤由 Task 10 的 saveNote 后 cleanOrphanFiles 收口）**

```kotlin
    override fun onRequestDelete(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx <= 0) return // 第一块不可删
        val blockSnapshot = view.toBlock()
        // 不再调 purgeImageOnDisk / purgeAudioOnDisk —— undo 需要文件还在。
        // 遗孤清理由 NoteRepository.cleanOrphanFiles 在 saveNote 后异步完成。
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
        (currentBlocks[idx - 1] as? TextBlockView)?.focusEditEnd()
        history.push(RemoveBlockCommand(this, blockSnapshot.id).also {
            // RemoveBlockCommand.apply 会再次抓 snapshot + remove；但本路径已 remove，
            // push 不调 apply 所以无副作用。undo 路径走 revert：snapshot 为 null（已不存在）
            // → revert no-op。我们需要预填 snapshot：
        })
    }
```

上面的写法有缺陷 —— RemoveBlockCommand 把 snapshot 抓取留在 apply()，但 push 不调 apply。修正：本路径下 RemoveBlockCommand 需要"预先就绪"的 snapshot。改造 RemoveBlockCommand 让它支持外部预填 snapshot 的入口（也不破坏 Task 2 单测：Task 2 调 cmd.apply() 走的是抓 snapshot 路径）。

**修正方案：** 在 `BlockCommands.kt` 的 `RemoveBlockCommand` 改造（追加构造参数，可选预填）：

修改 `app/src/main/java/com/fan/hwnote/app/model/history/commands/BlockCommands.kt` 中的 `RemoveBlockCommand`：

```kotlin
class RemoveBlockCommand(
    private val mutator: BlockMutator,
    private val blockId: String,
    /** 可选预填 — 调用方已 remove 时传入。null 时 apply 路径会自己抓。 */
    presnapshot: Block? = null,
    presavedIndex: Int = -1,
) : Command {
    override val label = "RemoveBlock($blockId)"
    private var snapshot: Block? = presnapshot
    private var savedIndex: Int = presavedIndex
    override fun apply() {
        if (snapshot == null) {
            snapshot = mutator.snapshotBlock(blockId)
            savedIndex = mutator.indexOfBlock(blockId)
        }
        mutator.silentRemoveBlock(blockId)
    }
    override fun revert() {
        val s = snapshot ?: return
        mutator.silentInsertBlock(savedIndex.coerceAtLeast(0), s)
        mutator.silentRequestFocus(s.id)
    }
}
```

Task 2 既有单测仍能通过（不传 presnapshot，走 apply 抓快照路径）。

`onRequestDelete` 改成：

```kotlin
    override fun onRequestDelete(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx <= 0) return
        val blockSnapshot = view.toBlock()
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
        (currentBlocks[idx - 1] as? TextBlockView)?.focusEditEnd()
        history.push(RemoveBlockCommand(this, blockSnapshot.id,
            presnapshot = blockSnapshot, presavedIndex = idx))
    }
```

- [ ] **Step 3: 改造 onImageLoadFailed —— 同样不再 purgeImageOnDisk，但加 push RemoveBlockCommand**

```kotlin
    override fun onImageLoadFailed(view: BlockView) {
        if (view !is ImageBlockView) { onRequestDelete(view); return }
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val snapshot = view.toBlock()
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (idx == 0 && currentBlocks.isEmpty()) {
            val tail = emptyTextBlock()
            addTextBlockView(tail)
            (currentBlocks[0] as? TextBlockView)?.focusEditEnd()
            history.push(RemoveBlockCommand(this, snapshot.id, presnapshot = snapshot, presavedIndex = idx))
            history.push(AddBlockCommand(this, 0, tail))
        } else {
            history.push(RemoveBlockCommand(this, snapshot.id, presnapshot = snapshot, presavedIndex = idx))
        }
    }
```

可以删除 `purgeImageOnDisk` / `purgeAudioOnDisk` 两个 private 方法和它们的调用（onRequestDelete 已不调）。

- [ ] **Step 4: 改造 onChecklistConvertBlockToText / onChecklistAppendTextAfter**

```kotlin
    override fun onChecklistConvertBlockToText(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val oldSnapshot = view.toBlock()
        container.removeView(view)
        currentBlocks.removeAt(idx)
        val newBlock = emptyTextBlock()
        addTextBlockView(newBlock, insertAt = idx)
        (currentBlocks[idx] as TextBlockView).focusEditEnd()
        history.push(ReplaceBlockCommand(this, oldSnapshot.id, newBlock))
    }

    override fun onChecklistAppendTextAfter(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val newBlock = emptyTextBlock()
        addTextBlockView(newBlock, insertAt = idx + 1)
        (currentBlocks[idx + 1] as TextBlockView).focusEditEnd()
        history.push(AddBlockCommand(this, idx + 1, newBlock))
    }
```

ReplaceBlockCommand 同样需要支持"预填 oldBlock"。改造 ReplaceBlockCommand：

```kotlin
class ReplaceBlockCommand(
    private val mutator: BlockMutator,
    private val blockId: String,
    private val newBlock: Block,
    preOldBlock: Block? = null,
) : Command {
    override val label = "ReplaceBlock($blockId -> ${newBlock.id})"
    private var oldBlock: Block? = preOldBlock
    override fun apply() {
        if (oldBlock == null) oldBlock = mutator.snapshotBlock(blockId)
        mutator.silentReplaceBlock(blockId, newBlock)
        mutator.silentRequestFocus(newBlock.id)
    }
    override fun revert() {
        val o = oldBlock ?: return
        mutator.silentReplaceBlock(newBlock.id, o)
        mutator.silentRequestFocus(o.id)
    }
}
```

`onChecklistConvertBlockToText` 改成：

```kotlin
        history.push(ReplaceBlockCommand(this, oldSnapshot.id, newBlock, preOldBlock = oldSnapshot))
```

- [ ] **Step 5: 改造 insertImageBlocksAtFocus / insertChecklistBlockAtFocus / insertAudioBlockAtFocus**

```kotlin
    fun insertImageBlocksAtFocus(blocks: List<Block.ImageBlock>) {
        if (blocks.isEmpty()) return
        val anchor = focusedTextBlock
        val baseIdx = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                      else currentBlocks.size
        var insertAt = baseIdx
        for (b in blocks) {
            addImageBlockView(b, insertAt = insertAt)
            history.push(AddBlockCommand(this, insertAt, b))
            insertAt += 1
        }
        val tail = emptyTextBlock()
        addTextBlockView(tail, insertAt = insertAt)
        history.push(AddBlockCommand(this, insertAt, tail))
        (currentBlocks[insertAt] as TextBlockView).focusEditEnd()
    }

    fun insertChecklistBlockAtFocus() {
        val anchor = focusedTextBlock
        val insertAt = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                       else currentBlocks.size
        val block = Block.ChecklistBlock(
            id = "c-${UUID.randomUUID().toString().take(8)}",
            items = mutableListOf(com.fan.hwnote.app.model.entity.ChecklistItem(false, "")),
        )
        addChecklistBlockView(block, insertAt = insertAt)
        (currentBlocks[insertAt] as ChecklistBlockView).focusLastItemEnd()
        history.push(AddBlockCommand(this, insertAt, block))
    }

    fun insertAudioBlockAtFocus(block: Block.AudioBlock) {
        val anchor = focusedTextBlock
        val baseIdx = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                      else currentBlocks.size
        addAudioBlockView(block, insertAt = baseIdx)
        history.push(AddBlockCommand(this, baseIdx, block))
        val tail = emptyTextBlock()
        addTextBlockView(tail, insertAt = baseIdx + 1)
        history.push(AddBlockCommand(this, baseIdx + 1, tail))
        (currentBlocks[baseIdx + 1] as TextBlockView).focusEditEnd()
    }
```

- [ ] **Step 6: 改造 convertTextBlockToChecklist / convertChecklistItemToText**

```kotlin
    private fun convertTextBlockToChecklist(tb: TextBlockView) {
        val idx = currentBlocks.indexOf(tb)
        if (idx < 0) return
        val oldSnapshot = tb.toBlock()
        val text = tb.edit.text.toString()
        container.removeView(tb)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === tb) focusedTextBlock = null
        val newBlock = Block.ChecklistBlock(
            id = "c-${UUID.randomUUID().toString().take(8)}",
            items = mutableListOf(com.fan.hwnote.app.model.entity.ChecklistItem(false, text)),
        )
        addChecklistBlockView(newBlock, insertAt = idx)
        (currentBlocks[idx] as ChecklistBlockView).focusLastItemEnd()
        history.push(ReplaceBlockCommand(this, oldSnapshot.id, newBlock, preOldBlock = oldSnapshot))
    }

    private fun convertChecklistItemToText(
        block: ChecklistBlockView,
        item: ChecklistItemView,
    ) {
        val blockIdx = currentBlocks.indexOf(block)
        if (blockIdx < 0) return
        val oldChecklistSnapshot = block.toBlock()
        val text = item.edit.text.toString()
        val becameEmpty = block.removeItemAndReturnEmpty(item)
        val newTextBlock = Block.TextBlock(
            id = "b-${UUID.randomUUID().toString().take(8)}",
            text = text,
        )
        if (becameEmpty) {
            container.removeView(block)
            currentBlocks.removeAt(blockIdx)
            addTextBlockView(newTextBlock, insertAt = blockIdx)
            (currentBlocks[blockIdx] as TextBlockView).focusEditEnd()
            history.push(ReplaceBlockCommand(this, oldChecklistSnapshot.id, newTextBlock,
                preOldBlock = oldChecklistSnapshot))
        } else {
            addTextBlockView(newTextBlock, insertAt = blockIdx + 1)
            (currentBlocks[blockIdx + 1] as TextBlockView).focusEditEnd()
            // 该路径：清单还在但少了一项；当前 ReplaceBlockCommand 无法表达"删 item"的细粒度。
            // 简化：以一次 ReplaceBlockCommand 表达整个 ChecklistBlock 的前后差异（apply/revert 用快照对）。
            val newChecklistSnapshot = block.toBlock()
            history.push(ReplaceBlockCommand(this, oldChecklistSnapshot.id, newChecklistSnapshot,
                preOldBlock = oldChecklistSnapshot))
            // 同时记 AddBlockCommand 表达新 TextBlock 的插入。两条共同回退即可。
            history.push(AddBlockCommand(this, blockIdx + 1, newTextBlock))
        }
    }
```

- [ ] **Step 7: 删 purgeImageOnDisk / purgeAudioOnDisk 两个 private 方法（不再使用）**

删除 `purgeImageOnDisk(block)` 和 `purgeAudioOnDisk(block)` 两个 private 方法定义。Grep 确认无引用：

```bash
grep -n "purgeImageOnDisk\|purgeAudioOnDisk" /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt
```

Expected: 无输出（全部已删）。

- [ ] **Step 8: 编译 + 跑全量测试**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

Expected: 全过。

- [ ] **Step 9: 提交（含 BlockCommands.kt 内的 RemoveBlockCommand/ReplaceBlockCommand 扩参修改）**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/BlockCommands.kt && \
git commit -m "feat(m11): 块入口接 history.push，删 purge 改由 cleanOrphanFiles 收口"
```

---

### Task 8: TextBlockView 防抖 + flushPendingTextEdit + suppressDebounceWhile

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`（一行：silentReplaceText 改用 suppressDebounceWhile）

- [ ] **Step 1: 在 TextBlockView 增加防抖状态**

在 TextBlockView 类内加字段（class 体顶部、edit 字段之后）：

```kotlin
    /** Presenter 注入：防抖窗口结束后回调，让 Presenter 决定是否落栈。 */
    var textDebounceCallback: ((blockId: String, beforeText: String, beforeSpans: List<com.fan.hwnote.app.model.entity.TextSpan>, afterText: String, afterSpans: List<com.fan.hwnote.app.model.entity.TextSpan>) -> Unit)? = null

    private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val debounceMs = 800L
    private var debounceArmed = false
    private var pendingBeforeText: String = ""
    private var pendingBeforeSpans: List<com.fan.hwnote.app.model.entity.TextSpan> = emptyList()
    private val debounceRunnable = Runnable { flushPendingTextEdit() }
    /** Presenter silentReplaceText 调用前后会用此守卫，避免那段 setText 又触发本 TextWatcher 入栈。 */
    private var suppressDebounce = false
```

- [ ] **Step 2: 改造 TextWatcher.onTextChanged 加防抖入口**

把现有 TextWatcher 改成（注意保留 pendingApplier 逻辑）：

```kotlin
        edit.addTextChangedListener(object : TextWatcher {
            private var insertStart = 0
            private var insertCount = 0
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // 第一次变更进入窗口前抓 before 快照
                if (!suppressDebounce && !debounceArmed) {
                    val pre = SpannableString(edit.text)
                    pendingBeforeText = pre.toString()
                    pendingBeforeSpans = pre.toTextSpans()
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                insertStart = start
                insertCount = count
            }
            override fun afterTextChanged(s: Editable?) {
                if (insertCount > 0 && s is android.text.Spannable) {
                    pendingApplier?.invoke(s, insertStart, insertCount)
                }
                if (suppressDebounce) return
                debounceArmed = true
                debounceHandler.removeCallbacks(debounceRunnable)
                debounceHandler.postDelayed(debounceRunnable, debounceMs)
            }
        })
```

- [ ] **Step 3: 加 flushPendingTextEdit + suppressDebounceWhile 两个公共方法**

在 TextBlockView 类末尾加：

```kotlin
    /** 立即把防抖窗口未落栈的变更落栈（focus 切换 / save / undo / redo 触发）。 */
    fun flushPendingTextEdit() {
        if (!debounceArmed) return
        debounceHandler.removeCallbacks(debounceRunnable)
        debounceArmed = false
        val currentSp = SpannableString(edit.text)
        val afterText = currentSp.toString()
        val afterSpans = currentSp.toTextSpans()
        textDebounceCallback?.invoke(blockId, pendingBeforeText, pendingBeforeSpans, afterText, afterSpans)
    }

    /** Presenter silentReplaceText 用：在 block 内执行 setText 时屏蔽防抖入栈。 */
    fun suppressDebounceWhile(block: () -> Unit) {
        suppressDebounce = true
        try { block() } finally {
            suppressDebounce = false
            debounceArmed = false
            debounceHandler.removeCallbacks(debounceRunnable)
        }
    }
```

- [ ] **Step 4: TextBlockView 也要在失焦时立即 flush（避免切到别的 TextBlock 后丢)**

把现有 `edit.setOnFocusChangeListener` 改成：

```kotlin
        edit.setOnFocusChangeListener { _, focused ->
            if (focused) {
                callback?.onFocusGained(this)
            } else {
                flushPendingTextEdit()
            }
        }
```

- [ ] **Step 5: 把 Presenter 中 silentReplaceText 内改为 suppressDebounceWhile 包裹**

`EditorPresenter.kt` 中 `silentReplaceText`：

```kotlin
    override fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        val sp = SpannableString(text)
        spans.applyTo(sp)
        view.suppressDebounceWhile {
            view.edit.setText(sp)
        }
    }
```

- [ ] **Step 6: 在 addTextBlockView 内注入 textDebounceCallback**

`EditorPresenter.kt` 中 `addTextBlockView`：

```kotlin
    private fun addTextBlockView(block: Block.TextBlock, insertAt: Int = -1) {
        val v = TextBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            pendingApplier = { sp, start, count -> applyPendingTo(sp, start, count) }
            textDebounceCallback = { blockId, beforeText, beforeSpans, afterText, afterSpans ->
                recordTextEdit(blockId, beforeText, beforeSpans, afterText, afterSpans)
            }
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v)
            currentBlocks.add(v)
        } else {
            container.addView(v, insertAt)
            currentBlocks.add(insertAt, v)
        }
    }
```

- [ ] **Step 7: 接通 Presenter 的 flushPendingTextEdits（Task 5 占位的版本）**

把 Task 5 留的占位改成真实实现：

```kotlin
    fun flushPendingTextEdits() {
        for (v in currentBlocks) {
            if (v is TextBlockView) v.flushPendingTextEdit()
        }
    }
```

- [ ] **Step 8: 编译 + 跑全量测试**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

Expected: 全过。

- [ ] **Step 9: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt && \
git commit -m "feat(m11): TextBlockView 加 800ms 文本防抖与 flush 接口，Presenter 接通"
```

---

### Task 9: menu_editor.xml + Activity 接 menu + history.listener + onPause flush

**Files:**
- Create: `app/src/main/res/menu/menu_editor.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

- [ ] **Step 1: 写 menu_editor.xml**

`app/src/main/res/menu/menu_editor.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/action_undo"
        android:icon="@drawable/ic_undo"
        android:title="@string/action_undo_cd"
        app:showAsAction="always" />
    <item
        android:id="@+id/action_redo"
        android:icon="@drawable/ic_redo"
        android:title="@string/action_redo_cd"
        app:showAsAction="always" />
</menu>
```

- [ ] **Step 2: 加 strings**

`app/src/main/res/values/strings.xml` 内追加（在 `</resources>` 之前）：

```xml
    <string name="action_undo_cd">撤销</string>
    <string name="action_redo_cd">重做</string>
```

- [ ] **Step 3: NoteEditorActivity 加 onCreateOptionsMenu + onOptionsItemSelected**

在 NoteEditorActivity.kt 类内、`onCreate` 之后（或紧邻 `onPause` 之前）加：

```kotlin
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        menu.findItem(R.id.action_undo)?.let { it.isEnabled = presenter.history.canUndo(); applyMenuIconAlpha(it) }
        menu.findItem(R.id.action_redo)?.let { it.isEnabled = presenter.history.canRedo(); applyMenuIconAlpha(it) }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_undo -> { presenter.undo(); true }
            R.id.action_redo -> { presenter.redo(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** disabled 时图标显示半透明（Material 不会自动 alpha；自己 mutate）。 */
    private fun applyMenuIconAlpha(item: android.view.MenuItem) {
        item.icon?.mutate()?.alpha = if (item.isEnabled) 255 else 102
    }
```

- [ ] **Step 4: 在 onCreate 末尾注册 history.listener**

在 `onCreate` 内、`loadNote()` 调用之前加：

```kotlin
        presenter.history.listener = { _, _ -> invalidateOptionsMenu() }
```

- [ ] **Step 5: 在 onPause 内 saveNote 之前先 flush**

把 onPause 改成：

```kotlin
    override fun onPause() {
        super.onPause()
        presenter.stopAllPlayback()
        currentRecordingSheet?.forceCancel()
        currentRecordingSheet = null
        presenter.flushPendingTextEdits()  // M11: 防抖文本先入栈，再走 save
        saveNote()
    }
```

- [ ] **Step 6: 编译 + 跑测试**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

Expected: 全过。

- [ ] **Step 7: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/res/menu/menu_editor.xml \
        code/HuaWeiNote/app/src/main/res/values/strings.xml \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt && \
git commit -m "feat(m11): 顶部 AppBar 加撤销/重做 MenuItem + 接通 Presenter"
```

---

### Task 10: NoteRepository.cleanOrphanFiles + saveNote 成功 clear + 触发 cleanOrphan

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

- [ ] **Step 1: NoteRepository 加 cleanOrphanFiles**

`NoteRepository.kt` 内，在 `purgeExpired` 之后加：

```kotlin
    /**
     * 扫描某笔记本地 images/ + audio/ 目录，删除当前 content.blocks 未引用的孤儿文件。
     *
     * 用于 M11 撤销 / 重做：编辑器删块时不立即 rm 文件（保证 undo 能复活），
     * 保存成功后异步清理本次操作产生的真正孤儿。
     *
     * - 容错：任何 IOException 仅 log，不抛
     * - 幂等：多次调用对同一份 note 行为一致
     */
    suspend fun cleanOrphanFiles(noteId: Long, note: com.fan.hwnote.app.model.entity.Note) =
        withContext(Dispatchers.IO) {
            if (noteId <= 0L) return@withContext
            val referenced = mutableSetOf<String>()
            for (b in note.content.blocks) {
                when (b) {
                    is com.fan.hwnote.app.model.entity.Block.ImageBlock -> referenced += b.fileName
                    is com.fan.hwnote.app.model.entity.Block.AudioBlock -> referenced += b.fileName
                    else -> Unit
                }
            }
            runCatching {
                fileStorage.imageDir(noteId).listFiles()?.forEach { f ->
                    if (f.isFile && f.name !in referenced) f.delete()
                }
            }.onFailure { android.util.Log.w("NoteRepository", "cleanOrphan image failed", it) }
            runCatching {
                fileStorage.audioDir(noteId).listFiles()?.forEach { f ->
                    if (f.isFile && f.name !in referenced) f.delete()
                }
            }.onFailure { android.util.Log.w("NoteRepository", "cleanOrphan audio failed", it) }
        }
```

- [ ] **Step 2: NoteEditorActivity.saveNote 成功后 history.clear + 异步 cleanOrphan**

修改 saveNote 内 `withContext(Dispatchers.Main)` 块：

```kotlin
    private fun saveNote() {
        val loaded = loadedNote ?: return
        val title = titleInput.text.toString()
        val toSave = presenter.collectCurrentNote(title)
            .copy(id = loaded.id, categoryId = loaded.categoryId)
        val isAllEmpty = title.isEmpty() && toSave.plainText.isEmpty()
        if (loaded.id == 0L && isAllEmpty) return
        if (loaded.id == 0L && saveInFlight) return
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = NoteRepository.save(toSave)
            // M11: 异步清理本次保存后产生的孤儿文件（图片 / 音频）
            val savedId = if (newId > 0L) newId else loaded.id
            if (savedId > 0L) {
                NoteRepository.cleanOrphanFiles(savedId, toSave.copy(id = savedId))
            }
            withContext(Dispatchers.Main) {
                if (loaded.id == 0L && newId > 0) {
                    noteId = newId
                    loadedNote = toSave.copy(id = newId)
                    presenter.noteId = newId
                } else if (newId <= 0L) {
                    android.widget.Toast.makeText(this@NoteEditorActivity,
                        R.string.note_save_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
                // M11: 跨保存清栈
                presenter.history.clear()
            }
        }
    }
```

- [ ] **Step 3: 编译 + 跑测试**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

Expected: 全过（cleanOrphanFiles 是新方法，没必要单测 — 真机走查覆盖 Task 11 #14）。

- [ ] **Step 4: 提交**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt && \
git commit -m "feat(m11): NoteRepository.cleanOrphanFiles + saveNote 后异步清孤儿 + 清栈"
```

---

### Task 11: 全量构建 + 真机走查 + STATUS / memory / docs 收尾

**Files:**
- Modify: `docs/superpowers/STATUS.md`
- Read: `/Users/yichen/.claude/projects/-Users-yichen-cainiao-AI-ClaudeCode-HuaWeiNote/memory/project_hwnote_context.md`
- Modify: `/Users/yichen/.claude/projects/-Users-yichen-cainiao-AI-ClaudeCode-HuaWeiNote/memory/project_hwnote_context.md`
- Modify: `/Users/yichen/.claude/projects/-Users-yichen-cainiao-AI-ClaudeCode-HuaWeiNote/memory/MEMORY.md`
- 归档：`mv docs/superpowers/plans/2026-06-04-hwnote-m11-undo-redo.md docs/superpowers/plans/archived/`

- [ ] **Step 1: clean + assemble + test gate**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:clean :app:assembleDebug :app:test
```

Expected: BUILD SUCCESSFUL，test 总数 ≥ 105（85 基线 + 22 新增 M11）。

- [ ] **Step 2: 真机走查 13 条**

逐条手测（参见 spec §5.3）。每条记录 PASS / FAIL。如发现 FAIL，回到对应 task 修复后回归。

```
1.  输入"hello" → ↶ → 文字消失为空块；↷ → "hello"回来
2.  输入一段文字、暂停 1s 后再输入 → 连按 ↶ 两次分两段回退
3.  加粗某段 → ↶ → 加粗撤销；↷ → 加粗回来
4.  插入图片 → ↶ → 图片消失；↷ → 图片回来
5.  录音生成 AudioBlock → ↶ → 消失；↷ → 回来
6.  删除图片块 → ↶ → 图片块复活（图片显示，文件未丢）
7.  转清单块 ↔ 文本块 → ↶/↷ 切回
8.  应用 H1 → ↶ → H1 退回普通段
9.  切颜色到红色 → ↶ → 颜色恢复
10. 连续 51 次操作 → 最老条目无法 undo
11. 保存后返回再进笔记 → ↶ 按钮灰
12. 切到别笔记再回来 → 撤销栈是新的
13. 手写 Overlay 内画 → 退出 → 顶部 ↶ 不影响手写块
14. 删一张图后保存 → 重新进笔记 → 检查 notes/<id>/images/ 该 fileName 已被遗孤清理（adb shell ls /data/user/0/com.fan.hwnote.app/files/notes/<id>/images/）
```

- [ ] **Step 3: STATUS.md 更新**

读取并修改 `docs/superpowers/STATUS.md`：
- 顶部时间戳改"最后更新：2026-06-04（M11 撤销/重做完成 — PRD §13 全部完成 4/4）"
- "里程碑" 表新增 M11 行：`✅ 完成（2026-06-04）` + 关键产出 = `EditHistoryManager + 5 Command 子类 + 顶部 ↶↷ + 文本防抖 800ms + cleanOrphanFiles`
- 新增 `## M11 完成详情（2026-06-04）` 章节：起因 / 4 维度（model/history 包 / Presenter silent mutator / TextBlockView 防抖 / Activity menu + Repo cleanOrphan）/ 涉及文件清单 / commit 列表 / 测试统计 / 验收 13 条
- 项目完成总览表追加 M11 行
- 累计计数刷新（commit / 测试数）
- "Pending" 段：清空（PRD §13 全部完成；后续如有新需求需重新 brainstorm）

- [ ] **Step 4: memory 更新**

读取 `/Users/yichen/.claude/projects/-Users-yichen-cainiao-AI-ClaudeCode-HuaWeiNote/memory/project_hwnote_context.md`，修改：
- description 改 "11 milestones (M1..M11) complete (2026-06-04). PRD §13 fully shipped..."
- Current state 加 "M11 introduced model/history/ package with Inverse Op Command pattern + 800ms text debounce."
- 里程碑表加 M11 行
- Latest HEAD 改最新 SHA
- Pending: 清空 "M11 ⏳"
- 新增"M11 architectural notes worth remembering"小节：
  - **Silent mutator 边界**：所有"会改 currentBlocks/spans"入口必须经过 silentInsert/silentRemove/silentMove/silentReplace/silentApplySpan/silentReplaceText；silent mutator 自身不调 history.push，避免无限递归。后续若加新 Block 类型（如 VideoBlock）同样需要扩 silent mutator + 走"公共方法 = silent + push"模式
  - **Inverse Op + 预填快照**：RemoveBlockCommand/ReplaceBlockCommand 支持 presnapshot/preOldBlock 参数，让"已经手工 mutate 之后再 push"的调用方零成本入栈
  - **suppressDebounceWhile 守卫**：silentReplaceText 调 EditText.setText 时必须用 view.suppressDebounceWhile { ... } 包裹，否则会触发 TextWatcher 入栈循环
  - **跨保存清栈**：onSaveSuccess 是唯一 clear() 入口（含 onPause 触发的 save 路径）；process death 重建走 onCreate 新 Manager = 空栈
  - **文件遗孤模式**：删块不动文件（保证 undo 可复活）；saveNote 后异步扫 notes/<id>/images audio 与 content.blocks 差集删除。后续若加 VideoBlock 须在 cleanOrphanFiles 内加 videoDir 扫描

修改 `MEMORY.md` 中 HwNote 项一行：
```
- [Project: HwNote context](project_hwnote_context.md) — Huawei-style memo app, M1..M11 complete (2026-06-04), PRD §13 fully shipped
```

- [ ] **Step 5: 归档实施计划**

```bash
mkdir -p /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/docs/superpowers/plans/archived && \
mv /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/docs/superpowers/plans/2026-06-04-hwnote-m11-undo-redo.md \
   /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/docs/superpowers/plans/archived/
```

- [ ] **Step 6: 提交收尾**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && \
git add docs/superpowers/STATUS.md \
        docs/superpowers/plans/archived/2026-06-04-hwnote-m11-undo-redo.md && \
git rm docs/superpowers/plans/2026-06-04-hwnote-m11-undo-redo.md 2>/dev/null || true && \
git commit -m "docs(m11): 标记 M11 撤销/重做完成 + 归档实施计划（PRD §13 全部交付）"
```

memory 文件不在 repo 内，不需 git。

---

## 不动的东西（明确边界）

- M1-M10 所有功能代码、布局、资源 —— 仅在 Task 5-10 明确列出的文件内改动
- HandwritingOverlayView / BrushPainter / StrokeEraser —— **完全不动**
- M10 AudioRecorder / AudioPlayer / AudioRecordingBottomSheet —— 完全不动
- M9 分类系统 / 软删除回收站 / DeleteConfirmBottomSheet —— 完全不动
- 数据层 schema —— **不动**（DB 仍 v2，无 v3 迁移）
- NoteJson 序列化格式 —— **不动**（撤销栈纯内存，不持久化）
- 既有 85 个单测 —— 全部保持 PASS

---

## 验证（真机）

详见 Task 11 Step 2 的 13 条走查清单。任何一条 FAIL 必须修复后才能提交收尾 commit。

---

## 执行方式

Subagent-Driven Development（同 M10）：每任务 implementer DONE → spec reviewer → code quality reviewer → fix（如需）→ re-review → 标记完成。Task 11 仅静态验证 + 真机 + 收尾。

预估 1-2 天。
