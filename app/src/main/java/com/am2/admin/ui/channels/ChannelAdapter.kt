package com.am2.admin.ui.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.am2.admin.data.model.Channel
import com.am2.admin.databinding.ItemChannelBinding

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onManageAccess: (Channel) -> Unit,
    private val onEdit: (Channel) -> Unit,
    private val onDelete: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    fun updateData(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(private val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: Channel) {
            binding.apply {
                tvChannelName.text = channel.display_name
                tvChannelId.text = channel.name
                
                // Button Manage Access with count
                btnManageAccess.text = "${channel.total_access} User"
                btnManageAccess.setOnClickListener { onManageAccess(channel) }
                
                tvCreator.text = channel.creator_name ?: "System"
                
                btnEdit.setOnClickListener { onEdit(channel) }
                btnDelete.setOnClickListener { onDelete(channel) }
            }
        }
    }
}
