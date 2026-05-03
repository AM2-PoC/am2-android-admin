package com.am2.admin.ui.access

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.am2.admin.data.model.UserAccess
import com.am2.admin.databinding.ItemUserAccessBinding

class UserAccessAdapter(
    private var list: List<UserAccess>,
    private val onEditAccess: (UserAccess) -> Unit,
    private val onKick: (UserAccess) -> Unit
) : RecyclerView.Adapter<UserAccessAdapter.ViewHolder>() {

    fun updateData(newList: List<UserAccess>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserAccessBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    inner class ViewHolder(private val binding: ItemUserAccessBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UserAccess) {
            binding.apply {
                tvUserId.text = "#${item.id}"
                tvUserName.text = item.name
                
                chipGroupChannels.removeAllViews()
                item.allowed_channels?.split(", ")?.forEach { channelName ->
                    if (channelName.isNotEmpty()) {
                        val chip = Chip(root.context).apply {
                            text = channelName.replace("*", "")
                            isCheckable = false
                            if (channelName.startsWith("*")) {
                                setChipBackgroundColorResource(android.R.color.black)
                                setTextColor(root.context.getColor(android.R.color.white))
                            }
                        }
                        chipGroupChannels.addView(chip)
                    }
                }

                root.setOnClickListener { onEditAccess(item) }
                btnKick.setOnClickListener { onKick(item) }
            }
        }
    }
}
