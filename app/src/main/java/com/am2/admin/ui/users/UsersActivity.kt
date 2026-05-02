package com.am2.admin.ui.users

import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.am2.admin.R
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.model.Channel
import com.am2.admin.data.model.User
import com.am2.admin.databinding.ActivityUsersBinding
import com.am2.admin.ui.BaseActivity
import com.google.gson.Gson
import kotlinx.coroutines.launch

class UsersActivity : BaseActivity() {

    private lateinit var binding: ActivityUsersBinding
    private lateinit var userAdapter: UserAdapter
    private var allChannels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Sidebar dengan tombol tiga garis
        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)

        setupRecyclerView()
        setupSearch()
        setupFab()
        fetchUsers()
        fetchAllChannels()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(
            users = emptyList(),
            onUserClick = { user -> showChannelSelectionDialog(user) },
            onEdit = { user -> showEditUserDialog(user) },
            onFeatureUpdate = { user, feature, value -> updateFeature(user, feature, value) },
            onDelete = { user -> confirmDelete(user) }
        )
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@UsersActivity)
            adapter = userAdapter
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
                    // Gunakan distinctBy untuk menghindari channel ganda dari API
                    allChannels = response.body()?.distinctBy { it.id } ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showChannelSelectionDialog(user: User) {
        if (allChannels.isEmpty()) {
            Toast.makeText(this, "Memuat data channel...", Toast.LENGTH_SHORT).show()
            fetchAllChannels()
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserChannels(userId = user.id)
                val selectedIds = (if (response.isSuccessful) response.body() ?: emptyList() else emptyList()).toMutableSet()
                
                val layout = LinearLayout(this@UsersActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(40, 20, 40, 20)
                }

                val selectAllCb = CheckBox(this@UsersActivity).apply {
                    text = "Pilih Semua"
                }
                layout.addView(selectAllCb)

                val channelCheckboxes = mutableListOf<CheckBox>()
                allChannels.forEach { channel ->
                    val cb = CheckBox(this@UsersActivity).apply {
                        text = channel.display_name
                        isChecked = selectedIds.contains(channel.id)
                        tag = channel.id
                    }
                    channelCheckboxes.add(cb)
                    layout.addView(cb)
                }

                selectAllCb.setOnCheckedChangeListener { _, isChecked ->
                    channelCheckboxes.forEach { it.isChecked = isChecked }
                }

                AlertDialog.Builder(this@UsersActivity)
                    .setTitle("Akses Channel: ${user.name}")
                    .setView(layout)
                    .setPositiveButton("Simpan") { _, _ ->
                        val finalSelectedIds = channelCheckboxes.filter { it.isChecked }.map { it.tag as Int }
                        saveUserChannels(user.id, finalSelectedIds)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserChannels(userId: String, selectedIds: List<Int>) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.saveUserChannels(
                    userId = userId,
                    channelsJson = Gson().toJson(selectedIds)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@UsersActivity, "Akses channel diperbarui", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@UsersActivity, "Gagal menyimpan akses", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                fetchUsers(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) fetchUsers()
                return true
            }
        })
    }

    private fun setupFab() {
        binding.fabAddUser.setOnClickListener { showAddUserDialog() }
    }

    private fun fetchUsers(search: String? = null) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUsers(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole(),
                    search = search
                )
                if (response.isSuccessful) {
                    response.body()?.let { userAdapter.updateData(it) }
                }
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFeature(user: User, feature: String, value: Any) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateFeature(
                    userId = user.id,
                    feature = feature,
                    value = value
                )
                if (!response.isSuccessful || response.body()?.success != true) {
                    Toast.makeText(this@UsersActivity, "Gagal memperbarui fitur", Toast.LENGTH_SHORT).show()
                    fetchUsers() 
                }
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                fetchUsers()
            }
        }
    }

    private fun showAddUserDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val etId = EditText(this).apply { hint = "ID / Username" }
        val etName = EditText(this).apply { hint = "Nama Lengkap" }
        
        val passContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val etPassword = EditText(this).apply { 
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnToggle = ImageButton(this).apply {
            setImageResource(R.drawable.ic_visibility)
            background = null
            setOnClickListener {
                if (etPassword.transformationMethod == null) {
                    etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                    setImageResource(R.drawable.ic_visibility)
                } else {
                    etPassword.transformationMethod = null
                }
                etPassword.setSelection(etPassword.text.length)
            }
        }
        passContainer.addView(etPassword)
        passContainer.addView(btnToggle)

        layout.addView(etId)
        layout.addView(etName)
        layout.addView(passContainer)

        AlertDialog.Builder(this)
            .setTitle("Tambah Personel Baru")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val id = etId.text.toString().trim()
                val name = etName.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (id.isNotEmpty() && name.isNotEmpty() && password.isNotEmpty()) {
                    addUser(id, name, password)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun addUser(id: String, name: String, pass: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.addUser(
                    adminId = sessionManager.getAdminId(),
                    id = id, 
                    name = name, 
                    password = pass
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@UsersActivity, "User berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    fetchUsers()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditUserDialog(user: User) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val etName = EditText(this).apply { 
            hint = "Nama Lengkap"
            setText(user.name)
        }
        
        val passContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val etPassword = EditText(this).apply { 
            hint = "Password Baru (Kosongkan jika tidak ganti)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnToggle = ImageButton(this).apply {
            setImageResource(R.drawable.ic_visibility)
            background = null
            setOnClickListener {
                if (etPassword.transformationMethod == null) {
                    etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                    setImageResource(R.drawable.ic_visibility)
                } else {
                    etPassword.transformationMethod = null
                }
                etPassword.setSelection(etPassword.text.length)
            }
        }
        passContainer.addView(etPassword)
        passContainer.addView(btnToggle)

        layout.addView(TextView(this).apply { text = "NRP/ID: ${user.id}"; setPadding(0,0,0,10) })
        layout.addView(etName)
        layout.addView(passContainer)

        AlertDialog.Builder(this)
            .setTitle("Edit Personel")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val name = etName.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (name.isNotEmpty()) {
                    updateUser(user.id, name, password)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateUser(id: String, name: String, pass: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.addUser(
                    action = "edit",
                    adminId = sessionManager.getAdminId(),
                    id = id,
                    name = name,
                    password = pass
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@UsersActivity, "User berhasil diperbarui", Toast.LENGTH_SHORT).show()
                    fetchUsers()
                } else {
                    Toast.makeText(this@UsersActivity, response.body()?.message ?: "Gagal update", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Personel")
            .setMessage("Apakah Anda yakin ingin menghapus ${user.name}?")
            .setPositiveButton("Hapus") { _, _ -> deleteUser(user.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteUser(id: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.deleteUser(id = id)
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchUsers()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
