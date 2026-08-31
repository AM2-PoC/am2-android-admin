package com.am2.admin.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.am2.admin.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Whether a downloaded APK may be installed, and when not, which check said so.
 *
 * Build 57 refused build 63 with "identitas APK tidak valid" about an APK whose
 * identity was correct. Ten checks answered with one Boolean and the screen
 * rendered it as one sentence, so a signature that was never read looked
 * exactly like a signature that did not match.
 *
 * This screen already carries a comment about that shape from the last time it
 * happened for a different reason -- an update refused by its own build
 * settings, reported as a bad artifact. Naming the refusal is the fix for the
 * class, not for one instance of it.
 */
sealed class UpdateCheck {
    object Ok : UpdateCheck()

    /** [reason] is a stable identifier, not a sentence: it goes in bug reports. */
    data class Refused(val reason: String) : UpdateCheck()
}

object UpdateVerifier {

    fun check(
        file: File,
        metadata: UpdateMetadata,
        installedVersionCode: Long,
        packageManager: PackageManager,
    ): UpdateCheck {
        var outcome: UpdateCheck = UpdateCheck.Refused("unknown")
        try {
            if (!BuildConfig.SELF_UPDATE_ENABLED) {
                outcome = UpdateCheck.Refused("self_update_disabled")
                return outcome
            }
            if (!file.isFile) {
                outcome = UpdateCheck.Refused("no_file")
                return outcome
            }
            if (file.length() < 100 * 1024L) {
                outcome = UpdateCheck.Refused("file_too_small_${file.length()}")
                return outcome
            }
            if (metadata.versionCode <= installedVersionCode) {
                outcome = UpdateCheck.Refused(
                    "not_newer_${metadata.versionCode}_vs_$installedVersionCode")
                return outcome
            }
            val digest = sha256(file)
            if (digest != metadata.sha256) {
                outcome = UpdateCheck.Refused("bytes_differ_${digest.take(12)}")
                return outcome
            }

            val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            @Suppress("DEPRECATION")
            val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            if (archive == null) {
                outcome = UpdateCheck.Refused("unreadable_archive")
                return outcome
            }
            if (archive.packageName != BuildConfig.APPLICATION_ID) {
                outcome = UpdateCheck.Refused("wrong_package_${archive.packageName}")
                return outcome
            }
            @Suppress("DEPRECATION")
            val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
            if (version != metadata.versionCode) {
                outcome = UpdateCheck.Refused("apk_version_${version}_manifest_${metadata.versionCode}")
                return outcome
            }

            val approved = UpdateMetadata.normalize(BuildConfig.APPROVED_UPDATE_SIGNER_SHA256)
            if (approved.length != 64) {
                outcome = UpdateCheck.Refused("build_trusts_no_signer")
                return outcome
            }
            if (approved != metadata.signerSha256) {
                outcome = UpdateCheck.Refused("manifest_signer_${metadata.signerSha256.take(12)}")
                return outcome
            }

            val certificates = signersOf(archive)
            if (certificates.isEmpty()) {
                outcome = UpdateCheck.Refused("no_signer_in_apk")
                return outcome
            }
            val seen = certificates.map { sha256(it.toByteArray()) }
            if (!seen.contains(approved)) {
                outcome = UpdateCheck.Refused("apk_signer_${seen.first().take(12)}")
                return outcome
            }

            outcome = UpdateCheck.Ok
            return outcome
        } catch (e: Exception) {
            outcome = UpdateCheck.Refused("error_${e.javaClass.simpleName}")
            return outcome
        } finally {
            if (outcome !is UpdateCheck.Ok) file.delete()
        }
    }

    /**
     * The certificates in an APK, from whichever of the two APIs answered.
     *
     * GET_SIGNATURES reads the v1 JAR signature, and the APK this channel
     * serves has none: META-INF holds no .RSA, only the v2/v3 signing block.
     * On API 24 to 27, which minSdk 24 admits, the legacy accessor therefore
     * finds nothing and the update is refused however correct the certificate
     * is. Above P a null signingInfo returned outright, ignoring the very flag
     * requested on the same line as the other one.
     */
    private fun signersOf(archive: PackageInfo): List<Signature> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = archive.signingInfo
            val modern = when {
                info == null -> emptyList()
                info.hasMultipleSigners() -> info.apkContentsSigners.orEmpty().toList()
                else -> info.signingCertificateHistory.orEmpty().toList()
            }
            if (modern.isNotEmpty()) return modern
        }
        @Suppress("DEPRECATION")
        return archive.signatures?.toList().orEmpty()
    }

    fun verify(
        file: File,
        metadata: UpdateMetadata,
        installedVersionCode: Long,
        packageManager: PackageManager,
    ): Boolean = check(file, metadata, installedVersionCode, packageManager) is UpdateCheck.Ok

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
