package com.fan.hwnote.app.view.handwriting

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke

/**
 * 手写透明覆盖层。位于 ScrollView 内 FrameLayout，与内容层 LinearLayout 同级覆盖。
 *
 * 模式：
 *   isHandwritingMode=false → onTouchEvent 返回 false 让事件继续下传
 *   isHandwritingMode=true  → onTouchEvent 全部消费；落笔/移动/抬起组装 Stroke
 *
 * 撤销栈：Add(stroke) / Erase(list) 两种 Action，自洽于 view 内。
 */
class HandwritingOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    sealed class Action {
        data class Add(val stroke: Stroke) : Action()
        data class Erase(val items: List<IndexedValue<Stroke>>) : Action()
    }

    private val brushPainter = BrushPainter(context)

    private val strokes = mutableListOf<Stroke>()
    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()

    var isHandwritingMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // 切换模式时丢掉正在收集的中间 stroke（双向都清，防残留）
            inProgressPoints.clear()
            erasedThisGesture.clear()
            invalidate()
        }

    var currentBrush: BrushType = BrushType.PEN
    var currentColor: String = "#212121"
    /** dp，PRD §6.4 三档：1=细 / 3=中 / 6=粗 */
    var currentWidth: Int = 3
    var isErasing: Boolean = false
    /** 橡皮接触半径（px），UI 不让用户调；默认 12dp 等效 */
    private val eraserRadiusPx: Float =
        12f * context.resources.displayMetrics.density

    /** Task 5 收集中：当前手指未抬起的采样点（绘制用）。 */
    internal val inProgressPoints = mutableListOf<Triple<Int, Int, Int>>()
    internal var gestureStartElapsedMs = 0L

    /** Task 5 橡皮收集：本次按下到抬起命中的所有 strokes（聚合一个 Erase action）。 */
    internal val erasedThisGesture = mutableListOf<Stroke>()

    fun setStrokes(list: List<Stroke>) {
        strokes.clear()
        strokes.addAll(list)
        undoStack.clear()
        redoStack.clear()
        invalidate()
    }

    fun getStrokes(): List<Stroke> = strokes.toList()

    fun clear() {
        if (strokes.isEmpty()) return
        val snapshot = strokes.withIndex().toList()
        undoStack.addLast(Action.Erase(snapshot))
        redoStack.clear()
        strokes.clear()
        invalidate()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        val a = undoStack.removeLastOrNull() ?: return
        when (a) {
            is Action.Add -> { strokes.remove(a.stroke) }
            is Action.Erase -> {
                a.items.sortedBy { it.index }.forEach { (idx, stroke) ->
                    val safeIdx = idx.coerceAtMost(strokes.size)
                    strokes.add(safeIdx, stroke)
                }
            }
        }
        redoStack.addLast(a)
        invalidate()
    }

    fun redo() {
        val a = redoStack.removeLastOrNull() ?: return
        when (a) {
            is Action.Add -> { strokes.add(a.stroke) }
            is Action.Erase -> {
                val toRemove = a.items.map { it.value }.toHashSet()
                strokes.removeAll(toRemove)
            }
        }
        undoStack.addLast(a)
        invalidate()
    }

    /** T5 mutator: append stroke and push Add to undo. Caller must invalidate(). */
    internal fun pushAdd(stroke: Stroke) {
        strokes.add(stroke)
        undoStack.addLast(Action.Add(stroke))
        redoStack.clear()
    }

    /** T5 mutator: clear redo and push Erase to undo. Caller must remove strokes from strokesRef AND invalidate(). */
    internal fun pushErase(items: List<IndexedValue<Stroke>>) {
        if (items.isEmpty()) return
        undoStack.addLast(Action.Erase(items))
        redoStack.clear()
    }

    internal fun eraserRadius(): Float = eraserRadiusPx
    internal fun brushPainter(): BrushPainter = brushPainter
    internal fun strokesRef(): List<Stroke> = strokes

    // Task 5 接管
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    // Task 5 接管
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
