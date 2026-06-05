package com.fan.hwnote.app.view.picker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Calendar

class DateTimePickerDialogTest {

    @Test
    fun `roundToNext5Minutes - exact multiple rounds up by 5`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 5, 14, 30, 15)
            set(Calendar.MILLISECOND, 500)
        }
        DateTimePickerDialog.roundToNext5Minutes(cal)
        assertEquals(35, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `roundToNext5Minutes - not on boundary rounds to next 5`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 5, 14, 37, 20)
        }
        DateTimePickerDialog.roundToNext5Minutes(cal)
        assertEquals(40, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun `roundToNext5Minutes - minute 58 crosses hour`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 5, 14, 58, 0)
        }
        DateTimePickerDialog.roundToNext5Minutes(cal)
        assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }
}
