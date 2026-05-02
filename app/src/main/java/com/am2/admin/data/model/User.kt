package com.am2.admin.data.model

data class User(
    val id: String,
    val name: String,
    val status: String,
    val admin_id: Int?,
    val enable_maps: Boolean,
    val enable_p2p: Boolean,
    val enable_ptt_video: Boolean,
    val duplex_mode: String?,
    val current_channel: String?
)