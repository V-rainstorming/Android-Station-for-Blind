package com.example.stationforblind

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

class ArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var currentAngle = -80f
    private var prevAngle = -80f

    private val paint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 15f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = 320f

        val startAngle = when {
            currentAngle <= 180f -> -80f
            currentAngle >= 340f -> 260f
            else -> currentAngle - 80f
        }
        val targetAngle = when {
            currentAngle >= 340f -> 0f
            currentAngle >= 180f -> 340f - currentAngle
            currentAngle >= 20f -> currentAngle - 20f
            else -> 0f
        }
        canvas.drawArc(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius,
            startAngle, targetAngle, false, paint)
    }

    fun startFetchingAngleFromServer(angle : Float) {
        prevAngle = currentAngle
        currentAngle = angle
        startAnimation()
    }

    private fun startAnimation() {
        // 시계
        if (prevAngle <= currentAngle) {
            if (currentAngle - prevAngle > 180f) {
                prevAngle += 360f
            }
        }
        // 반시계
        else {
            if (prevAngle - currentAngle > 180f) {
                prevAngle -= 360f
            }
        }
        ValueAnimator.ofFloat(prevAngle, currentAngle).apply {
            duration = 100
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                currentAngle = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
