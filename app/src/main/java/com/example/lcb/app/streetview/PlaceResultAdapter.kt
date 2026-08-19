package com.example.lcb.app.streetview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.R

class PlaceResultAdapter(
    private val isHistory: Boolean,
    private val onClick: (PlaceResult) -> Unit
) : ListAdapter<PlaceResult, PlaceResultAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place_result, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val name: TextView = itemView.findViewById(R.id.placeName)
        private val address: TextView = itemView.findViewById(R.id.placeAddress)

        fun bind(place: PlaceResult) {
            name.text = place.name
            val detailAddress = place.address.trim()
            // 无二级地址时移除整行及其间距，让单行标题与左侧图标自然垂直居中。
            // 每次绑定都显式恢复 visibility，避免 RecyclerView 复用造成显示状态串行。
            address.visibility = if (detailAddress.isEmpty()) View.GONE else View.VISIBLE
            address.text = detailAddress
            icon.setImageResource(if (isHistory) R.drawable.ic_history else R.drawable.ic_place)
            itemView.setOnClickListener { onClick(place) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PlaceResult>() {
            override fun areItemsTheSame(a: PlaceResult, b: PlaceResult): Boolean {
                return a.name == b.name && a.position == b.position
            }

            override fun areContentsTheSame(a: PlaceResult, b: PlaceResult): Boolean = a == b
        }
    }
}
