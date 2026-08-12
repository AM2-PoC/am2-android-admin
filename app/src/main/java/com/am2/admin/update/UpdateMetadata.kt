package com.am2.admin.update

import com.am2.admin.data.model.UpdateInfo

data class UpdateMetadata(
    val versionCode: Long,
    val versionName: String,
    val updateUrl: String,
    val sha256: String,
    val signerSha256: String,
    val changelog: String
) {
    companion object {
        const val APPROVED_URL = "https://webadmin.am2-poc.com/update/admin.apk"
        private val DIGEST = Regex("^[0-9a-f]{64}$")

        fun from(info: UpdateInfo): UpdateMetadata {
            val name = info.version_name.trim()
            val sha = normalize(info.sha256)
            val signer = normalize(info.signer_sha256)
            require(info.version_code > 0) { "version_code invalid" }
            require(name.isNotEmpty()) { "version_name invalid" }
            require(info.update_url.trim() == APPROVED_URL) { "update_url not approved" }
            require(DIGEST.matches(sha)) { "sha256 invalid" }
            require(DIGEST.matches(signer)) { "signer_sha256 invalid" }
            return UpdateMetadata(info.version_code, name, APPROVED_URL, sha, signer, info.changelog)
        }

        fun normalize(value: String): String = value.replace(":", "").trim().lowercase()
    }
}
