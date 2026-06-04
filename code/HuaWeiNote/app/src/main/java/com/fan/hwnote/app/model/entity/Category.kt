package com.fan.hwnote.app.model.entity

data class Category(
    val id: Long = 0L,
    val name: String,
    val color: String,            // hex "#FDD835"
    val orderIndex: Int = 0,
)
