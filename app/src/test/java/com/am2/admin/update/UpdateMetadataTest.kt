package com.am2.admin.update

import com.am2.admin.data.model.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateMetadataTest {
    private val digest = "a".repeat(64)

    @Test
    fun acceptsStrictApprovedMetadata() {
        val metadata = UpdateMetadata.from(
            UpdateInfo(2, "1.1.0", UpdateMetadata.APPROVED_URL, digest, digest, "Fix")
        )
        assertEquals(2L, metadata.versionCode)
        assertEquals(UpdateMetadata.APPROVED_URL, metadata.updateUrl)
    }

    @Test
    fun rejectsUnapprovedOrigin() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateMetadata.from(
                UpdateInfo(2, "1.1.0", "https://evil.example/admin.apk", digest, digest, "")
            )
        }
    }

    @Test
    fun rejectsMalformedDigest() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateMetadata.from(
                UpdateInfo(2, "1.1.0", UpdateMetadata.APPROVED_URL, "bad", digest, "")
            )
        }
    }
}
