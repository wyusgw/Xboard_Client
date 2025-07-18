package com.byteflow.www.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.byteflow.www.R
import com.byteflow.www.data.models.Server
import com.byteflow.www.databinding.ItemServerBinding

class ServerAdapter(
    private val onServerClick: (Server) -> Unit
) : ListAdapter<Server, ServerAdapter.ServerViewHolder>(ServerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServerViewHolder(binding, onServerClick)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ServerViewHolder(
        private val binding: ItemServerBinding,
        private val onServerClick: (Server) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(server: Server) {
            binding.apply {
                serverNameText.text = server.name
                serverLocationText.text = server.location
                serverPingText.text = server.ping
                serverLoadText.text = "${server.load}%"
                
                // Update load progress bar
                serverLoadProgress.progress = server.load
                
                // Set load color based on usage
                val loadColor = when {
                    server.load < 50 -> R.color.success_green
                    server.load < 80 -> R.color.warning_orange
                    else -> R.color.error_red
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    serverLoadProgress.progressTintList = 
                        android.content.res.ColorStateList.valueOf(root.context.getColor(loadColor))
                }
                
                // Show selection indicator
                selectionIndicator.visibility = if (server.isSelected) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                
                // Set click listener
                root.setOnClickListener {
                    onServerClick(server)
                }
            }
        }
    }

    class ServerDiffCallback : DiffUtil.ItemCallback<Server>() {
        override fun areItemsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem == newItem
        }
    }
}