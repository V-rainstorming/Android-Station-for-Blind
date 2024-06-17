package com.example.stationforblind

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.Q)
class OnBus : AppCompatActivity() {
    private lateinit var tts : TextToSpeech

    private lateinit var routes : MutableList<String>
    private lateinit var llRoutes : LinearLayout
    private lateinit var tvRestStops : TextView

    private var serviceID = 0
    private val baseUrl = "https://www.devhsb.com/MobileBusBlindControl?service_id="
    private lateinit var client: OkHttpClient
    private var initRoute = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_on_bus)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        serviceID = intent.getIntExtra("serviceID", 0)

        routes = mutableListOf()
        tvRestStops = findViewById(R.id.tv_rest_stops)
        llRoutes = findViewById(R.id.ll_routes)

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 무제한 타임 아웃 설정
            .build()
        startEventStream()

        // TextToSpeech 객체 생성
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS 초기화 성공
                tts.language = Locale.KOREAN
                findViewById<ConstraintLayout>(R.id.main).setOnClickListener {
                    speakText()
                }
            } else {
                // TTS 초기화 실패
                println("Failed to initialize text to speech")
            }
        }
        addOnBackPressedCallback()
    }
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            speakText()
        }
        return super.onTouchEvent(event)
    }
    private fun startEventStream() {
        val objectMapper = ObjectMapper()
        val sseUrl = "$baseUrl$serviceID"
        val request = Request.Builder()
            .url(sseUrl)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }

                response.body?.use { resBody ->
                    val source: BufferedSource = resBody.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        if (line != null && line.startsWith("data:")) {
                            val data = line.substring(5).trim()
                            val userStatus = objectMapper.readTree(data).at("/user_status").asText()
                            when (userStatus) {
                                "onBoard" -> {
                                    val responseBody = objectMapper.readValue(data, RouteData::class.java)
                                    if (!initRoute) {
                                        for (station in responseBody.routeData) {
                                            routes.add(station.stationName)
                                        }
                                        println(routes)
                                        updateLayout(routes.size)
                                        initRoute = true
                                    }
                                    else if (responseBody.routeData.size != routes.size) {
                                        routes.removeAt(0)
                                        speakText()
                                        updateLayout(routes.size)
                                        if (routes.size == 1) {
                                            client.dispatcher.cancelAll()
                                        }
                                    }
                                }
                                else -> println("Error : No data.")
                            }
                        }
                    }
                }
            }
        })
    }

    private fun updateLayout(stops : Int) {
        runOnUiThread {
            llRoutes.removeAllViews()
            for ((index, route) in routes.withIndex()) {
                when {
                    (index == routes.size - 1) -> {
                        val routeView = LayoutInflater.from(this)
                            .inflate(R.layout.component_route_end, llRoutes, false)
                        routeView.findViewById<TextView>(R.id.route_end_name).text = route
                        llRoutes.addView(routeView)
                    }

                    (index == 0) -> {
                        val routeView = LayoutInflater.from(this)
                            .inflate(R.layout.component_route_start, llRoutes, false)
                        routeView.findViewById<TextView>(R.id.route_start_name).text = route
                        llRoutes.addView(routeView)
                    }

                    else -> {
                        val routeView = LayoutInflater.from(this)
                            .inflate(R.layout.component_route_inner, llRoutes, false)
                        routeView.findViewById<TextView>(R.id.route_inner_name).text = route
                        llRoutes.addView(routeView)
                    }
                }
            }
            val restStops = when {
                (stops == 1) -> {
                    "도착"
                }

                (stops == 2) -> {
                    "곧 도착"
                }

                else -> {
                    (stops - 1).toString() + "번째 전"
                }
            }
            tvRestStops.text = restStops
        }
    }

    private fun speakText() {
        var text = "이번 정류장은 ${routes[0]}입니다 "
        text += when {
            routes.size > 2 ->
                "다음 정류장은 ${routes[1]}입니다 도착까지 " +
                        routes.size.toString() + "정류장 남았습니다"
            routes.size == 2 ->
                "다음 정류장은 하차 정류장인 ${routes[1]}입니다 " +
                        "하차 준비 해주시기 바랍니다"
            else -> "목적지에 도착하였으니 하차해주시기 바랍니다"
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        // 모든 흐름이 끝났으므로 30초 후 자동 메인 화면으로 이동
        if (routes.size == 1) {
            val handler = Handler(Looper.getMainLooper())
            val intent = Intent(this@OnBus, MainActivity::class.java)
            handler.postDelayed({
                startActivity(intent)
            }, 10000)
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun addOnBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@OnBus, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
            }
        }

        this.onBackPressedDispatcher.addCallback(this, callback)
    }
}