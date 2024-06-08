package com.example.stationforblind

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
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
import android.widget.Toast
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

@RequiresApi(Build.VERSION_CODES.Q)
class NavigateStation : AppCompatActivity(), SensorEventListener {
    // vibrator
    private lateinit var vibrator : Vibrator

    // tts variables
    private lateinit var tts : TextToSpeech

    // View variables
    private lateinit var arcView : ArcView
    private lateinit var destinationPoint : ImageView
    private lateinit var pointArrow : ImageView

    private var busNumber = 0
    private var searchKeyword : String? = null
    private var sourceName : String? = null

    // 현재 각도와 애니메이션을 위한 이전 각도
    private var degree = 0f
    private var prevDegree = 0f
    // 북극을 기준으로 y축이 얼마나 기울었는가
    private var axisYFromNorth = 90f
    private var sensorDegree = 0f
    private var descartesDegree = 0f
    private var directionText = ""

    // N 방향 기준 수평 회전 각 구하기
    private lateinit var sensorManager : SensorManager
    //private var accelerometerValues: FloatArray? = null
    //private var magneticFieldValues: FloatArray? = null

    private var distance = 50
    private lateinit var tvDistance : TextView
    private lateinit var tvDirection : TextView

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
        setContentView(R.layout.activity_navigate_station)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Sensor 연결
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_UI)
        // Vibrator 연결
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // View 연결
        tvDistance = findViewById(R.id.tv_distance)
        tvDirection = findViewById(R.id.tv_direction)

        // View 설정
        arcView = findViewById(R.id.arc_view)
        destinationPoint = findViewById(R.id.iv_destination_point)
        pointArrow = findViewById(R.id.iv_arrow_point)

        // intent 받아오기
        val busID = intent.getIntExtra("bus_id", 0)
        val sourceID = intent.getIntExtra("source_id", 0)
        val destinationID = intent.getIntExtra("destination_id", 0)
        busNumber = intent.getIntExtra("bus_number", 0)
        searchKeyword = intent.getStringExtra("search_keyword")
        sourceName = intent.getStringExtra("source_name")
        runOnUiThread {
            findViewById<TextView>(R.id.tv_navi_source).text = sourceName
        }

        // TextToSpeech 객체 생성
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS 초기화 성공
                tts.language = Locale.KOREAN
                speakText("${sourceName}으로 안내합니다")
            } else {
                // TTS 초기화 실패
                println("Failed to initialize text to speech")
            }
        }

        retrofitGetServiceID(busID, sourceID, destinationID)
    }
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            if (distance >= 100) {
                val text = "${sourceName}까지 ${directionText}으로 ${distance / 100}m 남았습니다."
                speakText(text)
            } else {
                val text = "${sourceName}까지 ${directionText}으로 ${distance}cm 남았습니다."
                speakText(text)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun retrofitGetServiceID(busID: Int, sourceID: Int, destinationID: Int) {
        val input = HashMap<String, Any>()
        input["bus_id"] = busID
        input["depature_station_id"] = sourceID
        input["destination_station_id"] = destinationID
        RetrofitBuilder.api.getServiceID(input).enqueue(object : Callback<ServiceID> {
            override fun onResponse(call: Call<ServiceID>, response: Response<ServiceID>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    when (responseBody?.code) {
                        200 -> {
                            // 성공적인 응답 처리
                            serviceID = responseBody.serviceID
                            client = OkHttpClient.Builder()
                                .readTimeout(0, TimeUnit.MILLISECONDS) // 무제한 타임 아웃 설정
                                .build()
                            startEventStream()
                        }
                        else -> println("Error: ${responseBody?.code}")
                    }
                }
                else {
                    // 오류 처리
                    println("Error: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<ServiceID>, t: Throwable) { }
        })
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
                            val responseBody = objectMapper.readValue(data, StreamData::class.java)
                            when (responseBody?.userStatus) {
                                "findStation" -> {
                                    // 성공적인 응답 처리
                                    busDataWithPos = responseBody.busDataWithPos
                                }
                                "waiting", "onBoard" -> {
                                    client.dispatcher.cancelAll()
                                    val intent = Intent(this@NavigateStation, WaitBus::class.java)
                                    intent.putExtra("serviceID", serviceID)
                                    intent.putExtra("bus_number", busNumber)
                                    intent.putExtra("search_keyword", searchKeyword)
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
        val x1 = busDataWithPos.stationX
        val y1 = busDataWithPos.stationY
        val x2 = busDataWithPos.userX
        val y2 = busDataWithPos.userY
        val dx = x1 - x2
        val dy = y1 - y2
        val deg = atan2(dy, dx)
        descartesDegree = ((Math.toDegrees(deg) + 360.0) % 360.0).toFloat()
        //degree = (abs(sensorDegree - 360f) + axisYFromNorth + (450f - descartesDegree) % 360f) % 360f
        //println("SensorDegree: $sensorDegree, Degree: $degree, DescartesDegree: $descartesDegree")
        val prevDistance = distance
        distance = busDataWithPos.distanceUserStation
        if (prevDistance >= 100 && distance < 100) {
            val text = "${sourceName}까지 ${directionText}으로 ${distance}cm 남았습니다."
            speakText(text)
        }
        updateDistanceAndDirection()
    }

    override fun onSensorChanged(event: SensorEvent) {
        sensorDegree = event.getAzimuthDegrees()
        println("SensorDegree : $sensorDegree")
        calculateDegreeFromCoordination()
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
                tvDistance.text = spannableText
            } else if (distance >= 50) {
                val text = distance.toString() + "cm"
                val spannableText = SpannableStringBuilder(text)

                spannableText.setSpan(
                    ForegroundColorSpan(Color.parseColor("#AAAAAA")),
                    text.length - 2,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tvDistance.text = spannableText
            } else {
                tvDistance.text = "50cm 이내"
            }

            prevDegree = degree
            degree = (abs(sensorDegree - 360f) + axisYFromNorth + (450f - descartesDegree) % 360f) % 360f
            if (degree >= 359f || degree <= 1f)
                triggerVibration()
            directionText = when {
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
            tvDirection.text = directionText

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

            arcView.startFetchingAngleFromServer(degree)
            ValueAnimator.ofFloat(prevDegree, degree).apply {
                duration = 100
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    degree = animator.animatedValue as Float
                    destinationPoint.rotation = degree
                    pointArrow.rotation = degree
                }
                start()
            }
        }
    }

    private fun triggerVibration() {
        // 두둥 하는 진동 패턴 설정
        val pattern = longArrayOf(0, 200, 100, 200)

        // 진동 패턴 실행
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }

    private fun speakText(text : String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_UI)
    }


    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}