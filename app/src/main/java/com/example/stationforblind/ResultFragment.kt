package com.example.stationforblind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Locale

class ResultFragment : Fragment() {
    private var busData: BusInformation? = null
    private var searchKeyword: String? = null
    private lateinit var tts : TextToSpeech

    companion object {
        private const val ARG_BUS_DATA = "bus_data"

        fun newInstance(busData: BusInformation, searchKeyword: String?): ResultFragment {
            return ResultFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_BUS_DATA, busData)
                    putString("search_keyword", searchKeyword)
                }
            }
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        busData = arguments?.getParcelable(ARG_BUS_DATA)
        searchKeyword = arguments?.getString("search_keyword")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        tts = TextToSpeech(requireActivity()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS 초기화 성공
                tts.language = Locale.KOREAN
            } else {
                // TTS 초기화 실패
                println("Failed to initialize text to speech")
            }
        }

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
            intent.putExtra("bus_number", busData?.busNumber)
            intent.putExtra("search_keyword", searchKeyword)
            startActivity(intent)
        }
        val speechString = "${busData?.sourceName}에서 ${busData?.busNumber}번 버스를 타고 " +
                "${busData?.destinationName}으로 가는 버스입니다 " +
                "${busData?.numberOfStops}개의 정류장을 이동하며 ${busData?.travelTime}분이 소요됩니다"
        tts.speak(speechString, TextToSpeech.QUEUE_FLUSH, null, null)
        return view
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
