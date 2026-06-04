package com.fan.hwnote.app.view.list

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
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Category
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

/**
 * 筛选 BottomSheet：4 个内置入口（全部 / 未分类 / 我的收藏 / 最近删除）
 * + 用户分类列表 + 「管理分类」入口。点击任一项立即回调并 dismiss。
 *
 * 当前选中项靠 view.isSelected 标记（不渲染左侧 4dp 蓝条，留到后续 polish）。
 */
class FilterPickerBottomSheet(
    private val activity: AppCompatActivity,
    private val currentFilter: NoteRepository.ListFilter,
    private val onPick: (NoteRepository.ListFilter) -> Unit,
    private val onManage: () -> Unit,
) : BottomSheetDialog(activity) {

    private lateinit var rowAll: LinearLayout
    private lateinit var rowUncat: LinearLayout
    private lateinit var rowFav: LinearLayout
    private lateinit var rowDeleted: LinearLayout
    private lateinit var rowManage: LinearLayout
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_filter_picker)

        rowAll = findViewById(R.id.row_all)!!
        rowUncat = findViewById(R.id.row_uncategorized)!!
        rowFav = findViewById(R.id.row_favorite)!!
        rowDeleted = findViewById(R.id.row_deleted)!!
        rowManage = findViewById(R.id.row_manage)!!
        recycler = findViewById(R.id.categories_recycler)!!

        rowAll.isSelected = currentFilter is NoteRepository.ListFilter.All
        rowUncat.isSelected = currentFilter is NoteRepository.ListFilter.Uncategorized
        rowFav.isSelected = currentFilter is NoteRepository.ListFilter.Favorite
        rowDeleted.isSelected = currentFilter is NoteRepository.ListFilter.Deleted

        rowAll.setOnClickListener { pick(NoteRepository.ListFilter.All) }
        rowUncat.setOnClickListener { pick(NoteRepository.ListFilter.Uncategorized) }
        rowFav.setOnClickListener { pick(NoteRepository.ListFilter.Favorite) }
        rowDeleted.setOnClickListener { pick(NoteRepository.ListFilter.Deleted) }
        rowManage.setOnClickListener {
            dismiss()
            onManage()
        }

        recycler.layoutManager = LinearLayoutManager(context)

        activity.lifecycleScope.launch {
            val categories = CategoryRepository.list()
            val selectedCategoryId = (currentFilter as? NoteRepository.ListFilter.Category)?.id
            recycler.adapter = CategoryRowAdapter(categories, selectedCategoryId) { cat ->
                pick(NoteRepository.ListFilter.Category(cat.id))
            }
        }
    }

    private fun pick(filter: NoteRepository.ListFilter) {
        onPick(filter)
        dismiss()
    }

    private class CategoryRowAdapter(
        private val items: List<Category>,
        private val selectedId: Long?,
        private val onClick: (Category) -> Unit,
    ) : RecyclerView.Adapter<CategoryRowAdapter.VH>() {

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
