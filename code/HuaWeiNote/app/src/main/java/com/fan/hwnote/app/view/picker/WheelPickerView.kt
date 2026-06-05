package com.fan.hwnote.app.view.picker

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R

class WheelPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val ITEM_HEIGHT_DP = 40
        private const val VISIBLE_ITEMS = 5
        private const val WRAP_MULTIPLIER = 1000
    }

    var wrapSelectorWheel: Boolean = false

    private val itemHeightPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, ITEM_HEIGHT_DP.toFloat(), resources.displayMetrics
    ).toInt()

    private var items: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var onValueChangedListener: ((oldIndex: Int, newIndex: Int) -> Unit)? = null

    private val adapter = WheelAdapter()
    private val layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    private val snapHelper = LinearSnapHelper()
    private val recyclerView: RecyclerView

    init {
        val totalHeight = VISIBLE_ITEMS * itemHeightPx
        val sidePadding = (VISIBLE_ITEMS / 2) * itemHeightPx

        recyclerView = RecyclerView(context).apply {
            this.layoutManager = this@WheelPickerView.layoutManager
            this.adapter = this@WheelPickerView.adapter
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
            setPadding(0, sidePadding, 0, sidePadding)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, totalHeight)
        }
        snapHelper.attachToRecyclerView(recyclerView)
        addView(recyclerView)

        val dividerColor = ContextCompat.getColor(context, R.color.divider)
        val dividerHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics
        ).toInt().coerceAtLeast(1)

        val topDivider = View(context).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dividerHeight).apply {
                topMargin = sidePadding
            }
        }
        addView(topDivider)

        val bottomDivider = View(context).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dividerHeight).apply {
                topMargin = sidePadding + itemHeightPx
            }
        }
        addView(bottomDivider)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                applyAlphaEffect(rv)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCurrentIndex()
                }
            }
        })
    }

    fun setItems(items: List<String>) {
        this.items = items
        adapter.notifyDataSetChanged()
        if (items.isNotEmpty()) {
            setSelectedIndex(0)
        }
    }

    fun setSelectedIndex(index: Int) {
        if (items.isEmpty()) return
        val safeIndex = index.coerceIn(0, items.size - 1)
        currentIndex = safeIndex
        val targetPosition = if (wrapSelectorWheel) {
            (WRAP_MULTIPLIER / 2) * items.size + safeIndex
        } else {
            safeIndex
        }
        layoutManager.scrollToPositionWithOffset(targetPosition, 0)
        recyclerView.post { applyAlphaEffect(recyclerView) }
    }

    fun getSelectedIndex(): Int = currentIndex

    fun setOnValueChangedListener(listener: ((oldIndex: Int, newIndex: Int) -> Unit)?) {
        onValueChangedListener = listener
    }

    private fun applyAlphaEffect(rv: RecyclerView) {
        val center = rv.height / 2f
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val childCenter = child.top + child.height / 2f
            val distance = Math.abs(center - childCenter) / (2.5f * itemHeightPx)
            child.alpha = Math.max(0.3f, 1f - distance)
        }
    }

    private fun updateCurrentIndex() {
        val snapView = snapHelper.findSnapView(layoutManager) ?: return
        val position = layoutManager.getPosition(snapView)
        if (position == RecyclerView.NO_POSITION || items.isEmpty()) return
        val realIndex = position % items.size
        if (realIndex != currentIndex) {
            val old = currentIndex
            currentIndex = realIndex
            onValueChangedListener?.invoke(old, realIndex)
        }
    }

    private inner class WheelAdapter : RecyclerView.Adapter<WheelAdapter.VH>() {

        inner class VH(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, itemHeightPx
                )
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            if (items.isEmpty()) return
            val realPos = position % items.size
            holder.textView.text = items[realPos]
        }

        override fun getItemCount(): Int =
            if (items.isEmpty()) 0
            else if (wrapSelectorWheel) items.size * WRAP_MULTIPLIER
            else items.size
    }
}
