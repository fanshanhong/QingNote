package com.fan.hwnote.app.view.picker

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.fan.hwnote.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateTimePickerDialog(
    context: Context,
    private val initialEpoch: Long,
    private val onDateTimeSelected: (Long) -> Unit,
) : BottomSheetDialog(context) {

    private val baseCalendar = Calendar.getInstance()
    private val dateStrings = mutableListOf<String>()
    private val dateCalendars = mutableListOf<Calendar>()
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.CHINESE)

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_datetime_picker, null)
        setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tv_date_title)
        val pickerDate = view.findViewById<WheelPickerView>(R.id.picker_date)
        val pickerAmPm = view.findViewById<WheelPickerView>(R.id.picker_ampm)
        val pickerHour = view.findViewById<WheelPickerView>(R.id.picker_hour)
        val pickerMinute = view.findViewById<WheelPickerView>(R.id.picker_minute)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)

        buildDateList()

        val initCal = Calendar.getInstance()
        if (initialEpoch > 0) {
            initCal.timeInMillis = initialEpoch
        } else {
            roundToNext5Minutes(initCal)
        }

        pickerDate.setItems(dateStrings)
        pickerDate.wrapSelectorWheel = false
        pickerDate.setSelectedIndex(findDateIndex(initCal))

        pickerAmPm.setItems(listOf(
            context.getString(R.string.picker_datetime_am),
            context.getString(R.string.picker_datetime_pm),
        ))
        pickerAmPm.wrapSelectorWheel = false
        pickerAmPm.setSelectedIndex(if (initCal.get(Calendar.AM_PM) == Calendar.AM) 0 else 1)

        pickerHour.setItems((1..12).map { it.toString() })
        pickerHour.wrapSelectorWheel = true
        val h = initCal.get(Calendar.HOUR)
        pickerHour.setSelectedIndex(if (h == 0) 11 else h - 1)

        pickerMinute.setItems((0..59).map { String.format("%02d", it) })
        pickerMinute.wrapSelectorWheel = true
        pickerMinute.setSelectedIndex(initCal.get(Calendar.MINUTE))

        updateTitle(tvTitle, pickerDate.getSelectedIndex())

        pickerDate.setOnValueChangedListener { _, newVal ->
            updateTitle(tvTitle, newVal)
        }

        btnCancel.setOnClickListener { dismiss() }

        btnConfirm.setOnClickListener {
            val cal = dateCalendars[pickerDate.getSelectedIndex()].clone() as Calendar
            val amPm = if (pickerAmPm.getSelectedIndex() == 0) Calendar.AM else Calendar.PM
            var hour = pickerHour.getSelectedIndex() + 1
            if (hour == 12) hour = 0
            cal.set(Calendar.AM_PM, amPm)
            cal.set(Calendar.HOUR, hour)
            cal.set(Calendar.MINUTE, pickerMinute.getSelectedIndex())
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onDateTimeSelected(cal.timeInMillis)
            dismiss()
        }
    }

    private fun buildDateList() {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        for (i in 0 until 365) {
            val day = today.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, i)
            dateCalendars += day
            dateStrings += if (i == 0) {
                context.getString(R.string.picker_datetime_today)
            } else {
                "${day.get(Calendar.MONTH) + 1}月${day.get(Calendar.DAY_OF_MONTH)}日"
            }
        }
    }

    private fun findDateIndex(cal: Calendar): Int {
        val target = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in dateCalendars.indices) {
            if (dateCalendars[i].get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && dateCalendars[i].get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            ) return i
        }
        return 0
    }

    private fun updateTitle(tv: TextView, dateIndex: Int) {
        val cal = dateCalendars[dateIndex]
        val dow = dayOfWeekFormat.format(cal.time)
        tv.text = context.getString(
            R.string.picker_datetime_title_format,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            dow,
        )
    }

    companion object {
        fun roundToNext5Minutes(cal: Calendar) {
            val min = cal.get(Calendar.MINUTE)
            val remainder = min % 5
            if (remainder != 0) {
                cal.add(Calendar.MINUTE, 5 - remainder)
            } else {
                cal.add(Calendar.MINUTE, 5)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
    }
}
