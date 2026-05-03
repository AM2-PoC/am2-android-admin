package com.am2.admin.ui.track

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.am2.admin.R
import com.am2.admin.data.model.TrackUnit
import com.am2.admin.databinding.ItemTrackUnitBinding

class TrackUnitAdapter(
    private var units: List<TrackUnit>,
    private val onItemClick: (TrackUnit) -> Unit
) : RecyclerView.Adapter<TrackUnitAdapter.ViewHolder>() {

    fun updateData(newUnits: List<TrackUnit>) {
        units = newUnits
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackUnitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(units[position])
    }

    override fun getItemCount(): Int = units.size

    inner class ViewHolder(private val binding: ItemTrackUnitBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(unit: TrackUnit) {
            binding.apply {
                tvUnitName.text = unit.name
                tvUnitChannel.text = unit.channel_name
                
                val isSpeaking = unit.is_speaking == 1
                viewStatus.setBackgroundResource(if (isSpeaking) R.drawable.bg_circle_red else R.drawable.bg_circle_green)
                tvTxBadge.visibility = if (isSpeaking) View.VISIBLE else View.GONE
                
                root.setOnClickListener { onItemClick(unit) }
            }
        }
    }
}
