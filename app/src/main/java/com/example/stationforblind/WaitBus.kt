package com.example.stationforblind

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.Q)
class WaitBus : AppCompatActivity(), SensorEventListener {
    private lateinit var busArrow : ImageView
    private lateinit var tvRestTime : TextView
    private lateinit var tvRestStation : TextView

    private lateinit var waveView1 : ImageView
    //private lateinit var waveView2 : ImageView
    //private var handlerAnimation = Handler()
    private var isPulse = false

    private var searchKeyword : String? = null
    private var busNumber = 333

    private var prevDegree = 0f
    private var degree = 0f
    //private var visibleArrow = false

    // 북극을 기준으로 y축이 얼마나 기울었는가
    private var axisYFromNorth = 0f
    private var sensorDegree = 0f
    private var descartesDegree = 0f

    // N 방향 기준 수평 회전 각 구하기
    private lateinit var sensorManager : SensorManager

    private lateinit var tts : TextToSpeech
    private var ttsFirst = true
    private lateinit var vibrator : Vibrator
    private var vibrationMode = 0

    private var distance = 50
    private var directionString = ""

    private var serviceID = 0
    private var busDataWithPos = BusDataWithPos(0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0,
        0, "", 0,
        0, 0, 0, 0)

    private val baseUrl = "http://www.devhsb.com:28900/MobileBusBlindControl?service_id="
    private lateinit var client: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_wait_bus)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_UI)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.KOREAN
                ttsFirst = true
            } else {
                // TTS 초기화 실패
                println("Failed to initialize text to speech")
            }
        }

        tvRestStation = findViewById(R.id.tv_rest_stops)
        tvRestTime = findViewById(R.id.tv_rest_time)
        busArrow = findViewById(R.id.iv_bus_arrow)
        busNumber = intent.getIntExtra("bus_number", 333)
        findViewById<TextView>(R.id.tv_bus_title).text = busNumber.toString()
        searchKeyword = intent.getStringExtra("search_keyword")

        waveView1 = findViewById(R.id.img_animation1)
        //waveView2 = findViewById(R.id.img_animation2)

        //changeImageColor(findViewById(R.id.iv_bus), Color.parseColor("#299827"), Color.parseColor("#000000"))

        addOnBackPressedCallback()

        RetrofitBuilder.api.getAzimuthFromServer().enqueue(object : Callback<Azimuth> {
            override fun onResponse(call: Call<Azimuth>, response: Response<Azimuth>) {
                if (response.isSuccessful) {
                    axisYFromNorth = response.body()?.azimuth!!.toFloat()
                    println("Server sent a azimuth value : $axisYFromNorth")
                } else {
                    println("Getting Azimuth from server is failed.")
                }
            }

            override fun onFailure(call: Call<Azimuth>, t: Throwable) { }
        })

        serviceID = intent.getIntExtra("serviceID", 0)
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 무제한 타임 아웃 설정
            .build()
        startEventStream()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            if (busDataWithPos.leftStation > 1) {
                val text = "${busNumber}번 버스 도착까지 ${busDataWithPos.leftStation}분 남았습니다."
                speakText(text)
            } else {
                val text = "${directionString}으로 ${distance / 100}m 거리에 있습니다."
                speakText(text)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun showArrow() {
        runOnUiThread {
            busArrow.visibility = View.VISIBLE
        }
    }

    private fun disableArrow() {
        runOnUiThread {
            busArrow.visibility = View.GONE
        }
    }

    private fun updateLayout() {
        if (ttsFirst) {
            ttsFirst = false
            val text = "${busNumber}번 버스 도착까지 ${busDataWithPos.leftStation}분 남았습니다."
            speakText(text)
        }
        runOnUiThread {
            val restStopsString = busDataWithPos.leftStation.toString() + "번째 전"
            val restTimeString = busDataWithPos.leftTime.toString() + "분 전"
            findViewById<TextView>(R.id.tv_rest_stops).text = restStopsString
            findViewById<TextView>(R.id.tv_rest_time).text = restTimeString
        }
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
                                "waiting" -> {
                                    // 성공적인 응답 처리
                                    val responseBody = objectMapper.readValue(data, StreamData::class.java)
                                    busDataWithPos = responseBody.busDataWithPos
                                }
                                "onBoard" -> {
                                    client.dispatcher.cancelAll()
                                    val intent = Intent(this@WaitBus, OnBus::class.java)
                                    intent.putExtra("serviceID", serviceID)
                                    startActivity(intent)
                                }
                                else -> println("Error : No data.")
                            }
                        }
                    }
                }
            }
        })
    }

    private fun calculateDegreeFromCoordination() {
        if (busDataWithPos.leftStation > 1) {
            updateLayout()
            disableArrow()
            vibrator.cancel()
            return
        }
        val x1 = busDataWithPos.busX
        val y1 = busDataWithPos.busY
        val x2 = busDataWithPos.userX
        val y2 = busDataWithPos.userY
        val dx = x1 - x2
        val dy = y1 - y2
        val deg = atan2(dy, dx)
        descartesDegree = ((Math.toDegrees(deg) + 360.0) % 360.0).toFloat()
        //println("SensorDegree: $sensorDegree, Degree: $degree, DescartesDegree: $descartesDegree")
        distance = sqrt(dx * dx + dy * dy).toInt()
        updateDistanceAndDirection()
    }

    override fun onSensorChanged(event: SensorEvent) {
        sensorDegree = event.getAzimuthDegrees()
        calculateDegreeFromCoordination()
        println("Sensor Degree : $sensorDegree")
    }
    private fun SensorEvent.getAzimuthDegrees() : Float {
        val rotationMatrix = FloatArray(9).also {
            SensorManager.getRotationMatrixFromVector(it, this.values)
        }
        val orientation = FloatArray(3).also {
            SensorManager.getOrientation(rotationMatrix, it)
        }
        val azimuthRadians = orientation.getOrElse(0) {0f}
        val azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble())
        return ((azimuthDegrees + 360.0) % 360.0).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        ;
    }

    private fun updateDistanceAndDirection() {
        runOnUiThread {
            if (distance >= 100) {
                val text = (distance / 100).toString() + "m"
                val spannableText = SpannableStringBuilder(text)

                spannableText.setSpan(
                    ForegroundColorSpan(Color.parseColor("#AAAAAA")),
                    text.length - 1,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tvRestTime.text = spannableText
            } else {
                val text = "1m 이내"
                tvRestTime.text = text
            }
            vibrateWithDistance()

            prevDegree = degree
            degree = (abs(sensorDegree - 360f) + axisYFromNorth + (450f - descartesDegree) % 360f) % 360f
            //degree = (450f - descartesDegree) % 360f
            val directionText = when {
                (degree >= 345f || degree < 15f) -> "12시 방향"
                (degree < 45f) -> "1시 방향"
                (degree < 75f) -> "2시 방향"
                (degree < 105f) -> "3시 방향"
                (degree < 135f) -> "4시 방향"
                (degree < 165f) -> "5시 방향"
                (degree < 195f) -> "6시 방향"
                (degree < 225f) -> "7시 방향"
                (degree < 255f) -> "8시 방향"
                (degree < 285f) -> "9시 방향"
                (degree < 315f) -> "10시 방향"
                else -> "11시 방향"
            }
            directionString = directionText
            tvRestStation.text = directionText

            if (distance < 100) {
                disableArrow()
                if (!isPulse) {
                    isPulse = true
                    startPulse()
                }
                return@runOnUiThread
            }

            showArrow()
            isPulse = false
            stopPulse()
            // 시계
            if (prevDegree <= degree) {
                if (degree - prevDegree > 180f) {
                    prevDegree += 360f
                }
            }
            // 반시계
            else {
                if (prevDegree - degree > 180f) {
                    prevDegree -= 360f
                }
            }

            ValueAnimator.ofFloat(prevDegree, degree).apply {
                duration = 100
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    degree = animator.animatedValue as Float
                    busArrow.rotation = degree
                }
                start()
            }
        }
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun vibrateWithDistance() {
        println("vibration mode : $vibrationMode")
        val currentMode = when {
            distance > 1000 -> 0
            distance > 500 -> 1
            distance > 300 -> 2
            distance > 100 -> 3
            else -> 4
        }
        if (currentMode == vibrationMode) return
        println("not returned.")
        vibrationMode = currentMode
        vibrator.cancel()
        if (vibrationMode == 0) return
        val pattern = when (vibrationMode) {
            1 -> longArrayOf(3000, 200, 3000, 200)
            2 -> longArrayOf(1000, 200, 1000, 200)
            3 -> longArrayOf(500, 200, 500, 200)
            else -> longArrayOf(250, 200, 250, 200)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_UI)
        vibrateWithDistance()
    }


    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        vibrator.cancel()
    }
    private fun addOnBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@WaitBus, SearchResult::class.java)
                intent.putExtra("searchKeyword", searchKeyword)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
            }
        }

        this.onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        vibrator.cancel()
        super.onDestroy()
    }

    private fun startPulse() {
        runnable.run()
    }

    private fun stopPulse() {
        Handler(Looper.getMainLooper()).removeCallbacks(runnable)
    }

    private var runnable = Runnable {
        waveView1.animate().scaleX(2f).scaleY(2f).alpha(0f).setDuration(1000)
            .withEndAction {
                waveView1.scaleX = 1f
                waveView1.scaleY = 1f
                waveView1.alpha = 1f
            }
        /*
        waveView2.animate().scaleX(2f).scaleY(2f).alpha(0f).setDuration(700)
            .withEndAction {
                waveView1.scaleX = 1f
                waveView1.scaleY = 1f
                waveView1.alpha = 1f
            }
         */
        Handler(Looper.getMainLooper()).postDelayed({
            isPulse = false
        }, 1500)
    }
    /*
    private fun changeImageColor(imageView: ImageView, targetColor: Int, newColor: Int) {
        val colorMatrix = ColorMatrix()
        colorMatrix.set(
            floatArrayOf(
                1f, 0f, 0f, 0f, (Color.red(newColor) - Color.red(targetColor)) / 255f,
                0f, 1f, 0f, 0f, (Color.green(newColor) - Color.green(targetColor)) / 255f,
                0f, 0f, 1f, 0f, (Color.blue(newColor) - Color.blue(targetColor)) / 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        imageView.colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
    */
}
