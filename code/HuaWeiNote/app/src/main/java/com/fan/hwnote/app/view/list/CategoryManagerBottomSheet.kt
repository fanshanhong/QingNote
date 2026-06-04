package com.fan.hwnote.app.view.list

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.CategoryRepository
import com.fan.hwnote.app.model.entity.Category
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * 分类管理 BottomSheet（M9 T8）。
 *
 * 功能：
 * - 展示全部用户分类（顺序按 order_index）。
 * - 长按行拖动重新排序，松手后 flush 到 CategoryRepository.reorder。
 * - 点击「编辑」按钮：弹 AlertDialog（dialog_category_editor.xml），可改名 + 选 4 色之一。
 * - 点击「删除」按钮：弹 AlertDialog 二次确认；确认后 CategoryRepository.delete
 *   （该分类下笔记由 Repository 自动 SET category_id = NULL）。
 * - 底部「新建分类」入口同样走 dialog_category_editor.xml。
 *
 * 任一 CRUD/排序操作完成后，会回调外部 onChanged()，让 NoteListActivity 刷新筛选 chip / 列表。
 *
 * DeleteConfirm 暂用 AlertDialog 占位（T9 实现 DeleteConfirmBottomSheet 后替换）。
 */
class CategoryManagerBottomSheet(
    private val activity: AppCompatActivity,
    private val onChanged: () -> Unit,
) : BottomSheetDialog(activity) {

    private lateinit var recycler: RecyclerView
    private lateinit var btnNew: LinearLayout
    private val items: MutableList<Category> = mutableListOf()
    private lateinit var adapter: ManageAdapter
    private lateinit var touchHelper: ItemTouchHelper

    private val colorHexs = listOf("#FDD835", "#00897B", "#43A047", "#E53935")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_category_manager)

        recycler = findViewById(R.id.manager_recycler)!!
        btnNew = findViewById(R.id.btn_new_category)!!

        adapter = ManageAdapter(
            items = items,
            onEdit = { cat -> onEditClicked(cat) },
            onDelete = { cat -> onDeleteClicked(cat) },
        )
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                Collections.swap(items, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                val orderedIds = items.map { it.id }
                activity.lifecycleScope.launch {
                    CategoryRepository.reorder(orderedIds)
                    onChanged()
                }
            }
        })
        touchHelper.attachToRecyclerView(recycler)

        btnNew.setOnClickListener { onEditClicked(null) }

        reload()
    }

    private fun reload() {
        activity.lifecycleScope.launch {
            val list = CategoryRepository.list()
            items.clear()
            items.addAll(list)
            adapter.notifyDataSetChanged()
        }
    }

    private fun onEditClicked(cat: Category?) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_category_editor, null)
        val nameInput = view.findViewById<EditText>(R.id.name_input)
        val colorIds = listOf(R.id.color_0, R.id.color_1, R.id.color_2, R.id.color_3)

        var pickedColor: String = cat?.color ?: colorHexs[0]
        nameInput.setText(cat?.name ?: "")

        fun refreshSelection() {
            colorIds.forEachIndexed { idx, id ->
                val dot = view.findViewById<ImageView>(id)
                val tint = runCatching { Color.parseColor(colorHexs[idx]) }
                    .getOrDefault(Color.GRAY)
                ImageViewCompat.setImageTintList(
                    dot,
                    android.content.res.ColorStateList.valueOf(tint),
                )
                dot.isSelected = (colorHexs[idx] == pickedColor)
            }
        }
        refreshSelection()

        colorIds.forEachIndexed { idx, id ->
            view.findViewById<View>(id).setOnClickListener {
                pickedColor = colorHexs[idx]
                refreshSelection()
            }
        }

        val title = if (cat == null) R.string.category_new else R.string.category_edit
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                activity.lifecycleScope.launch {
                    if (cat == null) {
                        CategoryRepository.insert(name, pickedColor)
                    } else {
                        CategoryRepository.update(cat.id, name, pickedColor)
                    }
                    reload()
                    onChanged()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onDeleteClicked(cat: Category) {
        AlertDialog.Builder(context)
            .setTitle(R.string.category_delete_title)
            .setMessage(context.getString(R.string.category_delete_message, cat.name))
            .setPositiveButton(R.string.action_ok) { _, _ ->
                activity.lifecycleScope.launch {
                    CategoryRepository.delete(cat.id)
                    reload()
                    onChanged()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private class ManageAdapter(
        private val items: List<Category>,
        private val onEdit: (Category) -> Unit,
        private val onDelete: (Category) -> Unit,
    ) : RecyclerView.Adapter<ManageAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_manage, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = items[position]
            holder.bind(cat, onEdit, onDelete)
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val dot: ImageView = view.findViewById(R.id.color_dot)
            private val name: TextView = view.findViewById(R.id.name)
            private val btnEdit: ImageView = view.findViewById(R.id.btn_edit)
            private val btnDelete: ImageView = view.findViewById(R.id.btn_delete)

            fun bind(
                cat: Category,
                onEdit: (Category) -> Unit,
                onDelete: (Category) -> Unit,
            ) {
                name.text = cat.name
                val tint = runCatching { Color.parseColor(cat.color) }.getOrDefault(Color.GRAY)
                ImageViewCompat.setImageTintList(
                    dot,
                    android.content.res.ColorStateList.valueOf(tint),
                )
                btnEdit.setOnClickListener { onEdit(cat) }
                btnDelete.setOnClickListener { onDelete(cat) }
            }
        }
    }
}
