package com.am2.admin.ui.access

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.am2.admin.R
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.model.Channel
import com.am2.admin.data.model.UserAccess
import com.am2.admin.databinding.ActivityUserAccessBinding
import com.am2.admin.databinding.DialogEditAccessBinding
import com.am2.admin.ui.BaseActivity
import com.am2.admin.ui.admin.AdminPanelActivity
import com.am2.admin.ui.channels.ChannelsActivity
import com.am2.admin.ui.login.LoginActivity
import com.am2.admin.ui.logs.LogsActivity
import com.am2.admin.ui.main.MainActivity
import com.am2.admin.ui.track.LiveTrackActivity
import com.am2.admin.ui.users.UsersActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

class UserAccessActivity : BaseActivity() {

    private lateinit var binding: ActivityUserAccessBinding
    private lateinit var adapter: UserAccessAdapter
    private var allChannels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserAccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Sidebar dengan tombol tiga garis menggunakan BaseActivity
        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)

        setupRecyclerView()
        setupSearch()
        
        fetchUserAccess()
        fetchAllChannels()
    }

    private fun setupRecyclerView() {
        adapter = UserAccessAdapter(
            list = emptyList(),
            onEditAccess = { userAccess -> showEditAccessDialog(userAccess) },
            onKick = { userAccess -> confirmKick(userAccess) }
        )
        binding.rvUserAccess.layoutManager = LinearLayoutManager(this)
        binding.rvUserAccess.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                fetchUserAccess(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) fetchUserAccess()
                return true
            }
        })
    }

    private fun fetchUserAccess(search: String? = null) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserAccessList(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole(),
                    search = search
                )
                if (response.isSuccessful) {
                    response.body()?.let { adapter.updateData(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchAllChannels() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getChannels(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    // Gunakan distinctBy untuk menghindari channel ganda dari API pada Admin Native
                    allChannels = response.body()?.distinctBy { it.id } ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showEditAccessDialog(userAccess: UserAccess) {
        val dialogBinding = DialogEditAccessBinding.inflate(layoutInflater)
        dialogBinding.tvTargetName.text = userAccess.name

        val selectionAdapter = ChannelSelectionAdapter(
            allChannels = allChannels,
            selectedIds = userAccess.channel_ids_json ?: emptyList(),
            defaultId = userAccess.default_id,
            permissions = userAccess.permissions_json?.let { perms ->
                val map = mutableMapOf<Int, String>()
                userAccess.channel_ids_json?.forEachIndexed { index, id ->
                    map[id] = perms[index]
                }
                map
            } ?: emptyMap()
        )

        dialogBinding.rvChannelsSelection.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvChannelsSelection.adapter = selectionAdapter

        AlertDialog.Builder(this)
            .setTitle("Edit Izin Akses")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val selectedIds = selectionAdapter.getSelectedIds()
                val defaultId = selectionAdapter.getDefaultId()
                val permissions = selectionAdapter.getPermissions()
                updateAccess(userAccess.id, selectedIds, defaultId, permissions)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateAccess(userId: String, channelIds: List<Int>, defaultId: Int?, permissions: Map<Int, String>) {
        lifecycleScope.launch {
            try {
                // Konversi Map permissions ke JSON string agar sesuai dengan API
                // Penting: Key harus String untuk menghindari ClassCastException di JSONObject Android
                val stringKeysMap = permissions.mapKeys { it.key.toString() }
                val permsJson = JSONObject(stringKeysMap).toString()
                
                // Pastikan mengirim admin_id untuk log aktivitas
                val response = RetrofitClient.instance.updateUserAccess(
                    adminId = sessionManager.getAdminId(),
                    userId = userId,
                    channelIds = if (channelIds.isEmpty()) null else channelIds,
                    defaultChannelId = defaultId,
                    permissionsJson = permsJson
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@UserAccessActivity, "Akses diperbarui", Toast.LENGTH_SHORT).show()
                    fetchUserAccess()
                } else {
                    val msg = response.body()?.message ?: "Gagal memperbarui akses"
                    Toast.makeText(this@UserAccessActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@UserAccessActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmKick(userAccess: UserAccess) {
        AlertDialog.Builder(this)
            .setTitle("Force Logout")
            .setMessage("Putuskan koneksi perangkat ${userAccess.name}?")
            .setPositiveButton("KICK") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.forceLogout(
                            userId = userAccess.id,
                            adminId = sessionManager.getAdminId()
                        )
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(this@UserAccessActivity, "Personel berhasil dikeluarkan", Toast.LENGTH_SHORT).show()
                            fetchUserAccess()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
