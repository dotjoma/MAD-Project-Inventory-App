package com.example.inventory

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.Locale

class ItemAdapter(
    private var items: List<Item> = emptyList(),
    private val onItemClick: (Item) -> Unit,
    private val onReorderClick: (Item) -> Unit,
    private val onCancelReorderClick: (Item) -> Unit,
    private val onDeleteClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvStockStatus: TextView = view.findViewById(R.id.tvStockStatus)
        val btnReorder: MaterialButton = view.findViewById(R.id.btnReorder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.tvItemName.text = item.name
        holder.tvQuantity.text = "Qty: ${item.quantity}"
        holder.tvPrice.text = "₱${String.format("%.2f", item.price)}"

        // Get the card view
        val cardView = holder.itemView as com.google.android.material.card.MaterialCardView

        if (item.isLowStock) {
            holder.tvStockStatus.visibility = View.VISIBLE
            holder.tvStockStatus.text = "Low Stock"
            holder.btnReorder.visibility = View.VISIBLE
            
            // Check if item has a pending reorder
            if (item.hasPendingReorder) {
                holder.btnReorder.text = "Cancel Reorder"
                holder.btnReorder.setOnClickListener { onCancelReorderClick(item) }
                
                // Change border color if reorder date is reached
                if (item.isReorderDateReached) {
                    holder.tvStockStatus.text = "Reorder Due"
                    holder.tvStockStatus.setTextColor(context.getColor(R.color.error))
                    cardView.strokeColor = context.getColor(R.color.error)
                    cardView.strokeWidth = 4
                } else {
                    holder.tvStockStatus.setTextColor(context.getColor(R.color.warning))
                    cardView.strokeColor = context.getColor(R.color.warning)
                    cardView.strokeWidth = 2
                }
            } else {
                holder.btnReorder.text = "Reorder"
                holder.btnReorder.setOnClickListener { onReorderClick(item) }
                holder.tvStockStatus.setTextColor(context.getColor(R.color.warning))
                cardView.strokeColor = context.getColor(R.color.warning)
                cardView.strokeWidth = 2
            }
        } else {
            holder.tvStockStatus.visibility = View.GONE
            holder.btnReorder.visibility = View.GONE
            cardView.strokeWidth = 0
        }

        // Set click listeners
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { 
            onDeleteClick(item)
            true
        }
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }
} 