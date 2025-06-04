/**
 * Three Body Problem Simulation
 * 
 * This application simulates the classical three-body gravitational problem with an optional
 * reflective planet. It demonstrates gravitational interactions between three stars and includes
 * features like color customization, gravity parameter controls, and a unique reflective planet
 * that responds to the gravitational field but doesn't influence it.
 *
 * Key Features:
 * - Real-time gravitational simulation of three bodies
 * - Adjustable gravity strength and range
 * - Interactive color selection for each star
 * - Optional reflective planet that shows light mixing
 * - Persistent statistics tracking
 * - Smooth camera tracking
 */

package com.example.threebodysimulation

import android.os.Bundle
import android.view.WindowManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.SeekBar
import android.widget.CheckBox
import android.graphics.drawable.GradientDrawable
import android.app.Dialog
import android.view.Window
import android.view.ViewGroup
import android.view.Gravity
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.ambilwarna.AmbilWarnaDialog
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    // UI Components
    private lateinit var surfaceView: SurfaceView
    private lateinit var statsText: TextView
    private lateinit var gravityRangeSlider: SeekBar
    private lateinit var gravityStrengthSlider: SeekBar
    private lateinit var reflectivePlanetToggle: CheckBox

    // Animation and rendering
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val paint = Paint()

    // Screen metrics
    private var screenWidth = 0f
    private var screenHeight = 0f

    // Simulation state
    private var startTime = System.currentTimeMillis()
    private var totalSimulations = 0
    private var maxSimTime = 0f
    private var camera = Camera()
    private var bodies = mutableListOf<Body>()
    private var reflectivePlanet: ReflectivePlanet? = null

    // Visual configuration
    private var colors = arrayOf("#ffc72e", "#00ffff", "#ffffff")
    private var colorButtons = arrayOfNulls<Button>(3)
    private var baseGravityStrength = 0.44f
    private var baseGravityRange = 9f

    // Color sequences for each button
    private val colorSequences = arrayOf(
        arrayOf("#ffc72e", "#ff0000", "#ff00ff", "#ffff00"),  // Yellow -> Red -> Magenta -> Yellow
        arrayOf("#00ffff", "#00ff00", "#0000ff", "#00ffff"),  // Cyan -> Green -> Blue -> Cyan
        arrayOf("#ffffff", "#cccccc", "#888888", "#ffffff")    // White -> Light Gray -> Gray -> White
    )
    private val colorIndices = IntArray(3) { 0 }

    /**
     * 2D Vector class for position and velocity calculations
     */
    data class Vec2(var x: Float, var y: Float) {
        operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
        operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
        operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
        operator fun times(other: Vec2) = Vec2(x * other.x, y * other.y)
        fun length() = sqrt(x * x + y * y)
        
        companion object {
            fun random() = Vec2(Random.nextFloat(), Random.nextFloat())
            fun fromPolar(r: Float, angle: Float) = Vec2(
                (cos(angle) * r).toFloat(),
                (sin(angle) * r).toFloat()
            )
        }
    }

    /**
     * Camera class for view transformation and smooth tracking
     */
    data class Camera(
        var position: Vec2 = Vec2(0f, 0f),
        var zoom: Float = 0.875f
    )

    /**
     * Represents a gravitational body (star) in the simulation
     */
    inner class Body(
        var position: Vec2,
        val mass: Float,
        var color: Int,
        var velocity: Vec2 = Vec2(0f, 0f)
    ) {
        val trail = mutableListOf<Vec2>()
        private val maxTrailLength = 66

        fun update() {
            if (trail.size > maxTrailLength) trail.removeAt(trail.size - 1)
            trail.add(0, Vec2(position.x, position.y))
            position = position + velocity
        }

        fun draw(canvas: Canvas) {
            // Draw trail
            paint.color = color
            paint.alpha = 50
            paint.strokeWidth = 3f / camera.zoom
            for (i in 0 until trail.size - 1) {
                canvas.drawLine(
                    trail[i].x, trail[i].y,
                    trail[i + 1].x, trail[i + 1].y,
                    paint
                )
            }

            // Draw body
            val radius = max(sqrt(mass) * 3.0f, 6f / camera.zoom)
            paint.shader = RadialGradient(
                position.x, position.y, radius * 2f,
                color, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            canvas.drawCircle(position.x, position.y, radius * 2.5f, paint)

            // Draw core
            paint.shader = null
            paint.color = color
            canvas.drawCircle(position.x, position.y, radius, paint)
        }

        fun isVisible(): Boolean {
            val margin = 55f
            return position.x > -margin && position.x < screenWidth + margin &&
                   position.y > -margin && position.y < screenHeight + margin
        }
    }

    /**
     * Represents a small planet that reflects light from nearby stars and responds
     * to their gravitational fields without affecting them
     */
    inner class ReflectivePlanet(
        var position: Vec2,
        var velocity: Vec2 = Vec2(0f, 0f)
    ) {
        val radius = 3f
        val trail = mutableListOf<Vec2>()
        private val maxTrailLength = 30
        private var currentColor = Color.WHITE
        private val colorTransitionSpeed = 0.1f

        private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
            val r1 = Color.red(color1)
            val g1 = Color.green(color1)
            val b1 = Color.blue(color1)
            val r2 = Color.red(color2)
            val g2 = Color.green(color2)
            val b2 = Color.blue(color2)

            val r = (r1 + (r2 - r1) * fraction).toInt()
            val g = (g1 + (g2 - g1) * fraction).toInt()
            val b = (b1 + (b2 - b1) * fraction).toInt()

            return Color.rgb(r, g, b)
        }

        private fun calculateReflectedColor(stars: List<Body>): Int {
            if (stars.isEmpty()) return Color.WHITE

            // Calculate weighted colors based on distance
            var totalWeight = 0f
            val colorWeights = stars.map { star ->
                val dist = (star.position - position).length()
                val weight = 1f / (dist * dist)  // Inverse square law for light
                totalWeight += weight
                Pair(star.color, weight)
            }

            // Mix colors based on weights
            var r = 0f
            var g = 0f
            var b = 0f
            
            colorWeights.forEach { (color, weight) ->
                val normalizedWeight = weight / totalWeight
                r += Color.red(color) * normalizedWeight
                g += Color.green(color) * normalizedWeight
                b += Color.blue(color) * normalizedWeight
            }

            return Color.rgb(r.toInt(), g.toInt(), b.toInt())
        }

        fun update(stars: List<Body>) {
            // Update trail
            if (trail.size > maxTrailLength) trail.removeAt(trail.size - 1)
            trail.add(0, Vec2(position.x, position.y))

            // Smoothly update color
            val targetColor = calculateReflectedColor(stars)
            currentColor = interpolateColor(currentColor, targetColor, colorTransitionSpeed)

            // Calculate gravitational influence from stars with much stronger effect
            var totalForce = Vec2(0f, 0f)
            for (star in stars) {
                val diff = star.position - position
                val distance = max(diff.length(), baseGravityRange * 0.25f)  // Allow even closer approach
                val direction = diff * (1f / distance)
                // Dramatically increased gravity response
                val force = min((star.mass * baseGravityStrength * 5f) / (distance * distance), 5000f)
                totalForce = totalForce + direction * force
            }

            // Update velocity with stronger response and less dampening
            velocity = velocity + totalForce * 1.5f  // Increased force multiplier
            velocity = velocity * (1f - baseGravityStrength * 0.0005f)  // Reduced dampening
            position = position + velocity
        }

        fun draw(canvas: Canvas, stars: List<Body>) {
            // Draw trail
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f / camera.zoom
            
            // Draw trail with smooth color transition
            for (i in 0 until trail.size - 1) {
                val progress = i.toFloat() / trail.size
                paint.color = interpolateColor(currentColor, Color.TRANSPARENT, progress)
                paint.alpha = (40 * (1 - progress)).toInt()
                canvas.drawLine(
                    trail[i].x, trail[i].y,
                    trail[i + 1].x, trail[i + 1].y,
                    paint
                )
            }

            // Draw the planet with reflection
            paint.style = Paint.Style.FILL
            
            // Calculate light direction from nearest star
            val nearestStar = stars.minByOrNull { (it.position - position).length() }
            if (nearestStar != null) {
                val lightDir = (nearestStar.position - position).let {
                    val len = it.length()
                    Vec2(it.x / len, it.y / len)
                }

                // Draw the planet with a larger reflection gradient
                val reflectionRadius = radius * 2.5f  // Increased reflection size
                paint.shader = RadialGradient(
                    position.x + lightDir.x * radius * 0.5f,
                    position.y + lightDir.y * radius * 0.5f,
                    reflectionRadius,
                    currentColor,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.alpha = 140  // Slightly increased reflection intensity
                canvas.drawCircle(position.x, position.y, reflectionRadius, paint)

                // Draw the core
                paint.shader = null
                paint.color = Color.WHITE
                paint.alpha = 180
                canvas.drawCircle(position.x, position.y, radius, paint)
            }
        }

        fun isVisible(): Boolean {
            val margin = 150f
            return position.x > -margin && 
                   position.x < screenWidth + margin &&
                   position.y > -margin && 
                   position.y < screenHeight + margin
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        statsText = findViewById(R.id.statsText)
        gravityRangeSlider = findViewById(R.id.gravityRangeSlider)
        gravityStrengthSlider = findViewById(R.id.gravityStrengthSlider)
        reflectivePlanetToggle = findViewById(R.id.reflectivePlanetToggle)
        surfaceView.holder.addCallback(this)

        // Load saved stats
        val prefs = getPreferences(MODE_PRIVATE)
        totalSimulations = prefs.getInt("simCount", 0)
        maxSimTime = prefs.getFloat("maxSimTime", 0f)
        
        // Set up sliders and toggle
        setupSliders()
        setupReflectivePlanetToggle()

        // Set up color buttons
        for (i in 0..2) {
            colorButtons[i] = findViewById<Button>(resources.getIdentifier("color$i", "id", packageName)).apply {
                setBackgroundResource(R.drawable.circle_button)
                background.setTint(Color.parseColor(colors[i]))
                setOnClickListener {
                    showColorPicker(i)
                }
            }
        }

        // Set up reset button
        findViewById<Button>(R.id.resetBtn).setOnClickListener {
            val currentRuntime = (System.currentTimeMillis() - startTime) / 1000f
            if (currentRuntime > maxSimTime) {
                maxSimTime = currentRuntime
                saveStats()
            }
            totalSimulations++
            saveStats()
            initBodies()
        }
    }

    private fun showColorPicker(buttonIndex: Int) {
        val initialColor = Color.parseColor(colors[buttonIndex])
        val colorPicker = AmbilWarnaDialog(
            this,
            initialColor,
            object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog) {
                    // Do nothing on cancel
                }

                override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                    // Update the color
                    val colorHex = String.format("#%06X", 0xFFFFFF and color)
                    colors[buttonIndex] = colorHex
                    colorButtons[buttonIndex]?.background?.setTint(color)
                    if (bodies.size > buttonIndex) {
                        bodies[buttonIndex].color = color
                    }
                }
            }
        )
        colorPicker.show()
    }

    private fun setupSliders() {
        // Set initial progress
        gravityRangeSlider.progress = 50
        gravityStrengthSlider.progress = 50

        // Set up listeners
        gravityRangeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-100) to range (0.1-1000)
                baseGravityRange = 0.1f + (progress / 100f) * 999.9f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        gravityStrengthSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-100) to strength (0.01-100.0)
                baseGravityStrength = 0.01f + (progress / 100f) * 99.99f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupReflectivePlanetToggle() {
        reflectivePlanetToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && reflectivePlanet == null) {
                // Create planet with more dynamic initial velocity
                val margin = 50f
                val x = margin + Random.nextFloat() * (screenWidth - 2 * margin)
                val y = margin + Random.nextFloat() * (screenHeight - 2 * margin)
                reflectivePlanet = ReflectivePlanet(
                    position = Vec2(x, y),
                    velocity = Vec2.fromPolar(
                        Random.nextFloat() * 0.5f + 0.1f,  // Reduced initial velocity for better gravity observation
                        Random.nextFloat() * 2f * Math.PI.toFloat()
                    )
                )
            } else if (!isChecked) {
                reflectivePlanet = null
            }
        }
    }

    private fun saveStats() {
        getPreferences(MODE_PRIVATE).edit().apply {
            putInt("simCount", totalSimulations)
            putFloat("maxSimTime", maxSimTime)
            apply()
        }
    }

    private fun initBodies() {
        bodies.clear()
        startTime = System.currentTimeMillis()

        // Calculate spawn area (88% of screen for wider range)
        val margin = minOf(screenWidth, screenHeight) * 0.06f
        val spawnWidth = screenWidth - 2 * margin
        val spawnHeight = screenHeight - 2 * margin

        repeat(3) { i ->
            val position = Vec2(
                margin + Random.nextFloat() * spawnWidth,
                margin + Random.nextFloat() * spawnHeight
            )
            val velocity = Vec2.fromPolar(
                Random.nextFloat() * 1.1f + 0.1f * 0.25f,
                Random.nextFloat() * 2f * Math.PI.toFloat()
            )
            bodies.add(Body(
                position = position,
                mass = Random.nextFloat() * 44f + 22f,
                color = Color.parseColor(colors[i]),
                velocity = velocity
            ))
        }
    }

    private fun updatePhysics() {
        val G = baseGravityStrength
        val alive = bodies.toList()

        // Update star physics (planet has no effect on stars)
        for (i in alive.indices) {
            for (j in i + 1 until alive.size) {
                val a = alive[i]
                val b = alive[j]
                val diff = a.position - b.position
                val distance = max(diff.length(), baseGravityRange)
                val direction = diff * (1f / distance)
                val force = min((a.mass * b.mass * G) / (distance * distance), 200000f) * 0.25f

                a.velocity = a.velocity - direction * force
                b.velocity = b.velocity + direction * force
            }
        }

        // Update positions
        bodies.forEach { it.update() }

        // Update reflective planet if enabled
        reflectivePlanet?.update(bodies)

        // Calculate visible bodies with a generous margin
        val margin = 150f
        val visibleBodies = bodies.count { body ->
            body.position.x > -margin && 
            body.position.x < screenWidth + margin &&
            body.position.y > -margin && 
            body.position.y < screenHeight + margin
        }

        // Only reset if less than 2 stars are visible
        if (visibleBodies < 2) {
            val runtime = (System.currentTimeMillis() - startTime) / 1000f
            if (runtime > maxSimTime) {
                maxSimTime = runtime
                saveStats()
            }
            totalSimulations++
            saveStats()
            initBodies()
            // Recreate reflective planet if enabled
            if (reflectivePlanetToggle.isChecked) {
                val x = screenWidth / 2 + Random.nextFloat() * 100 - 50
                val y = screenHeight / 2 + Random.nextFloat() * 100 - 50
                reflectivePlanet = ReflectivePlanet(
                    position = Vec2(x, y),
                    velocity = Vec2.fromPolar(
                        Random.nextFloat() * 0.5f,
                        Random.nextFloat() * 2f * Math.PI.toFloat()
                    )
                )
            }
            return
        }

        // Update camera position
        val positions = bodies.map { it.position }
        val centerX = (positions.maxOf { it.x } + positions.minOf { it.x }) / 2
        val centerY = (positions.maxOf { it.y } + positions.minOf { it.y }) / 2
        val targetPos = Vec2(centerX, centerY)
        val ease = 0.994f
        camera.position = camera.position * ease + targetPos * (1 - ease)
    }

    private fun updateStats() {
        val runtime = (System.currentTimeMillis() - startTime) / 1000f
        if (runtime > maxSimTime) {
            maxSimTime = runtime
            saveStats()
        }
        statsText.text = "Current Runtime: ${runtime.toInt()}s\n" +
                        "Longest Runtime: ${maxSimTime.toInt()}s\n" +
                        "Total Simulations: $totalSimulations"
    }

    private fun startAnimation() {
        isRunning = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                updatePhysics()
                drawFrame()
                updateStats()
                handler.postDelayed(this, 16)
            }
        })
    }

    private fun drawFrame() {
        val canvas = surfaceView.holder.lockCanvas()
        if (canvas != null) {
            try {
                canvas.drawColor(Color.BLACK)

                // Apply camera transform
                canvas.save()
                canvas.translate(screenWidth / 2, screenHeight / 2)
                canvas.scale(camera.zoom, camera.zoom)
                canvas.translate(-camera.position.x, -camera.position.y)

                // Draw bodies
                bodies.forEach { it.draw(canvas) }
                
                // Draw reflective planet
                reflectivePlanet?.draw(canvas, bodies)

                canvas.restore()
            } finally {
                surfaceView.holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startAnimation()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        if (bodies.isEmpty()) {
            initBodies()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        saveStats()
    }

    override fun onResume() {
        super.onResume()
        if (surfaceView.holder.surface.isValid) {
            startAnimation()
        }
    }
} 