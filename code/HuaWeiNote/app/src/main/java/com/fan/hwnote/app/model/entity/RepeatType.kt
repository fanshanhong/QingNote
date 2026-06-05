package com.fan.hwnote.app.model.entity

enum class RepeatType(val value: Int) {
    NONE(0), DAILY(1), WEEKLY(2), MONTHLY(3), YEARLY(4);
    companion object {
        fun fromValue(v: Int): RepeatType =
            entries.firstOrNull { it.value == v } ?: NONE
    }
}
