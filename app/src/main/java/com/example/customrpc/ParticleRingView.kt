package com.example.customrpc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotationAngle = 0f
    private var animator: ValueAnimator? = null
    
    // State: 0=Offline (Red Particles), 1=Connecting (Yellow), 2=Online (Green Solid)
    private var state = 0 
    
    // Particles
    private data class Particle(var angle: Float, var radiusOffset: Float, var size: Float, var speed: Float, var alpha: Int)
    private val particles = ArrayList<Particle>()
    private val particleCount = 60
    
    init {
        // Initialize random particles for the "Comet" effect
        for (i in 0 until particleCount) {
            particles.add(Particle(
                angle = Random.nextFloat() * 360f,
                radiusOffset = (Random.nextFloat() - 0.5f) * 20f, // Width variation
                size = Random.nextFloat() * 6f + 2f,
                speed = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextInt(100, 255)
            ))
        }
    }

    fun setStatus(status: Int) {
        state = status
        updateAnimator()
        invalidate()
    }

    private fun updateAnimator() {
        // Animation runs for ALL states now (Offline, Connecting, Online)
        if (animator == null || !animator!!.isRunning) {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000 // 3 seconds for full rotation
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { 
                    rotationAngle = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) - 20f 
        
        // 1. Draw Background Circle (Dark Grey)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2F3136")
        canvas.drawCircle(cx, cy, radius, paint)

        // 2. Draw Particles (All States)
        val baseColor = when (state) {
            0 -> Color.parseColor("#ED4245") // Offline: Red
            1 -> Color.parseColor("#FAA61A") // Connecting: Yellow
            2 -> Color.parseColor("#57F287") // Online: Green
            else -> Color.WHITE
        }
        
        paint.style = Paint.Style.FILL
        
        for (p in particles) {
            // Determine current angle of particle
            val currentAngle = (p.angle + rotationAngle * p.speed) % 360f
            val rad = Math.toRadians(currentAngle.toDouble())
            
            // Calculate position
            val r = radius + p.radiusOffset
            val px = cx + (r * cos(rad)).toFloat()
            val py = cy + (r * sin(rad)).toFloat()
            
            // Set Alpha/Color
            paint.color = baseColor
            paint.alpha = p.alpha
            
            canvas.drawCircle(px, py, p.size, paint)
        }
        
        // Reset alpha
        paint.alpha = 255
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
