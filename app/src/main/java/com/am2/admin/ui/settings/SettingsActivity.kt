package com.am2.admin.ui.settings

import com.am2.admin.logging.SafeLog

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.am2.admin.BuildConfig
import com.am2.admin.R
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.pref.SessionManager
import com.am2.admin.databinding.ActivitySettingsBinding
import com.am2.admin.ui.BaseActivity
import com.am2.admin.update.UpdateMetadata
import com.am2.admin.update.UpdateVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val currentVersion: String get() = BuildConfig.VERSION_NAME
    private val updateClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processImport(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)
        
        binding.tvCurrentVersion.text = "Versi Saat Ini: $currentVersion"
        fetchSettings()

        binding.btnUpdatePassword.setOnClickListener {
            val newPass = binding.etNewPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            if (newPass.length < 8) {
                Toast.makeText(this, "Password minimal 8 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass != confirmPass) {
                Toast.makeText(this, "Konfirmasi password tidak cocok", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            updatePassword(newPass)
        }

        binding.btnExportDb.setOnClickListener {
            val url = "${com.am2.admin.BuildConfig.BASE_URL}api_settings.php?action=export_db&admin_id=${sessionManager.getAdminId()}&role=${sessionManager.getRole()}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        binding.btnImportDb.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            importLauncher.launch(intent)
        }

        binding.btnCheckUpdate.setOnClickListener {
            checkUpdate()
        }
    }

    private fun checkUpdate() {
        Toast.makeText(this, "Memeriksa pembaruan...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.checkAppUpdate()
                val info = response.body()
                if (!response.isSuccessful || info == null) throw IllegalStateException("metadata update tidak tersedia")
                val metadata = UpdateMetadata.from(info)
                if (metadata.versionCode <= installedVersionCode()) {
                    Toast.makeText(this@SettingsActivity, "Aplikasi sudah versi terbaru", Toast.LENGTH_SHORT).show()
                } else {
                    showUpdateDialog(metadata)
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Update ditolak: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showUpdateDialog(metadata: UpdateMetadata) {
        AlertDialog.Builder(this)
            .setTitle("Pembaruan Tersedia (v${metadata.versionName})")
            .setMessage("Apa yang baru:\n${metadata.changelog}\n\nUnduh dan verifikasi sekarang?")
            .setPositiveButton("Unduh") { _, _ -> downloadUpdate(metadata) }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun downloadUpdate(metadata: UpdateMetadata) {
        lifecycleScope.launch {
            val destination = File(filesDir, "updates/admin-${metadata.versionCode}.apk")
            try {
                withContext(Dispatchers.IO) {
                    destination.parentFile?.mkdirs()
                    destination.delete()
                    val request = Request.Builder().url(metadata.updateUrl).build()
                    updateClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("download gagal (${response.code})")
                        val body = response.body ?: throw IllegalStateException("APK kosong")
                        destination.outputStream().use { output -> body.byteStream().copyTo(output) }
                    }
                    if (!UpdateVerifier.verify(destination, metadata, installedVersionCode(), packageManager)) {
                        throw IllegalStateException("identitas APK tidak valid")
                    }
                }
                showVerifiedInstallDialog(destination, metadata, installedVersionCode())
            } catch (e: Exception) {
                destination.delete()
                Toast.makeText(this@SettingsActivity, "Update ditolak: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showVerifiedInstallDialog(file: File, metadata: UpdateMetadata, installedVersionCode: Long) {
        AlertDialog.Builder(this)
            .setTitle("Pasang Pembaruan")
            .setMessage("Update v${metadata.versionName} sudah diverifikasi. Pasang sekarang?")
            .setPositiveButton("Pasang") { _, _ ->
                if (UpdateVerifier.verify(file, metadata, installedVersionCode, packageManager)) {
                    installUpdate(file)
                } else {
                    Toast.makeText(this, "APK berubah atau tidak valid", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun installUpdate(file: File) {
        val updateRoot = File(filesDir, "updates").canonicalPath + File.separator
        if (!file.isFile || !file.canonicalPath.startsWith(updateRoot)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun installedVersionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun fetchSettings() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAdminProfile(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    response.body()?.let { profile ->
                        binding.tvUsername.text = "Username: ${profile.username}"
                        // profile.total_users and other fields might be missing in AdminProfile
                        // I need to check AdminProfile.kt again.
                        // I read it as: username, role, user_quota, channel_quota, used_user_quota, used_channel_quota, can_manage_maps, can_manage_p2p, can_manage_video
                        // So total_users, total_channels, total_admins are missing.
                        
                        // binding.tvTotalUsers.text = profile.total_users.toString()
                        // binding.tvTotalChannels.text = profile.total_channels.toString()
                        
                        if (sessionManager.getRole() == "superadmin") {
                            binding.layoutAdminStat.visibility = View.VISIBLE
                            // binding.tvTotalAdmins.text = profile.total_admins.toString()
                            binding.tvUserQuota.text = "UNLIMITED"
                            binding.tvChannelQuota.text = "UNLIMITED"
                        } else {
                            binding.tvUserQuota.text = profile.user_quota.toString()
                            binding.tvChannelQuota.text = profile.channel_quota.toString()
                        }

                        binding.tvExpiry.text = "LIFETIME ACCESS" // Assuming for now
                        setupFeaturesList(profile.can_manage_maps, profile.can_manage_p2p, profile.can_manage_video)
                    }
                }
            } catch (e: Exception) {
                SafeLog.e("Exception", "Operation failed", e)
            }
        }
    }

    private fun setupFeaturesList(maps: Boolean, p2p: Boolean, video: Boolean) {
        binding.layoutFeatures.removeAllViews()
        addFeatureItem("Tracking GPS", maps)
        addFeatureItem("Chat P2P & Group", p2p)
        addFeatureItem("Video Call Group", video)
    }

    private fun addFeatureItem(name: String, active: Boolean) {
        val tv = TextView(this).apply {
            text = "$name: ${if (active) "AKTIF" else "NONAKTIF"}"
            setTextColor(if (active) Color.parseColor("#198754") else Color.RED)
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }
        binding.layoutFeatures.addView(tv)
    }

    private fun updatePassword(pass: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateAdminPassword(
                    adminId = sessionManager.getAdminId(),
                    newPass = pass
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@SettingsActivity, "Password diperbarui", Toast.LENGTH_SHORT).show()
                    binding.etNewPassword.text?.clear()
                    binding.etConfirmPassword.text?.clear()
                } else {
                    Toast.makeText(this@SettingsActivity, response.body()?.message ?: "Gagal", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processImport(uri: Uri) {
        val file = getFileFromUri(uri) ?: return
        val requestFile = file.asRequestBody("application/sql".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("sql_file", file.name, requestFile)
        val actionPart = "import_db".toRequestBody("text/plain".toMediaTypeOrNull())
        val adminIdPart = sessionManager.getAdminId().toString().toRequestBody("text/plain".toMediaTypeOrNull())

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.importDatabase(actionPart, adminIdPart, body)
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@SettingsActivity, "Restore Berhasil", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "Restore Gagal", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        val contentResolver = contentResolver
        val fileName = getFileName(uri) ?: "backup.sql"
        val tempFile = File(cacheDir, fileName)
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }
}
