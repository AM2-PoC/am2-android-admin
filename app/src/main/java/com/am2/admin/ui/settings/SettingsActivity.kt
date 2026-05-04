package com.am2.admin.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.am2.admin.R
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.pref.SessionManager
import com.am2.admin.databinding.ActivitySettingsBinding
import com.am2.admin.ui.BaseActivity
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val currentVersion = "1.0.0" // Versi aplikasi saat ini

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
            val url = "https://webadmin.am2-poc.com/api_settings.php?action=export_db&admin_id=${sessionManager.getAdminId()}&role=${sessionManager.getRole()}"
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
                if (response.isSuccessful) {
                    val updateInfo = response.body()
                    if (updateInfo != null) {
                        val latestVersion = updateInfo.latest_version
                        // Sederhana: jika string versi tidak sama, anggap ada update
                        // (Bisa dikembangkan dengan pembandingan versi yang lebih kompleks)
                        if (latestVersion != currentVersion) {
                            showUpdateDialog(latestVersion, updateInfo.download_url, updateInfo.changelog)
                        } else {
                            Toast.makeText(this@SettingsActivity, "Aplikasi sudah versi terbaru", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@SettingsActivity, "Gagal terhubung ke server update", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(newVersion: String, downloadUrl: String, changelog: String) {
        AlertDialog.Builder(this)
            .setTitle("Pembaruan Tersedia (v$newVersion)")
            .setMessage("Apa yang baru:\n$changelog\n\nApakah Anda ingin mengunduh sekarang?")
            .setPositiveButton("Download") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                startActivity(intent)
            }
            .setNegativeButton("Nanti", null)
            .show()
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
                e.printStackTrace()
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
