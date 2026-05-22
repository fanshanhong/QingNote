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
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
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
    val edit: EditText

    /** Presenter 注入：把 pending 样式应用到刚插入的文字范围。 */
    var pendingApplier: ((android.text.Spannable, Int, Int) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.block_text, this, true)
        edit = findViewById(R.id.block_edit)
        wireListeners()
    }

    override fun bind(block: Block) {
        require(block is Block.TextBlock) { "TextBlockView only binds TextBlock" }
        blockId = block.id
        heading = block.heading
        applyHeadingSize()
        val spannable = SpannableString(block.text)
        block.spans.applyTo(spannable)
        edit.setText(spannable)
    }

    override fun toBlock(): Block.TextBlock {
        val spannable = SpannableString(edit.text)
        return Block.TextBlock(
            id = blockId,
            heading = heading,
            text = spannable.toString(),
            spans = spannable.toTextSpans(),
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

    private fun applyHeadingSize() {
        val sp = when (heading) {
            Heading.H1 -> resources.getDimension(R.dimen.editor_text_h1)
            Heading.H2 -> resources.getDimension(R.dimen.editor_text_h2)
            null -> resources.getDimension(R.dimen.editor_text_normal)
        }
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp)
    }

    private fun wireListeners() {
        edit.setOnFocusChangeListener { _, focused ->
            if (focused) callback?.onFocusGained(this)
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                insertStart = start
                insertCount = count - before  // 净插入量；正常打字 count=1 before=0
            }
            override fun afterTextChanged(s: Editable?) {
                if (insertCount > 0 && s is android.text.Spannable) {
                    pendingApplier?.invoke(s, insertStart, insertCount)
                }
            }
        })
    }
}
