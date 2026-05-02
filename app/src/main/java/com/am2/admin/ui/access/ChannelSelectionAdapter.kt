package com.am2.admin.ui.access

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.am2.admin.data.model.Channel
import com.am2.admin.databinding.ItemChannelSelectionBinding

class ChannelSelectionAdapter(
    private val allChannels: List<Channel>,
    selectedIds: List<Int>,
    private var defaultId: Int?,
    permissions: Map<Int, String>
) : RecyclerView.Adapter<ChannelSelectionAdapter.ViewHolder>() {

    private val selectedIdsSet = selectedIds.toMutableSet()
    private val permissionsMap = permissions.toMutableMap()

    fun getSelectedIds() = selectedIdsSet.toList()
    fun getDefaultId() = defaultId
    fun getPermissions() = permissionsMap.toMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChannelSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(allChannels[position])
    }

    override fun getItemCount(): Int = allChannels.size

    inner class ViewHolder(private val binding: ItemChannelSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: Channel) {
            binding.apply {
                tvChannelName.text = channel.display_name
                checkBox.setOnCheckedChangeListener(null)
                checkBox.isChecked = selectedIdsSet.contains(channel.id)

                tvDefaultLabel.visibility = if (channel.id == defaultId) View.VISIBLE else View.GONE
                root.setBackgroundColor(if (channel.id == defaultId) 0xFFFFF9C4.toInt() else 0x00000000)

                switchRxOnly.setOnCheckedChangeListener(null)
                switchRxOnly.isChecked = permissionsMap[channel.id] == "RX"

                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedIdsSet.add(channel.id)
                    } else {
                        selectedIdsSet.remove(channel.id)
                        if (defaultId == channel.id) defaultId = null
                    }
                    notifyItemChanged(adapterPosition)
                }

                layoutInfo.setOnClickListener {
                    if (selectedIdsSet.contains(channel.id)) {
                        defaultId = channel.id
                        notifyDataSetChanged()
                    }
                }

                switchRxOnly.setOnCheckedChangeListener { _, isChecked ->
                    permissionsMap[channel.id] = if (isChecked) "RX" else "FULL DUPLEX"
                }
            }
        }
    }
}
