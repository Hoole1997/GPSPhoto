package com.example.lcb.app.streetview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.lcb.app.R

class HotSpotAdapter(
    private val onClick: (StreetViewSpot) -> Unit
) : ListAdapter<StreetViewSpot, HotSpotAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hot_spot, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.cover)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val address: TextView = itemView.findViewById(R.id.address)
        private val description: TextView = itemView.findViewById(R.id.description)

        fun bind(spot: StreetViewSpot) {
            title.text = spot.title
            address.text = spot.subtitle
            description.text = spot.description ?: ""
            Glide.with(cover)
                .load(spot.coverUrl)
                .centerCrop()
                .transform(RoundedCorners(cover.resources.displayMetrics.density.times(10).toInt()))
                .placeholder(R.drawable.bg_cover_placeholder)
                .error(R.drawable.bg_cover_placeholder)
                .into(cover)
            itemView.setOnClickListener { onClick(spot) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<StreetViewSpot>() {
            override fun areItemsTheSame(a: StreetViewSpot, b: StreetViewSpot): Boolean {
                return a.title == b.title && a.position == b.position
            }

            override fun areContentsTheSame(a: StreetViewSpot, b: StreetViewSpot): Boolean {
                return a == b
            }
        }
    }
}
