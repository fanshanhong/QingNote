package com.fan.hwnote.app.view.block

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.storage.NoteFileStorage

class ImageBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr) {

    private val imageView: ImageView
    private var data: Block.ImageBlock? = null

    /** 调用方（Presenter）注入：传 noteId，让 ImageBlockView 自己解析本地文件路径。 */
    var noteId: Long = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.block_image, this, true)
        imageView = findViewById(R.id.block_image)
        setOnLongClickListener {
            showDeleteDialog()
            true
        }
        imageView.setOnLongClickListener {
            showDeleteDialog()
            true
        }
    }

    override fun bind(block: Block) {
        require(block is Block.ImageBlock) { "ImageBlockView only accepts ImageBlock" }
        data = block
        val storage = NoteFileStorage(context)
        val file = storage.imageFile(noteId, block.fileName)
        Glide.with(imageView)
            .load(file)
            .placeholder(R.drawable.bg_image_block)
            .error(R.drawable.bg_image_block)
            .into(imageView)
    }

    override fun toBlock(): Block = data
        ?: throw IllegalStateException("ImageBlockView.toBlock() called before bind()")

    private fun showDeleteDialog() {
        AlertDialog.Builder(context)
            .setTitle(R.string.image_delete_title)
            .setMessage(R.string.image_delete_message)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                callback?.onRequestDelete(this)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
