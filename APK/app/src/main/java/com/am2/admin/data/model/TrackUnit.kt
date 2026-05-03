package com.am2.admin.data.model

data class TrackUnit(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val is_online: Int,
    val is_speaking: Int,
    val is_stale: Boolean,
    val channel_name: String,
    val updated_at: String
)