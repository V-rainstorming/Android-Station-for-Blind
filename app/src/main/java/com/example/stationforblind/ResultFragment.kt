package com.example.stationforblind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ResultFragment : Fragment() {
    private var busData: BusInformation? = null

    companion object {
        private const val ARG_BUS_DATA = "bus_data"

        fun newInstance(busData: BusInformation): ResultFragment {
            return ResultFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_BUS_DATA, busData)
                }
            }
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        busData = arguments?.getParcelable(ARG_BUS_DATA)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.component_search_result, container, false)
        val travelTimeText = busData?.travelTime.toString() + "분"
        val numberOfStopsText = busData?.numberOfStops.toString() + "개 정류장 이동"
        view?.findViewById<TextView>(R.id.tv_bus_number)?.text = busData?.busNumber.toString()
        view?.findViewById<TextView>(R.id.tv_travel_time)?.text = travelTimeText
        view?.findViewById<TextView>(R.id.tv_number_of_stops)?.text = numberOfStopsText
        view?.findViewById<TextView>(R.id.tv_source_name)?.text = busData?.sourceName
        view?.findViewById<TextView>(R.id.tv_destination_name)?.text = busData?.destinationName
        view?.setOnClickListener {
            val intent = Intent(requireActivity(), NavigateStation::class.java)
            intent.putExtra("bus_id", busData?.busID)
            intent.putExtra("source_id", busData?.sourceID)
            intent.putExtra("destination_id", busData?.destinationID)
            intent.putExtra("source_name", busData?.sourceName)
            startActivity(intent)
        }
        return view
    }
}
