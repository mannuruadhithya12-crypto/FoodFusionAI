package com.foodfusionai.driver.ui.earnings

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
import com.foodfusionai.driver.data.repository.DriverRepositoryImpl
import com.foodfusionai.driver.databinding.FragmentEarningsBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EarningsFragment : Fragment() {

    private var _binding: FragmentEarningsBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()
    private lateinit var adapter: EarningsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEarningsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        setupRecyclerView()
        fetchEarningsSummary(uid)
        fetchEarningsEntries(uid)
    }

    private fun setupRecyclerView() {
        adapter = EarningsAdapter()
        binding.rvEarnings.layoutManager = LinearLayoutManager(context)
        binding.rvEarnings.adapter = adapter
    }

    private fun fetchEarningsSummary(uid: String) {
        lifecycleScope.launch {
            repository.getEarningsSummary(uid).collect { result ->
                result.onSuccess { summary ->
                    val total = summary["totalEarnings"] ?: 0.0
                    binding.tvTotalEarnings.text = "₹${total}"
                }
            }
        }
    }

    private fun fetchEarningsEntries(uid: String) {
        lifecycleScope.launch {
            repository.getEarningsEntries(uid).collect { result ->
                result.onSuccess { entries ->
                    adapter.submitList(entries)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // RecyclerView Adapter
    class EarningsAdapter : RecyclerView.Adapter<EarningsAdapter.ViewHolder>() {

        private var list: List<Map<String, Any>> = emptyList()

        fun submitList(newList: List<Map<String, Any>>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_earnings, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvType: TextView = itemView.findViewById(R.id.tvEarningType)
            private val tvRef: TextView = itemView.findViewById(R.id.tvOrderRef)
            private val tvDate: TextView = itemView.findViewById(R.id.tvEarningDate)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvEarningAmount)

            fun bind(entry: Map<String, Any>) {
                tvType.text = entry["type"] as? String ?: "Delivery Fee"
                val orderId = entry["orderId"] as? String ?: ""
                tvRef.text = "Order ID: #${orderId.take(8).uppercase()}"
                
                val timestamp = entry["timestamp"] as? Long ?: 0L
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                tvDate.text = sdf.format(Date(timestamp))

                val amount = entry["amount"] ?: 0.0
                tvAmount.text = "+₹${amount}"
            }
        }
    }
}
