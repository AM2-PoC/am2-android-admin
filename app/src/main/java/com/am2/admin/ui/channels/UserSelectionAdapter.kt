package com.am2.admin.ui.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.am2.admin.data.model.User
import com.am2.admin.databinding.ItemUserSelectionBinding

class UserSelectionAdapter(
    private var users: List<User>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<UserSelectionAdapter.ViewHolder>() {

    private val selectedIds = mutableSetOf<String>()

    fun updateData(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun setSelectedIds(ids: List<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun getSelectedIds(): List<String> = selectedIds.toList()

    fun selectAll(select: Boolean) {
        if (select) {
            selectedIds.addAll(users.map { it.id })
        } else {
            selectedIds.clear()
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class ViewHolder(private val binding: ItemUserSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.tvUserName.text = user.name
            binding.tvUserId.text = "#${user.id}"
            
            // Hide spinner to match Website (it only uses checkboxes in channel access modal)
            binding.spinnerPermission.visibility = android.view.View.GONE

            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = selectedIds.contains(user.id)
            
            binding.root.setOnClickListener {
                binding.checkbox.isChecked = !binding.checkbox.isChecked
            }

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedIds.add(user.id)
                } else {
                    selectedIds.remove(user.id)
                }
                onSelectionChanged(selectedIds.size)
            }
        }
    }
}
