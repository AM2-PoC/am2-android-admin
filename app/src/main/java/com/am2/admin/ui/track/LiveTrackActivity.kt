package com.am2.admin.ui.track

import com.am2.admin.logging.SafeLog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.am2.admin.R
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.model.TrackUnit
import com.am2.admin.data.pref.SessionManager
import com.am2.admin.databinding.ActivityLiveTrackBinding
import com.am2.admin.ui.BaseActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class LiveTrackActivity : BaseActivity() {

    private lateinit var binding: ActivityLiveTrackBinding
    private val markers = mutableMapOf<String, Marker>()
    private lateinit var trackAdapter: TrackUnitAdapter
    private var syncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // OSMDroid configuration
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        binding = ActivityLiveTrackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Sidebar dengan tombol tiga garis menggunakan BaseActivity
        setupDrawer(binding.drawerLayout, binding.navView, binding.toolbar)

        setupMap()
        setupRecyclerView()

        startAutoRefresh()
    }

    private fun setupMap() {
        binding.map.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(-2.5, 118.0))
        }
    }

    private fun setupRecyclerView() {
        trackAdapter = TrackUnitAdapter(
            units = emptyList(),
            onItemClick = { unit ->
                val point = GeoPoint(unit.lat, unit.lng)
                binding.map.controller.animateTo(point, 17.0, 1000L)
                markers[unit.id]?.showInfoWindow()
            }
        )
        binding.rvTrackUnits.layoutManager = LinearLayoutManager(this)
        binding.rvTrackUnits.adapter = trackAdapter
    }

    private fun startAutoRefresh() {
        syncJob = lifecycleScope.launch {
            while (true) {
                fetchTrackData()
                delay(2000)
            }
        }
    }

    private fun fetchTrackData() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getTrackUnits(
                    adminId = sessionManager.getAdminId(),
                    role = sessionManager.getRole()
                )
                if (response.isSuccessful) {
                    val units = response.body() ?: emptyList()
                    updateMarkers(units)
                    trackAdapter.updateData(units)

                    val isAnySpeaking = units.any { it.is_speaking == 1 }
                    binding.tvTxIndicator.visibility = if (isAnySpeaking) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                SafeLog.e("Exception", "Operation failed", e)
            }
        }
    }

    private fun createCustomMarker(name: String, isSpeaking: Boolean): BitmapDrawable {
        val view = LayoutInflater.from(this).inflate(R.layout.layout_custom_marker, null)
        val tvName = view.findViewById<TextView>(R.id.tvMarkerName)
        val viewDot = view.findViewById<View>(R.id.viewMarkerDot)

        tvName.text = name
        if (isSpeaking) {
            tvName.setBackgroundResource(R.drawable.bg_marker_label_red)
            viewDot.setBackgroundResource(R.drawable.bg_circle_red)
        } else {
            tvName.setBackgroundResource(R.drawable.bg_marker_label)
            viewDot.setBackgroundResource(R.drawable.bg_circle_green)
        }

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return BitmapDrawable(resources, bitmap)
    }

    private fun updateMarkers(units: List<TrackUnit>) {
        val activeIds = units.map { it.id }.toSet()

        val toRemove = markers.keys.filter { it !in activeIds }
        toRemove.forEach { id ->
            binding.map.overlays.remove(markers[id])
            markers.remove(id)
        }

        units.forEach { unit ->
            if (unit.lat == 0.0) return@forEach
            val position = GeoPoint(unit.lat, unit.lng)
            val isSpeaking = unit.is_speaking == 1

            // Perbarui ikon jika status TX berubah atau marker baru
            val marker = markers[unit.id]
            if (marker != null) {
                marker.position = position
                marker.title = unit.name
                marker.subDescription = "Channel: ${unit.channel_name}"

                // Cek apakah status bicara berubah untuk update ikon
                val wasSpeaking = marker.relatedObject as? Boolean ?: false
                if (wasSpeaking != isSpeaking) {
                    marker.icon = createCustomMarker(unit.name, isSpeaking)
                    marker.relatedObject = isSpeaking
                }
            } else {
                val newMarker = Marker(binding.map).apply {
                    this.position = position
                    this.title = unit.name
                    this.icon = createCustomMarker(unit.name, isSpeaking)
                    this.subDescription = "Channel: ${unit.channel_name}"
                    this.relatedObject = isSpeaking // Simpan status TX di relatedObject
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                markers[unit.id] = newMarker
                binding.map.overlays.add(newMarker)
            }
        }
        binding.map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
    }
}
