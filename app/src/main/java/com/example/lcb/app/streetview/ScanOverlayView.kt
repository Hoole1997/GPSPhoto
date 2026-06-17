package com.example.lcb.app.streetview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A circular radar-style scanning overlay drawn at a given screen point.
 * Shows a translucent dome, an expanding pulse ring and a rotating sweep,
 * conveying that the area is being scanned for street view data.
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var active = false

    private var sweepAngle = 0f
    private var pulseFraction = 0f

    private val accent = Color.parseColor("#4A9CFF")

    private val domePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = accent
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accent
    }

    private var sweepAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    fun start(x: Float, y: Float, scanRadius: Float) {
        centerX = x
        centerY = y
        radius = scanRadius
        active = true
        visibility = VISIBLE

        sweepAnimator?.cancel()
        sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sweepAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** 地图平移/缩放时，更新罩子在屏幕上的中心与半径（不重启动画）。 */
    fun updateGeometry(x: Float, y: Float, scanRadius: Float) {
        if (!active) return
        centerX = x
        centerY = y
        radius = scanRadius
        invalidate()
    }

    fun stop() {
        active = false
        sweepAnimator?.cancel()
        pulseAnimator?.cancel()
        sweepAnimator = null
        pulseAnimator = null
        visibility = GONE
        invalidate()
    }

    fun isActive(): Boolean = active

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active || radius <= 0f) return

        // 半透明圆形罩子（径向渐变，中心亮、边缘淡）
        domePaint.shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                Color.parseColor("#334A9CFF"),
                Color.parseColor("#1A4A9CFF"),
                Color.parseColor("#0D4A9CFF")
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, domePaint)

        // 外圈边框
        canvas.drawCircle(centerX, centerY, radius, ringPaint)

        // 扩散脉冲圈
        val pulseRadius = radius * pulseFraction
        pulsePaint.color = withAlpha(accent, (255 * (1f - pulseFraction)).toInt())
        if (pulseRadius > 0f) {
            canvas.drawCircle(centerX, centerY, pulseRadius, pulsePaint)
        }

        // 旋转扫描扇形
        canvas.save()
        canvas.rotate(sweepAngle, centerX, centerY)
        sweepPaint.shader = SweepGradient(
            centerX, centerY,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                withAlpha(accent, 90),
                withAlpha(accent, 160)
            ),
            floatArrayOf(0f, 0.7f, 0.93f, 1f)
        )
        canvas.drawCircle(centerX, centerY, radius, sweepPaint)
        canvas.restore()

        // 中心点
        canvas.drawCircle(centerX, centerY, dp(4f), centerDotPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
