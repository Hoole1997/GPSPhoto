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
import kotlin.math.roundToInt

class StreetViewAdapter(
    private val onClick: (StreetViewSpot) -> Unit
) : ListAdapter<StreetViewSpot, StreetViewAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_street_view, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumb: ImageView = itemView.findViewById(R.id.itemThumb)
        private val panoBadge: TextView = itemView.findViewById(R.id.itemPanoBadge)
        private val title: TextView = itemView.findViewById(R.id.itemTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.itemSubtitle)
        private val distance: TextView = itemView.findViewById(R.id.itemDistance)

        fun bind(spot: StreetViewSpot) {
            title.text = spot.title
            subtitle.text = spot.subtitle
            distance.text = spot.distanceMeters?.let { formatDistance(it) } ?: ""
            panoBadge.visibility = if (spot.isPano) View.VISIBLE else View.GONE

            val radius = (10 * thumb.resources.displayMetrics.density).toInt()
            val imageUrl = spot.thumbUrl ?: spot.coverUrl
            if (imageUrl != null) {
                Glide.with(thumb)
                    .load(imageUrl)
                    .centerCrop()
                    .transform(RoundedCorners(radius))
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .into(thumb)
            } else {
                Glide.with(thumb).clear(thumb)
                thumb.setImageResource(R.drawable.bg_cover_placeholder)
            }

            itemView.setOnClickListener { onClick(spot) }
        }

        private fun formatDistance(meters: Double): String {
            return if (meters < 1000) {
                "${meters.roundToInt()}m"
            } else {
                "${(meters / 100).roundToInt() / 10.0}km"
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<StreetViewSpot>() {
            override fun areItemsTheSame(a: StreetViewSpot, b: StreetViewSpot): Boolean {
                return (a.panoId ?: a.mapillaryId) == (b.panoId ?: b.mapillaryId) &&
                    a.position == b.position
            }

            override fun areContentsTheSame(a: StreetViewSpot, b: StreetViewSpot): Boolean {
                return a == b
            }
        }
    }
}
