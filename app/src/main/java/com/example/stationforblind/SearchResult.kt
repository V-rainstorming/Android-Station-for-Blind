package com.example.stationforblind

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.Q)
class SearchResult : AppCompatActivity(), SensorEventListener {
    // variables for speech recognition
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechDialog : Dialog
    private lateinit var speechPrompt : TextView
    private lateinit var progressBar: ProgressBar
    //private lateinit var btnSpeech : ImageButton

    // text-to-speech
    lateinit var tts : TextToSpeech

    // 가속도 센서
    private lateinit var sensorManager : SensorManager
    private var accelerometer : Sensor ?= null
    private val shakeThreshold = 15f
    private var isVoiceRecognizing = false

    // variables for search result
    private lateinit var busDataList : MutableList<BusInformation>
    //private lateinit var llSearchResult : LinearLayout

    private lateinit var viewPager : ViewPager2
    private lateinit var viewPagerAdapter: SliderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 가속도 센서 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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

        //items = mutableListOf()
        busDataList = mutableListOf()
        viewPager = findViewById(R.id.viewpager)
        // initialize speech recognizer
        initializeSpeechRecognizer()


        // linear layout which contain all results
        //llSearchResult = findViewById(R.id.ll_results)

        // post method
        val searchKeyword = intent.getStringExtra("searchKeyword")
        findMappedKeyword(searchKeyword!!)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                speak(busDataList[position])
            }
        })
    }
    private fun speak(busData : BusInformation?) {
        val speechString = "${busData?.sourceName}에서 ${busData?.busNumber}번 버스를 타고 " +
                "${busData?.destinationName}으로 가는 경로로, " +
                "${busData?.travelTime}분이 소요됩니다."
        tts.speak(speechString, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    private fun findMappedKeyword(keyword: String) {
        RetrofitBuilder.api.getMappedKeyword(keyword).enqueue(object : Callback<StationNickname> {
            override fun onResponse(call : Call<StationNickname>, response: Response<StationNickname>) {
                if (response.isSuccessful) {
                    val stationName = response.body()?.stationName
                    if (stationName == null) {
                        searchNewKeyword(keyword)
                    } else {
                        searchNewKeyword(stationName)
                    }
                }
                else {
                    println("error : response is not succeed.")
                }
            }
            override fun onFailure(call : Call<StationNickname>, t : Throwable) { }
        })
    }
    private fun searchNewKeyword(keyword : String?) {
        findViewById<TextView>(R.id.tv_search_keyword).text = keyword
        findViewById<LinearLayout>(R.id.ll_search_title).setOnClickListener {
            speakText("${keyword}로 검색한 결과입니다.")
        }
        var text = "${keyword}로 검색한 결과입니다. "
        val input = HashMap<String, Any>()
        input["uuid"] = "2320102FA2"
        input["station_name"] = "$keyword"
        RetrofitBuilder.api.getPostList(input).enqueue(object : Callback<Post> {
            override fun onResponse(call: Call<Post>, response: Response<Post>) {
                if (response.isSuccessful) {
                    val busInfo = response.body()
                    when (busInfo?.code) {
                        200 -> {
                            // 성공적인 응답 처리
                            busInfo.busData.forEach { busData ->
                                busDataList.add(
                                    BusInformation(busData.busNumber, busData.busColor,
                                        busData.travelTime, busData.numberOfStops,
                                        busData.sourceName, busData.destinationName,
                                        busData.busID, busData.sourceID, busData.destinationID)
                                )
                            }
                            speakText(text)
                            //updateLayout()
                            viewPagerAdapter = SliderAdapter(this@SearchResult, busDataList, keyword)
                            viewPager.adapter = viewPagerAdapter
                        }
                        513 -> {
                            val tvErrorMsg = findViewById<TextView>(R.id.tv_error_message)
                            tvErrorMsg.text = "가능한 경로가 존재하지 않습니다."
                            tvErrorMsg.visibility = View.VISIBLE
                            text += "가능한 경로가 존재하지 않습니다. 다시 검색해주세요"
                            speakText(text)
                        }
                        514 -> {
                            val tvErrorMsg = findViewById<TextView>(R.id.tv_error_message)
                            val tmpText = "현재 운행중인 버스가\n 존재하지 않습니다."
                            tvErrorMsg.text = tmpText
                            tvErrorMsg.visibility = View.VISIBLE
                            text += "현재 운행중인 버스가 존재하지 않습니다."
                            speakText(text)
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

    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isVoiceRecognizing = true
                    progressBar.visibility = View.VISIBLE
                    speechPrompt.text = "말씀해주세요..."
                }

                override fun onResults(results: Bundle?) {
                    isVoiceRecognizing = false
                    progressBar.visibility = View.GONE
                    speechDialog.dismiss()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val intent = Intent(this@SearchResult, SearchResult::class.java).apply {
                        putExtra("searchKeyword", matches?.get(0))
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                }
                override fun onEndOfSpeech() {
                    isVoiceRecognizing = false
                    progressBar.visibility = View.GONE
                }
                override fun onError(error: Int) {
                    isVoiceRecognizing = false
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
    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration = sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
            if (acceleration > shakeThreshold) {
                // 화면 흔들림 감지 시 실행할 동작
                if (!isVoiceRecognizing) {
                    isVoiceRecognizing = true
                    showSpeechDialog()
                    startListening()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //TODO("Not yet implemented")
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        // TextToSpeech 객체 종료
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

}