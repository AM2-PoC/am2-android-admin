package com.am2.admin.data.model

data class UserAccess(
    val id: String,
    val name: String,
    val allowed_channels: String?,
    val channel_ids_json: List<Int>?,
    val permissions_json: List<String>?,
    val default_id: Int?
)