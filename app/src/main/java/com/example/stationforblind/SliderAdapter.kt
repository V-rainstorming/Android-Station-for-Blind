package com.example.stationforblind

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SliderAdapter (
    fragmentActivity: SearchResult,
    private val busDataList: List<BusInformation>,
    private val searchKeyword: String?
) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int {
        println("Adapter received data: $busDataList")
        return busDataList.size
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun createFragment(position: Int): Fragment {
        println("Adapter received data: $busDataList")
        return ResultFragment.newInstance(busDataList[position], searchKeyword)
    }
}