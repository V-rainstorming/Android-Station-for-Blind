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
import android.speech.tts.TextToSpeech
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    // Speech Recognizer
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechDialog : Dialog
    private lateinit var speechPrompt : TextView
    private lateinit var progressBar: ProgressBar
    // Views
    private lateinit var btnVoiceRecognizer : ImageButton
    private lateinit var btnOnSearch : ImageButton
    private lateinit var searchKeyword : EditText

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

        // TextToSpeech 객체 생성
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS 초기화 성공
                tts.language = Locale.KOREAN
                speakText("NFC를 태그하거나 목적지를 입력해주세요. 핸드폰을 흔들면 음성인식이 실행됩니다.")
            } else {
                // TTS 초기화 실패
                println("Failed to initialize text to speech")
            }
        }
        // View Init
        btnVoiceRecognizer = findViewById(R.id.btn_voice_recognition)
        btnOnSearch = findViewById(R.id.btn_on_search)
        searchKeyword = findViewById(R.id.et_search_bar)

        // 버튼 클릭 시 다음 액티비티로 전환
        btnOnSearch.setOnClickListener {
            val intent = Intent(this, SearchResult::class.java).apply {
                putExtra("searchKeyword", searchKeyword.text)
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
                    progressBar.visibility = View.VISIBLE
                    speechPrompt.text = "말씀해주세요..."
                }

                override fun onResults(results: Bundle?) {
                    progressBar.visibility = View.GONE
                    speechDialog.dismiss()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val intent = Intent(this@MainActivity, SearchResult::class.java).apply {
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

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun speakText(text : String) {
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