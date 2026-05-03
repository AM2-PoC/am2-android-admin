package com.am2.admin.data.model

data class LogEntry(
    val id: String,
    val jam: String,
    val tanggal: String,
    val pelaksana: String,
    val pelaksana_id: String,
    val target: String,
    val aksi: String,
    val kategori: String, // 'ADM' or 'PTT'
    val raw_time: String
)