package com.am2.admin.data.model

data class UpdateInfo(
    val version_code: Long,
    val version_name: String,
    val update_url: String,
    val sha256: String,
    val signer_sha256: String,
    val changelog: String = ""
)
