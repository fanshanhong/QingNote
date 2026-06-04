package com.fan.hwnote.app.view.editor

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.CategoryRepository
import com.fan.hwnote.app.model.entity.Category
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

/**
 * 编辑器内"移动到"分类选择 BottomSheet（M9 T10）。
 *
 * 行为：
 * - 顶部固定一行「未分类」（对应 categoryId = null）。
 * - 下方滚动展示全部用户分类（按 CategoryRepository.list 的 orderIndex 排序）。
 * - 点击任一项回调 onPick(Long?) 并 dismiss；null 表示「未分类」。
 *
 * 与 FilterPickerBottomSheet 的差异：不带「全部 / 我的收藏 / 最近删除 / 管理分类」入口，
 * 因为这里是给单条笔记重新归类，而不是切换列表筛选。
 */
class CategoryPickerBottomSheet(
    private val activity: AppCompatActivity,
    private val currentCategoryId: Long?,
    private val onPick: (Long?) -> Unit,
) : BottomSheetDialog(activity) {

    private lateinit var rowUncategorized: LinearLayout
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_category_picker)

        rowUncategorized = findViewById(R.id.row_uncategorized)!!
        recycler = findViewById(R.id.categories_recycler)!!

        rowUncategorized.isSelected = (currentCategoryId == null)
        rowUncategorized.setOnClickListener {
            dismiss()
            onPick(null)
        }

        recycler.layoutManager = LinearLayoutManager(context)

        activity.lifecycleScope.launch {
            val categories = CategoryRepository.list()
            recycler.adapter = PickerAdapter(categories, currentCategoryId) { cat ->
                dismiss()
                onPick(cat.id)
            }
        }
    }

    private class PickerAdapter(
        private val items: List<Category>,
        private val selectedId: Long?,
        private val onClick: (Category) -> Unit,
    ) : RecyclerView.Adapter<PickerAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_filter_row, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = items[position]
            holder.name.text = cat.name
            val tint = runCatching { Color.parseColor(cat.color) }.getOrDefault(Color.GRAY)
            ImageViewCompat.setImageTintList(
                holder.dot,
                android.content.res.ColorStateList.valueOf(tint),
            )
            holder.itemView.isSelected = (selectedId != null && selectedId == cat.id)
            holder.itemView.setOnClickListener { onClick(cat) }
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val dot: ImageView = view.findViewById(R.id.color_dot)
            val name: TextView = view.findViewById(R.id.name)
        }
    }
}
