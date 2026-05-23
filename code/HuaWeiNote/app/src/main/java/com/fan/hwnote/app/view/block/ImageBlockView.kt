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
    private var deleteDialog: AlertDialog? = null

    /** 调用方（Presenter）注入：传 noteId，让 ImageBlockView 自己解析本地文件路径。 */
    var noteId: Long = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.block_image, this, true)
        imageView = findViewById(R.id.block_image)
        imageView.setOnLongClickListener {
            showDeleteDialog()
            true
        }
    }

    override fun bind(block: Block) {
        require(block is Block.ImageBlock) { "ImageBlockView only accepts ImageBlock" }
        check(noteId != 0L) { "ImageBlockView.bind() called before noteId was set" }
        data = block
        val storage = NoteFileStorage(context)
        val file = storage.imageFile(noteId, block.fileName)
        Glide.with(imageView)
            .load(file)
            .error(R.drawable.bg_image_block)
            .dontAnimate()
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    android.widget.Toast.makeText(
                        context, R.string.image_load_failed, android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    val cb = callback
                    if (cb is com.fan.hwnote.app.controller.editor.EditorPresenter) {
                        cb.removeImageBlockOnLoadFailure(this@ImageBlockView)
                    } else {
                        cb?.onRequestDelete(this@ImageBlockView)
                    }
                    return false // 不拦截 error drawable 渲染
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean,
                ): Boolean = false
            })
            .into(imageView)
    }

    override fun toBlock(): Block = data
        ?: throw IllegalStateException("ImageBlockView.toBlock() called before bind()")

    override fun onDetachedFromWindow() {
        deleteDialog?.dismiss()
        deleteDialog = null
        super.onDetachedFromWindow()
    }

    private fun showDeleteDialog() {
        deleteDialog?.dismiss()
        deleteDialog = AlertDialog.Builder(context)
            .setTitle(R.string.image_delete_title)
            .setMessage(R.string.image_delete_message)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                callback?.onRequestDelete(this)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setOnDismissListener { deleteDialog = null }
            .show()
    }
}
