package com.example.stationforblind

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class SearchResult : AppCompatActivity() {
    // variables for speech recognition
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechDialog : Dialog
    private lateinit var speechPrompt : TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSpeech : ImageButton

    // text-to-speech
    private lateinit var tts : TextToSpeech

    // variables for search result
    private lateinit var items : MutableList<BusData>
    private lateinit var llSearchResult : LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        items = mutableListOf()
        // initialize speech recognizer
        initializeSpeechRecognizer()

        // TextToSpeech 객체 생성
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS 초기화 성공
                tts.language = Locale.KOREAN
            } else {
                // TTS 초기화 실패
                println("Failed to initialize text to speech")
            }
        }

        // linear layout which contain all results
        llSearchResult = findViewById(R.id.ll_results)
        //updateLayout()

        // post method
        val searchKeyword = intent.getStringExtra("searchKeyword")
        findViewById<TextView>(R.id.tv_search_keyword).text = searchKeyword
        var text = searchKeyword + "로 검색한 결과입니다 "
        val input = HashMap<String, Any>()
        input["uuid"] = "2320102FA2"
        input["station_name"] = "$searchKeyword"
        RetrofitBuilder.api.getPostList(input).enqueue(object : Callback<Post> {
            override fun onResponse(call: Call<Post>, response: Response<Post>) {
                if (response.isSuccessful) {
                    val busInfo = response.body()
                    when (busInfo?.code) {
                        200 -> {
                            // 성공적인 응답 처리
                            busInfo.busData.forEach { busData ->
                                items.add(
                                    BusData(busData.busNumber, busData.travelTime,
                                    busData.numberOfStops, busData.busColor,
                                    busData.sourceName, busData.destinationName,
                                    busData.busID, busData.sourceID, busData.destinationID)
                                )
                            }
                            updateLayout()
                        }
                        513 -> {
                            val tvErrorMsg = findViewById<TextView>(R.id.tv_error_message)
                            tvErrorMsg.text = "가능한 경로가 존재하지 않습니다."
                            tvErrorMsg.visibility = View.VISIBLE
                            text += "가능한 경로가 존재하지 않습니다 다시 검색해주세요"
                        }
                        514 -> {
                            val tvErrorMsg = findViewById<TextView>(R.id.tv_error_message)
                            tvErrorMsg.text = "현재 운행중인 버스가 존재하지 않습니다."
                            tvErrorMsg.visibility = View.VISIBLE
                            text += "현재 운행중인 버스가 존재하지 않습니다"
                        }
                        else -> println("Error: ${busInfo?.code}")
                    }
                }
                else {
                    // 오류 처리
                    println("Error: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<Post>, t: Throwable) { }
        })

        // set button to speech recognition
        btnSpeech = findViewById(R.id.btn_voice_recognition)
        btnSpeech.setOnClickListener {
            showSpeechDialog()
            startListening()
        }
        speakText(text)

    }

    private fun updateLayout() {
        // set each result's data and add into linear layout
        for (item in items) {
            val itemView = LayoutInflater.from(this@SearchResult).inflate(R.layout.component_search_result, llSearchResult, false)
            val travelTimeText = item.travelTime.toString() + "분"
            val numberOfStopsText = item.numberOfStops.toString() + "개 정류장 이동"
            itemView.findViewById<TextView>(R.id.tv_bus_number).text = item.busNumber.toString()
            itemView.findViewById<TextView>(R.id.tv_travel_time).text = travelTimeText
            itemView.findViewById<TextView>(R.id.tv_number_of_stops).text = numberOfStopsText
            itemView.findViewById<TextView>(R.id.tv_source_name).text = item.sourceName
            itemView.findViewById<TextView>(R.id.tv_destination_name).text = item.destinationName
            itemView.setOnClickListener {
                val intent = Intent(this@SearchResult, NavigateStation::class.java)
                intent.putExtra("bus_id", item.busID)
                intent.putExtra("source_id", item.sourceID)
                intent.putExtra("destination_id", item.destinationID)
                intent.putExtra("source_name", item.sourceName)
                startActivity(intent)
            }
            llSearchResult.addView(itemView)
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    progressBar.visibility = View.VISIBLE
                    speechPrompt.text = "말씀해주세요..."
                }

                override fun onResults(results: Bundle?) {
                    progressBar.visibility = View.GONE
                    speechDialog.dismiss()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val intent = Intent(this@SearchResult, SearchResult::class.java).apply {
                        putExtra("searchKeyword", matches?.get(0))
                    }
                    startActivity(intent)
                }
                override fun onEndOfSpeech() {
                    progressBar.visibility = View.GONE
                }
                override fun onError(error: Int) {
                    progressBar.visibility = View.GONE
                    speechDialog.dismiss()
                    println("Error occurred: $error")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer.startListening(intent)
    }

    private fun showSpeechDialog() {
        speechDialog = Dialog(this).apply {
            setContentView(R.layout.dialog_speech_recognition)
            setTitle("Speech Recognition")
            progressBar = findViewById(R.id.progress_bar)
            speechPrompt = findViewById(R.id.tv_speech_prompt)
            setCancelable(true)
            show()
        }
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        // TextToSpeech 객체 종료
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}