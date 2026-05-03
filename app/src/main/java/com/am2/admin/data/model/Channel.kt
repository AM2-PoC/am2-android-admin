package com.am2.admin.data.model

data class Channel(
    val id: Int,
    val name: String,
    val display_name: String,
    val category: String,
    val creator_name: String?,
    val online_count: Int,
    val total_access: Int = 0
)
