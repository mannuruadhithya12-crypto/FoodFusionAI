package com.foodfusionai.driver.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.driver.R
import com.foodfusionai.driver.data.models.Order
import com.foodfusionai.driver.data.repository.DriverRepositoryImpl
import com.foodfusionai.driver.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        setupRecyclerView()
        fetchHistory(uid)
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(context)
        binding.rvHistory.adapter = adapter
    }

    private fun fetchHistory(uid: String) {
        lifecycleScope.launch {
            repository.getDeliveryHistory(uid).collect { result ->
                result.onSuccess { orders ->
                    adapter.submitList(orders)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // RecyclerView Adapter
    class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        private var list: List<Order> = emptyList()

        fun submitList(newList: List<Order>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvOrderId: TextView = itemView.findViewById(R.id.tvOrderId)
            private val tvRestName: TextView = itemView.findViewById(R.id.tvRestaurantName)
            private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

            fun bind(order: Order) {
                tvOrderId.text = "Order #${order.orderId.take(8).uppercase()}"
                tvRestName.text = order.restaurantName
                
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                tvDate.text = sdf.format(Date(order.createdAt))
                
                tvAmount.text = "₹${order.deliveryFee}"
                tvStatus.text = order.orderStatus.name
                
                if (order.orderStatus.name == "DELIVERED") {
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                } else {
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                }
            }
        }
    }
}
