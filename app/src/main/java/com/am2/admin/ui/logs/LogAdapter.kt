package com.am2.admin.ui.logs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.am2.admin.R
import com.am2.admin.data.model.LogEntry
import com.am2.admin.databinding.ItemLogBinding

class LogAdapter(private var logs: List<LogEntry>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    fun updateData(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: LogEntry) {
            binding.apply {
                tvLogTime.text = log.jam
                tvLogDate.text = log.tanggal
                tvLogPelaksana.text = log.pelaksana
                tvLogTarget.text = log.target

                val type = log.aksi.uppercase()
                tvLogStatus.text = type
                
                when {
                    listOf("PUSH", "TX", "START", "PTT_ON").contains(type) -> {
                        tvLogStatus.setBackgroundResource(R.drawable.bg_badge_red)
                        tvLogStatus.setTextColor(Color.WHITE)
                        tvLogStatus.text = "TX / ON"
                    }
                    type == "LOGIN" -> {
                        tvLogStatus.setBackgroundResource(R.drawable.bg_badge_green)
                        tvLogStatus.setTextColor(Color.WHITE)
                        tvLogStatus.text = "ONLINE"
                    }
                    type.contains("CREATE") -> {
                        tvLogStatus.setBackgroundResource(R.drawable.bg_badge_green)
                        tvLogStatus.setTextColor(Color.WHITE)
                        tvLogStatus.text = "BARU"
                    }
                    else -> {
                        tvLogStatus.setBackgroundResource(R.drawable.bg_badge_category)
                        tvLogStatus.setTextColor(Color.parseColor("#003566"))
                    }
                }
            }
        }
    }
}
