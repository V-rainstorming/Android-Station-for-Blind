package com.example.stationforblind

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SliderAdapter (
    fragmentActivity: SearchResult,
    private val busDataList: List<BusInformation>
) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int {
        println("Adapter received data: $busDataList")
        return busDataList.size
    }

    override fun createFragment(position: Int): Fragment {
        println("Adapter received data: $busDataList")
        return ResultFragment.newInstance(busDataList[position])
    }
}