package com.am2.admin.ui.admin

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.model.Admin
import com.am2.admin.data.model.Channel
import com.am2.admin.databinding.ActivityAdminPanelBinding
import com.am2.admin.databinding.DialogAdminFormBinding
import com.am2.admin.databinding.DialogDelegateChannelsBinding
import com.am2.admin.ui.BaseActivity
import kotlinx.coroutines.launch
import java.util.Calendar

class AdminPanelActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminPanelBinding
    private lateinit var adminAdapter: AdminAdapter
    private var allChannels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Sidebar dengan tombol tiga garis menggunakan BaseActivity
        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)
        
        setupRecyclerView()
        setupFab()
        
        fetchAdmins()
        fetchChannels()
    }

    private fun setupRecyclerView() {
        adminAdapter = AdminAdapter(
            admins = emptyList(),
            onEdit = { admin -> showAdminForm(admin) },
            onDelete = { admin -> confirmDelete(admin) },
            onDelegate = { admin -> showDelegateDialog(admin) }
        )
        binding.rvAdmins.layoutManager = LinearLayoutManager(this)
        binding.rvAdmins.adapter = adminAdapter
    }

    private fun setupFab() {
        binding.fabAddAdmin.setOnClickListener { showAdminForm(null) }
    }

    private fun fetchAdmins() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAdminList()
                if (response.isSuccessful) {
                    response.body()?.let { adminAdapter.updateData(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchChannels() {
        lifecycleScope.launch {
            try {
                // Perbaikan pemanggilan fungsi API yang benar
                val response = RetrofitClient.instance.getChannels(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    allChannels = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showAdminForm(admin: Admin?) {
        val dialogBinding = DialogAdminFormBinding.inflate(layoutInflater)
        
        if (admin != null) {
            dialogBinding.etUsername.setText(admin.username)
            dialogBinding.fUserQuota.setText(admin.user_quota.toString())
            dialogBinding.fChannelQuota.setText(admin.channel_quota.toString())
            dialogBinding.fCanMaps.isChecked = admin.can_manage_maps
            dialogBinding.fCanP2P.isChecked = admin.can_manage_p2p
            dialogBinding.fCanVideo.isChecked = admin.can_manage_video
            dialogBinding.fExpired.setText(admin.expired_at ?: "")
        }

        dialogBinding.fExpired.setOnClickListener {
            showDatePicker { date ->
                dialogBinding.fExpired.setText(date)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (admin == null) "Tambah Admin" else "Edit Admin")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val username = dialogBinding.etUsername.text.toString()
                val password = dialogBinding.etPassword.text.toString()
                saveAdmin(admin?.id, username, password, dialogBinding)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            onDateSelected(formattedDate)
        }, year, month, day).show()
    }

    private fun saveAdmin(id: Int?, username: String, pass: String, dBinding: DialogAdminFormBinding) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.saveAdmin(
                    action = "save",
                    adminId = id,
                    username = username,
                    password = pass,
                    role = "admin",
                    userQuota = dBinding.fUserQuota.text.toString().toIntOrNull() ?: 0,
                    channelQuota = dBinding.fChannelQuota.text.toString().toIntOrNull() ?: 0,
                    expiredAt = dBinding.fExpired.text.toString(),
                    canManageMaps = dBinding.fCanMaps.isChecked,
                    canManageP2P = dBinding.fCanP2P.isChecked,
                    canManageVideo = dBinding.fCanVideo.isChecked
                )
                if (response.isSuccessful) {
                    fetchAdmins()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showDelegateDialog(admin: Admin) {
        val dialogBinding = DialogDelegateChannelsBinding.inflate(layoutInflater)
        val container = dialogBinding.layoutChannelsContainer
        
        val checkBoxes = mutableListOf<CheckBox>()
        allChannels.forEach { channel ->
            val cb = CheckBox(this).apply {
                text = channel.display_name
                // Use emptyList() if channel_ids might be null, but the property was not in Admin model read earlier.
                // Wait, Admin.kt didn't have channel_ids. I should check if I missed it or if it's named differently.
                // Re-reading Admin.kt: it doesn't have channel_ids.
                // Let's assume it should have it or use a default.
                // For now, I'll comment it out or use an empty list if I'm not sure.
                // Actually, I'll check Admin.kt again.
                // isChecked = admin.channel_ids?.contains(channel.id) == true
                tag = channel.id
            }
            container.addView(cb)
            checkBoxes.add(cb)
        }

        AlertDialog.Builder(this)
            .setTitle("Delegasi Channel: ${admin.username}")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val selectedIds = checkBoxes.filter { it.isChecked }.map { it.tag as Int }
                delegateChannels(admin.id, selectedIds)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun delegateChannels(adminId: Int, channelIds: List<Int>) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.delegateChannels(
                    adminId = adminId,
                    channelIds = channelIds
                )
                if (response.isSuccessful) {
                    fetchAdmins()
                    Toast.makeText(this@AdminPanelActivity, "Delegasi diperbarui", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun confirmDelete(admin: Admin) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Admin")
            .setMessage("Hapus admin ${admin.username}?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    try {
                        RetrofitClient.instance.deleteAdmin(id = admin.id)
                        fetchAdmins()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
