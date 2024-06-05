package com.example.stationforblind

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class OnBus : AppCompatActivity() {
    private lateinit var routes : MutableList<Route>
    private lateinit var llRoutes : LinearLayout
    private lateinit var btnTts : ImageButton
    private lateinit var tvRestStops : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_on_bus)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        routes = mutableListOf(
            Route("용남시장"),
            Route("학산소극장"),
            Route("정석항공과학고"),
            Route("인하대 후문")
        )

        tvRestStops = findViewById(R.id.tv_rest_stops)
        llRoutes = findViewById(R.id.ll_routes)
        updateLayout(4)

        btnTts = findViewById(R.id.image_btn_tts)
        btnTts.setOnClickListener {
            updateData()
        }
    }

    private fun updateData() {
        routes.removeAt(0)
        if (routes.size == 0) {
            val intent = Intent(this@OnBus, MainActivity::class.java)
            startActivity(intent)
        }
        updateLayout(routes.size)
    }

    private fun updateLayout(stops : Int) {
        llRoutes.removeAllViews()
        for ((index, route) in routes.withIndex()) {
            when {
                (index == routes.size - 1) -> {
                    val routeView = LayoutInflater.from(this)
                        .inflate(R.layout.component_route_end, llRoutes, false)
                    routeView.findViewById<TextView>(R.id.route_end_name).text = route.routeName
                    llRoutes.addView(routeView)
                }
                (index == 0) -> {
                    val routeView = LayoutInflater.from(this)
                        .inflate(R.layout.component_route_start, llRoutes, false)
                    routeView.findViewById<TextView>(R.id.route_start_name).text = route.routeName
                    llRoutes.addView(routeView)
                }
                else -> {
                    val routeView = LayoutInflater.from(this)
                        .inflate(R.layout.component_route_inner, llRoutes, false)
                    routeView.findViewById<TextView>(R.id.route_inner_name).text = route.routeName
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


data class Route(val routeName : String)