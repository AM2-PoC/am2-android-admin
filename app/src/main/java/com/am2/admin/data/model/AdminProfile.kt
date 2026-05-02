package com.am2.admin.data.model

data class AdminProfile(
    val username: String,
    val role: String,
    val user_quota: Int,
    val channel_quota: Int,
    val used_user_quota: Int,
    val used_channel_quota: Int,
    val can_manage_maps: Boolean,
    val can_manage_p2p: Boolean,
    val can_manage_video: Boolean
)
