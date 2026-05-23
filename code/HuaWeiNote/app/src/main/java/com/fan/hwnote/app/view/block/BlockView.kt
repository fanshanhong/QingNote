package com.fan.hwnote.app.view.block

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.fan.hwnote.app.model.entity.Block

/**
 * 编辑器内每一个内容块的视图基类。
 * 子类负责把 Block 数据渲染到 UI，并能反向把 UI 当前状态收集回一个新 Block。
 *
 * 通信通过 [callback] 上抛 — Presenter 持有 BlockView 列表，注入回调。
 */
abstract class BlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var callback: Callback? = null

    init {
        orientation = VERTICAL
    }

    abstract fun bind(block: Block)
    abstract fun toBlock(): Block

    /** 上抛给 Presenter 的事件。 */
    interface Callback {
        /** 用户在末尾按回车，要求在该块后面插入新块。 */
        fun onRequestSplitAfter(view: BlockView)
        /** 用户在空块按退格，要求删除该块并把焦点上移。 */
        fun onRequestDelete(view: BlockView)
        /** 用户聚焦到这块（用于 Presenter 记录 currentFocus）。 */
        fun onFocusGained(view: BlockView)
        /** 图片块加载失败的回调。默认行为：等同请求删除；Presenter 可覆写以做"第一块退化为空 TextBlock"等特殊处理。 */
        fun onImageLoadFailed(view: BlockView) {
            onRequestDelete(view)
        }
    }
}
