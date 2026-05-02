package com.am2.admin.ui.users

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.am2.admin.data.model.User
import com.am2.admin.databinding.ItemUserBinding

class UserAdapter(
    private var users: List<User>,
    private val onUserClick: (User) -> Unit,
    private val onEdit: (User) -> Unit,
    private val onFeatureUpdate: (User, String, Any) -> Unit,
    private val onDelete: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    fun updateData(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.apply {
                tvUserName.text = user.name
                tvUserId.text = "ID: ${user.id}"

                root.setOnClickListener { onUserClick(user) }
                btnEdit.setOnClickListener { onEdit(user) }

                // Reset listeners to avoid triggering on manual set
                switchDuplex.setOnCheckedChangeListener(null)
                switchMaps.setOnCheckedChangeListener(null)
                switchP2P.setOnCheckedChangeListener(null)
                switchVideo.setOnCheckedChangeListener(null)

                switchDuplex.isChecked = user.duplex_mode == "FULL DUPLEX"
                switchMaps.isChecked = user.enable_maps
                switchP2P.isChecked = user.enable_p2p
                switchVideo.isChecked = user.enable_ptt_video

                switchDuplex.setOnCheckedChangeListener { _, isChecked ->
                    val mode = if (isChecked) "FULL DUPLEX" else "HALF DUPLEX"
                    onFeatureUpdate(user, "duplex_mode", mode)
                }
                switchMaps.setOnCheckedChangeListener { _, isChecked ->
                    onFeatureUpdate(user, "enable_maps", isChecked)
                }
                switchP2P.setOnCheckedChangeListener { _, isChecked ->
                    onFeatureUpdate(user, "enable_p2p", isChecked)
                }
                switchVideo.setOnCheckedChangeListener { _, isChecked ->
                    onFeatureUpdate(user, "enable_ptt_video", isChecked)
                }

                btnDelete.setOnClickListener { onDelete(user) }
            }
        }
    }
}