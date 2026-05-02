package com.am2.admin.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.am2.admin.data.model.Admin
import com.am2.admin.databinding.ItemAdminBinding

class AdminAdapter(
    private var admins: List<Admin>,
    private val onEdit: (Admin) -> Unit,
    private val onDelete: (Admin) -> Unit,
    private val onDelegate: (Admin) -> Unit
) : RecyclerView.Adapter<AdminAdapter.ViewHolder>() {

    fun updateData(newAdmins: List<Admin>) {
        admins = newAdmins
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(admins[position])
    }

    override fun getItemCount(): Int = admins.size

    inner class ViewHolder(private val binding: ItemAdminBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(admin: Admin) {
            binding.apply {
                tvAdminUsername.text = admin.username
                tvRole.text = "ROLE: ${admin.role.uppercase()}"
                
                // Note: The model 'Admin' doesn't seem to have 'current_status' based on the file I read earlier.
                // Re-checking Admin.kt
                // tvStatus.text = admin.current_status.uppercase()
                
                if (admin.role == "superadmin") {
                    tvQuota.text = "Kuota: UNLIMITED"
                    btnDelete.visibility = android.view.View.GONE
                    btnDelegate.visibility = android.view.View.GONE
                } else {
                    tvQuota.text = "Kuota: ${admin.user_quota} User / ${admin.channel_quota} Channel"
                    btnDelete.visibility = android.view.View.VISIBLE
                    btnDelegate.visibility = android.view.View.VISIBLE
                }

                tvExpiry.text = if (admin.expired_at != null) "Exp: ${admin.expired_at}" else "Exp: PERMANEN"

                btnEdit.setOnClickListener { onEdit(admin) }
                btnDelete.setOnClickListener { onDelete(admin) }
                btnDelegate.setOnClickListener { onDelegate(admin) }
            }
        }
    }
}
