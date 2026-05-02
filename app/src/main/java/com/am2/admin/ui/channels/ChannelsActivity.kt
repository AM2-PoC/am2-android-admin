package com.am2.admin.ui.channels

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.am2.admin.R
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.model.Channel
import com.am2.admin.data.model.User
import com.am2.admin.databinding.ActivityChannelsBinding
import com.am2.admin.ui.BaseActivity
import kotlinx.coroutines.launch

class ChannelsActivity : BaseActivity() {

    private lateinit var binding: ActivityChannelsBinding
    private lateinit var channelAdapter: ChannelAdapter
    private var allChannels: List<Channel> = emptyList()
    private var allUsers: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)

        setupUI()
        fetchChannels()
        fetchAllUsers()
    }

    private fun setupUI() {
        channelAdapter = ChannelAdapter(
            channels = emptyList(),
            onManageAccess = { channel -> showManageAccessDialog(channel) },
            onEdit = { channel -> showEditChannelDialog(channel) },
            onDelete = { channel -> confirmDelete(channel) }
        )
        binding.rvChannels.apply {
            layoutManager = LinearLayoutManager(this@ChannelsActivity)
            adapter = channelAdapter
        }

        binding.btnAddChannel.setOnClickListener {
            val name = binding.etNewChannelName.text.toString().trim()
            if (name.isNotEmpty()) {
                addChannel(name)
            } else {
                Toast.makeText(this, "Nama channel tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchChannels() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getChannels(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    allChannels = response.body() ?: emptyList()
                    channelAdapter.updateData(allChannels)
                    
                    // Update stats like Website
                    val ownedCount = allChannels.count { it.creator_name == sessionManager.getUsername() || it.creator_name == "System" }
                    binding.tvOwnedCount.text = ownedCount.toString()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChannelsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterChannels(query: String) {
        val filtered = allChannels.filter { 
            it.display_name.contains(query, ignoreCase = true) || 
            it.name.contains(query, ignoreCase = true) 
        }
        channelAdapter.updateData(filtered)
    }

    private fun fetchAllUsers() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUsers(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    allUsers = response.body() ?: emptyList()
                }
            } catch (e: Exception) { }
        }
    }

    private fun addChannel(name: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.addChannel(
                    adminId = sessionManager.getAdminId(),
                    displayName = name,
                    category = "public"
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    binding.etNewChannelName.text?.clear()
                    fetchChannels()
                    Toast.makeText(this@ChannelsActivity, "Channel berhasil dibuat", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ChannelsActivity, response.body()?.message ?: "Gagal", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChannelsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditChannelDialog(channel: Channel) {
        val etName = EditText(this).apply {
            setText(channel.display_name)
            setSelection(channel.display_name.length)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Update Channel")
            .setView(etName)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty()) updateChannel(channel.id, newName)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateChannel(id: Int, name: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateChannel(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole(),
                    id = id,
                    displayName = name
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchChannels()
                    Toast.makeText(this@ChannelsActivity, "Channel diperbarui", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
    }

    private fun showManageAccessDialog(channel: Channel) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_access, null)
        val tvTarget = dialogView.findViewById<android.widget.TextView>(R.id.tvTargetChannel)
        val rvUsers = dialogView.findViewById<RecyclerView>(R.id.rvUserSelection)
        val cbSelectAll = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSelectAll)

        tvTarget.text = channel.display_name
        
        val selectionAdapter = UserSelectionAdapter(allUsers) { selectedCount ->
            cbSelectAll.setOnCheckedChangeListener(null)
            cbSelectAll.isChecked = selectedCount > 0 && selectedCount == allUsers.size
            cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
                // This line will be reached after the lambda is called, and selectionAdapter is defined.
                // Wait, recursion? No, UserSelectionAdapter is already constructed here.
            }
        }
        
        // Fix the reference in the listener
        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            selectionAdapter.selectAll(isChecked)
        }

        rvUsers.apply {
            layoutManager = LinearLayoutManager(this@ChannelsActivity)
            adapter = selectionAdapter
        }

        // Fetch current access
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.instance.getChannelUsersAccess(channelId = channel.id)
                if (resp.isSuccessful) {
                    val assignedUserIds = resp.body() ?: emptyList()
                    selectionAdapter.setSelectedIds(assignedUserIds)
                    
                    cbSelectAll.setOnCheckedChangeListener(null)
                    cbSelectAll.isChecked = assignedUserIds.size == allUsers.size && allUsers.isNotEmpty()
                    cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
                        selectionAdapter.selectAll(isChecked)
                    }
                }
            } catch (e: Exception) { }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("SIMPAN PERUBAHAN AKSES") { _, _ ->
                saveAccess(channel.id, selectionAdapter.getSelectedIds())
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun saveAccess(channelId: Int, userIds: List<String>) {
        lifecycleScope.launch {
            try {
                val jsonIds = Gson().toJson(userIds)
                val response = RetrofitClient.instance.saveChannelAccess(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole(),
                    channelId = channelId,
                    userIdsJson = jsonIds
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@ChannelsActivity, "Izin akses berhasil diperbarui", Toast.LENGTH_SHORT).show()
                    fetchChannels()
                } else {
                    Toast.makeText(this@ChannelsActivity, response.body()?.message ?: "Gagal", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChannelsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(channel: Channel) {
        AlertDialog.Builder(this)
            .setTitle("Hapus")
            .setMessage("Hapus channel ${channel.display_name}?")
            .setPositiveButton("Hapus") { _, _ -> deleteChannel(channel.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteChannel(id: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.deleteChannel(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole(),
                    id = id
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@ChannelsActivity, "Channel berhasil dihapus", Toast.LENGTH_SHORT).show()
                    fetchChannels()
                } else {
                    val msg = response.body()?.message ?: "Gagal menghapus channel"
                    Toast.makeText(this@ChannelsActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChannelsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
