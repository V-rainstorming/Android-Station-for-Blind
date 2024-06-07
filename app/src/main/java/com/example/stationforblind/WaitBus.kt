package com.example.stationforblind

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class WaitBus : AppCompatActivity(), SensorEventListener {
    private lateinit var busArrow : ImageView
    private lateinit var tvRestTime : TextView
    private lateinit var tvRestStation : TextView

    private var prevDegree = 0f
    private var degree = 0f
    private var visibleArrow = false

    // 북극을 기준으로 y축이 얼마나 기울었는가
    private var axisYFromNorth = 90f
    private var sensorDegree = 0f
    private var descartesDegree = 0f

    // N 방향 기준 수평 회전 각 구하기
    private lateinit var sensorManager : SensorManager
    //private var accelerometerValues: FloatArray? = null
    //private var magneticFieldValues: FloatArray? = null

    private var distance = 50

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

        tvRestStation = findViewById(R.id.tv_rest_stops)
        tvRestTime = findViewById(R.id.tv_rest_time)
        busArrow = findViewById(R.id.iv_bus_arrow)

        serviceID = intent.getIntExtra("serviceID", 0)
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 무제한 타임 아웃 설정
            .build()
        startEventStream()
    }

    private fun showArrow() {
        runOnUiThread {
            busArrow.visibility = View.VISIBLE
        }
    }
    /*
    private fun unshowArrow() {
        busArrow.visibility = View.GONE
    }*/

    private fun updateLayout() {
        runOnUiThread {
            val restStopsString = busDataWithPos.leftStation.toString() + "번째 전"
            val restTimeString = busDataWithPos.leftStation.toString() + "분 전"
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

                                    if (busDataWithPos.leftStation == 1) {
                                        visibleArrow = true
                                        showArrow()
                                    } else {
                                        // 만약 못 탔으면,
                                        //visibleArrow = false
                                        //unshowArrow()
                                        updateLayout()
                                    }
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
        val x1 = busDataWithPos.busX
        val y1 = busDataWithPos.busY
        val x2 = busDataWithPos.userX
        val y2 = busDataWithPos.userY
        val dx = x1 - x2
        val dy = y1 - y2
        val deg = atan2(dy, dx)
        descartesDegree = ((Math.toDegrees(deg) + 360.0) % 360.0).toFloat()
        //degree = (abs(sensorDegree - 360f) + axisYFromNorth + (450f - descartesDegree) % 360f) % 360f
        //println("SensorDegree: $sensorDegree, Degree: $degree, DescartesDegree: $descartesDegree")
        distance = sqrt(dx * dx + dy * dy).toInt()
        updateDistanceAndDirection()
    }

    override fun onSensorChanged(event: SensorEvent) {
        /*
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerValues = event.values.clone()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magneticFieldValues = event.values.clone()
            }
        }
        accelerometerValues?.let { accelerometer ->
            magneticFieldValues?.let { magneticField ->
                val rotationMatrix = FloatArray(16)
                val orientation = FloatArray(3)

                SensorManager.getRotationMatrix(rotationMatrix, null, accelerometer, magneticField)
                SensorManager.getOrientation(rotationMatrix, orientation)

                sensorDegree = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                if (visibleArrow) {
                    calculateDegreeFromCoordination()
                }
            }
        }
         */
        sensorDegree = event.getAzimuthDegrees()
        if (visibleArrow) {
            calculateDegreeFromCoordination()
        }
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
            val text = distance.toString() + "m"
            val spannableText = SpannableStringBuilder(text)

            spannableText.setSpan(
                ForegroundColorSpan(Color.parseColor("#AAAAAA")),
                text.length - 1,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            tvRestTime.text = spannableText

            prevDegree = degree
            degree = (abs(sensorDegree - 360f) + axisYFromNorth + (450f - descartesDegree) % 360f) % 360f
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
            tvRestStation.text = directionText

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

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_UI)
    }


    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
