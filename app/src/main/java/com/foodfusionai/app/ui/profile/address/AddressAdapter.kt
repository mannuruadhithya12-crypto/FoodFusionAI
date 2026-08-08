package com.foodfusionai.app.ui.profile.address

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.app.R
import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.databinding.ItemAddressBinding

class AddressAdapter(
    private val onEditClick: (Address) -> Unit,
    private val onDeleteClick: (Address) -> Unit,
    private val onSetDefaultClick: (Address) -> Unit
) : ListAdapter<Address, AddressAdapter.AddressViewHolder>(AddressDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val binding = ItemAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AddressViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AddressViewHolder(private val binding: ItemAddressBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(address: Address) {
            binding.tvType.text = address.type
            binding.tvRecipient.text = address.recipientName
            binding.tvAddressLines.text = "${address.street}, ${address.city}\n${address.state} - ${address.zipCode}"
            binding.tvPhone.text = "Ph: ${address.phoneNumber}"

            binding.chipDefault.visibility = if (address.isDefault) View.VISIBLE else View.GONE
            
            // Set icon based on type (Home, Work, Other)
            val iconRes = when (address.type.lowercase()) {
                "home" -> android.R.drawable.ic_menu_myplaces
                "work" -> android.R.drawable.ic_menu_agenda
                else -> android.R.drawable.ic_menu_mapmode
            }
            binding.ivTypeIcon.setImageResource(iconRes)

            binding.btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_address_item, popup.menu)
                
                if (address.isDefault) {
                    popup.menu.findItem(R.id.action_set_default)?.isVisible = false
                }
                
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            onEditClick(address)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClick(address)
                            true
                        }
                        R.id.action_set_default -> {
                            onSetDefaultClick(address)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    class AddressDiffCallback : DiffUtil.ItemCallback<Address>() {
        override fun areItemsTheSame(oldItem: Address, newItem: Address) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Address, newItem: Address) = oldItem == newItem
    }
}
