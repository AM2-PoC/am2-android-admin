package com.am2.admin.ui.logs

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.databinding.ActivityLogsBinding
import com.am2.admin.ui.BaseActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LogsActivity : BaseActivity() {

    private lateinit var binding: ActivityLogsBinding
    private lateinit var logAdapter: LogAdapter
    private var currentCategory = "ALL"
    private var syncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Sidebar dengan tombol tiga garis menggunakan BaseActivity
        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)

        setupRecyclerView()
        setupTabs()
        
        startAutoRefresh()
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter(emptyList())
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentCategory = when (tab?.position) {
                    1 -> "PTT"
                    2 -> "ADM"
                    else -> "ALL"
                }
                fetchLogs()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun startAutoRefresh() {
        syncJob = lifecycleScope.launch {
            while (true) {
                fetchLogs()
                delay(5000)
            }
        }
    }

    private fun fetchLogs() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getLogs(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole(),
                    category = currentCategory
                )
                if (response.isSuccessful) {
                    response.body()?.let { logAdapter.updateData(it) }
                }
            } catch (e: Exception) {
                // Silently handle errors for background sync
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
    }
}
