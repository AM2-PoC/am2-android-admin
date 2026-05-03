package com.am2.admin.ui.main

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.databinding.ActivityMainBinding
import com.am2.admin.ui.BaseActivity
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Sidebar dengan tombol tiga garis menggunakan BaseActivity
        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)
        
        fetchDashboardStats()
        fetchChartData()
    }

    private fun fetchDashboardStats() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getDashboardStats(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    response.body()?.let {
                        binding.tvTotalPersonnel.text = it.total_user.toString()
                        binding.tvUserOnline.text = it.user_online.toString()
                        binding.tvTotalChannels.text = it.total_channel.toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Stats Error: ${e.message}")
            }
        }
    }

    private fun fetchChartData() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getChartData(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    response.body()?.let {
                        if (it.labels.isNotEmpty()) setupLineChart(it.labels, it.values)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Chart Error: ${e.message}")
            }
        }
    }

    private fun setupLineChart(labels: List<String>, values: List<Int>) {
        val entries = values.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
        val dataSet = LineDataSet(entries, "Panggilan PTT").apply {
            color = Color.parseColor("#003566")
            setCircleColor(Color.parseColor("#003566"))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#003566")
            fillAlpha = 50
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -45f
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            animateX(1000)
            invalidate()
        }
    }
}
