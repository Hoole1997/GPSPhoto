package com.example.lcb.app.streetview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.R

class RecommendAdapter(
    private val items: List<StreetViewSpot>,
    private val onClick: (StreetViewSpot) -> Unit
) : RecyclerView.Adapter<RecommendAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommend_chip, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.recommendName)
        private val sub: TextView = itemView.findViewById(R.id.recommendSub)

        fun bind(spot: StreetViewSpot) {
            name.text = spot.title
            sub.text = spot.subtitle
            itemView.setOnClickListener { onClick(spot) }
        }
    }
}
