package com.fan.hwnote.app.model.entity

/**
 * 笔记内容块。三种变体：文本 / 图片 / 清单。
 * id 是块在笔记内的稳定标识（"b-001" 形式或 UUID 短串）；编辑器据此识别块。
 *
 * 字段标 var 是有意为之：编辑器 View 层会就地修改（用户输入触发 TextWatcher → 更新 text/spans）。
 * 数据类 equals/hashCode 仍按当前 var 值计算，不影响测试。
 */
sealed class Block {
    abstract val id: String

    data class TextBlock(
        override val id: String,
        var heading: Heading? = null,
        var text: String = "",
        var spans: List<TextSpan> = emptyList(),
    ) : Block()

    data class ImageBlock(
        override val id: String,
        var fileName: String,
        var width: Int,
        var height: Int,
    ) : Block()

    data class ChecklistBlock(
        override val id: String,
        var items: MutableList<ChecklistItem> = mutableListOf(),
    ) : Block()
}

/** 清单内单项。 */
data class ChecklistItem(var checked: Boolean, var text: String)

/** 文本块的标题级别（块级属性，不写在 spans 里）。 */
enum class Heading { H1, H2 }
