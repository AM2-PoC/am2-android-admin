package com.am2.admin.data.model

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val admin_id: Int?,
    val username: String?,
    val role: String?
)