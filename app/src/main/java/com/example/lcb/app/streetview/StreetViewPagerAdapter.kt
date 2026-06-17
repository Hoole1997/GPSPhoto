package com.example.lcb.app.streetview

import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class StreetViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): androidx.fragment.app.Fragment {
        return when (position) {
            0 -> StreetViewListFragment.newInstance(StreetViewListFragment.TYPE_HOT)
            else -> StreetViewListFragment.newInstance(StreetViewListFragment.TYPE_NEARBY)
        }
    }
}
