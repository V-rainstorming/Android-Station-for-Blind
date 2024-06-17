package com.example.stationforblind

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.Q)
class MainActivity : AppCompatActivity(), SensorEventListener {
    // Speech Recognizer
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechDialog : Dialog
    private lateinit var speechPrompt : TextView
    private lateinit var progressBar: ProgressBar
    // Views
    private lateinit var btnVoiceRecognizer : ImageButton
    private lateinit var btnOnSearch : ImageButton
    private lateinit var searchKeyword : EditText

    // Variables for handling shake
    private lateinit var sensorManager : SensorManager
    private var accelerometer : Sensor?= null
    private var shakeThreshold = 15f
    private var isVoiceRecognizing = false

    private lateinit var tts : TextToSpeech
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //addOnBackPressedCallback()
        // 가속도 센서 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // TextToSpeech 객체 생성
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS 초기화 성공
                tts.language = Locale.KOREAN
                speakText("목적지를 입력해주세요. 핸드폰을 흔들면 음성인식이 실행됩니다.")
            } else {
                // TTS 초기화 실패
                println("Failed to initialize text to speech")
            }
        }
        // View Init
        btnVoiceRecognizer = findViewById(R.id.btn_voice_recognition)
        btnOnSearch = findViewById(R.id.btn_on_search)
        searchKeyword = findViewById(R.id.et_search_bar)
        searchKeyword.setOnEditorActionListener { _, _, event ->
            // 검색 중에 엔터 눌렀을 때
            if (event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                // 검색 버튼 클릭 시와 동일한 동작 실행
                val intent = Intent(this, SearchResult::class.java).apply {
                    putExtra("searchKeyword", searchKeyword.text.toString())
                }
                startActivity(intent)
                true // 이벤트 소비
            } else {
                false // 이벤트 전파
            }
        }

        // 버튼 클릭 시 다음 액티비티로 전환
        btnOnSearch.setOnClickListener {
            val intent = Intent(this, SearchResult::class.java).apply {
                putExtra("searchKeyword", searchKeyword.text.toString())
            }
            startActivity(intent)
        }

        // initialize speechRecognizer
        checkAudioPermission()
        initializeSpeechRecognizer()
        btnVoiceRecognizer.setOnClickListener {
            showSpeechDialog()
            startListening()
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isVoiceRecognizing = true
                    progressBar.visibility = View.VISIBLE
                    speechPrompt.text = "말씀해주세요..."
                    //speakText("말씀해주세요.")
                }

                override fun onResults(results: Bundle?) {
                    isVoiceRecognizing = false
                    progressBar.visibility = View.GONE
                    speechDialog.dismiss()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val intent = Intent(this@MainActivity, SearchResult::class.java).apply {
                        putExtra("searchKeyword", matches?.get(0))
                    }
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

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun speakText(text : String) {
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

    private fun addOnBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        }

        this.onBackPressedDispatcher.addCallback(this, callback)
    }
}