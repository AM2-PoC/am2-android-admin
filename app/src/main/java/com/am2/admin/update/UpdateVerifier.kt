package com.am2.admin.update

import android.content.pm.PackageManager
import android.os.Build
import com.am2.admin.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object UpdateVerifier {
    fun verify(file: File, metadata: UpdateMetadata, installedVersionCode: Long, packageManager: PackageManager): Boolean {
        var valid = false
        try {
            if (!com.am2.admin.BuildConfig.SELF_UPDATE_ENABLED) return false
            if (!file.isFile || file.length() < 100 * 1024L) return false
            if (metadata.versionCode <= installedVersionCode || sha256(file) != metadata.sha256) return false
            val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            @Suppress("DEPRECATION")
            val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
            if (archive.packageName != com.am2.admin.BuildConfig.APPLICATION_ID) return false
            @Suppress("DEPRECATION")
            val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
            if (version != metadata.versionCode) return false
            val approved = UpdateMetadata.normalize(BuildConfig.APPROVED_UPDATE_SIGNER_SHA256)
            if (approved.length != 64 || approved != metadata.signerSha256) return false
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = archive.signingInfo ?: return false
                signingInfo.apkContentsSigners.toList()
            } else {
                @Suppress("DEPRECATION") archive.signatures?.toList().orEmpty()
            }
            valid = signatures.any { sha256(it.toByteArray()) == approved }
            return valid
        } catch (_: Exception) {
            return false
        } finally {
            if (!valid) file.delete()
        }
    }

    fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
