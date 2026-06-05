package com.fan.hwnote.app.view.block

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.storage.NoteFileStorage

class ImageBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr) {

    private val imageView: ImageView
    private val btnDelete: ImageView
    private var data: Block.ImageBlock? = null
    private var deleteDialog: AlertDialog? = null

    /** 调用方（Presenter）注入：传 noteId，让 ImageBlockView 自己解析本地文件路径。 */
    var noteId: Long = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.block_image, this, true)
        imageView = findViewById(R.id.block_image)
        btnDelete = findViewById(R.id.btn_delete_image)
        btnDelete.setOnClickListener { callback?.onRequestDelete(this) }
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
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    Toast.makeText(
                        context, R.string.image_load_failed, Toast.LENGTH_SHORT,
                    ).show()
                    callback?.onImageLoadFailed(this@ImageBlockView)
                    return false // 不拦截 error drawable 渲染
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
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

    fun setDeleteVisible(visible: Boolean) {
        btnDelete.visibility = if (visible) VISIBLE else GONE
        imageView.isClickable = visible
        imageView.isLongClickable = visible
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
