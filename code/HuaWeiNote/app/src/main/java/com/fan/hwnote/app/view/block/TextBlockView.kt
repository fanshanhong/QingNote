package com.fan.hwnote.app.view.block

import android.content.Context
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.EditText
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.util.applyTo
import com.fan.hwnote.app.util.toTextSpans

/**
 * 文本块视图：单个 EditText + heading 字号 + Span 应用。
 *
 * 不要直接在外部读 [edit].text 然后判断；Presenter 通过 [toBlock] 拿到结构化数据。
 */
class TextBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr) {

    private lateinit var blockId: String
    private var heading: Heading? = null
    private var alignment: Alignment? = null
    private var listType: ListType? = null
    private var indentLevel: Int = 0
    val edit: EditText
    private val listMarker: android.widget.TextView

    /** Presenter 注入：把 pending 样式应用到刚插入的文字范围。 */
    var pendingApplier: ((android.text.Spannable, Int, Int) -> Unit)? = null

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

    init {
        LayoutInflater.from(context).inflate(R.layout.block_text, this, true)
        edit = findViewById(R.id.block_edit)
        listMarker = findViewById(R.id.list_marker)
        wireListeners()
    }

    override fun bind(block: Block) {
        require(block is Block.TextBlock) { "TextBlockView only binds TextBlock" }
        blockId = block.id
        heading = block.heading
        applyHeadingSize()
        alignment = block.alignment
        listType = block.listType
        indentLevel = block.indentLevel
        applyAlignment()
        applyIndentation()
        val spannable = SpannableString(block.text)
        block.spans.applyTo(spannable)
        suppressDebounceWhile {
            edit.setText(spannable)
        }
    }

    override fun toBlock(): Block.TextBlock {
        val spannable = SpannableString(edit.text)
        return Block.TextBlock(
            id = blockId,
            heading = heading,
            text = spannable.toString(),
            spans = spannable.toTextSpans(),
            alignment = alignment,
            listType = listType,
            indentLevel = indentLevel,
        )
    }

    /** Presenter 调：切换 H1/H2/正文。 */
    fun setHeading(h: Heading?) {
        heading = h
        applyHeadingSize()
    }

    fun currentHeading(): Heading? = heading

    /** 让 Presenter 把焦点交给这块（新块、删除上一块时上移焦点都用）。 */
    fun focusEditEnd() {
        edit.requestFocus()
        edit.setSelection(edit.text.length)
    }

    fun setEditable(editable: Boolean) {
        edit.isFocusableInTouchMode = editable
        edit.isFocusable = editable
        edit.isCursorVisible = editable
        edit.isClickable = editable
        edit.isLongClickable = editable
    }

    fun setListMarker(text: String) {
        listMarker.text = text
        listMarker.visibility = android.view.View.VISIBLE
    }

    fun hideListMarker() {
        listMarker.visibility = android.view.View.GONE
    }

    fun setAlignment(a: Alignment?) {
        alignment = a
        applyAlignment()
    }

    fun currentAlignment(): Alignment? = alignment

    fun setListType(lt: ListType?) {
        listType = lt
    }

    fun currentListType(): ListType? = listType

    fun setIndentLevel(level: Int) {
        indentLevel = level
        applyIndentation()
    }

    fun currentIndentLevel(): Int = indentLevel

    private fun applyAlignment() {
        val gravity = when (alignment) {
            Alignment.START, null -> android.view.Gravity.START
            Alignment.CENTER -> android.view.Gravity.CENTER_HORIZONTAL
            Alignment.END -> android.view.Gravity.END
        }
        edit.gravity = gravity or android.view.Gravity.TOP
    }

    private fun applyIndentation() {
        val indentPx = (indentLevel * resources.getDimension(R.dimen.editor_indent_unit)).toInt()
        setPadding(indentPx, paddingTop, paddingRight, paddingBottom)
    }

    private fun applyHeadingSize() {
        val sp = when (heading) {
            Heading.H1 -> resources.getDimension(R.dimen.editor_text_h1_v2)
            Heading.H2 -> resources.getDimension(R.dimen.editor_text_h2_v2)
            Heading.H3 -> resources.getDimension(R.dimen.editor_text_h3)
            Heading.H4 -> resources.getDimension(R.dimen.editor_text_h4)
            Heading.H5 -> resources.getDimension(R.dimen.editor_text_h5)
            Heading.H6 -> resources.getDimension(R.dimen.editor_text_h6)
            null -> resources.getDimension(R.dimen.editor_text_normal)
        }
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp)
    }

    private fun wireListeners() {
        edit.setOnFocusChangeListener { _, focused ->
            if (focused) {
                callback?.onFocusGained(this)
            } else {
                flushPendingTextEdit()
            }
        }

        // 末尾按回车 → 上抛 split；中间按回车 → 让 EditText 自己换行
        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val sel = edit.selectionStart
                if (sel == edit.text.length) {
                    callback?.onRequestSplitAfter(this)
                    return@setOnKeyListener true
                }
            }
            // 空块按退格 → 上抛 delete
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                if (edit.text.isEmpty()) {
                    callback?.onRequestDelete(this)
                    return@setOnKeyListener true
                }
            }
            false
        }

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
    }

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
}
