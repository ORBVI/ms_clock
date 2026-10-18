package com.huanshi.timesync

import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** One monotonic clock drives every part of the photographic target. */
class MainActivity : AppCompatActivity() {
    private lateinit var target: SyncTargetView
    private lateinit var pauseButton: Button
    private lateinit var controls: LinearLayout
    private var origin = SystemClock.elapsedRealtimeNanos()
    private var heldNanos = 0L
    private var sequence = 0L
    private var paused = false
    private var light = false
    private var controlsHidden = true
    private var minimal = true
    private var redDigits = true
    private var active = false
    private var previousFrame = 0L
    private var averageInterval = 0.0
    private val choreographer by lazy { Choreographer.getInstance() }

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!active) return
            if (!paused) {
                val now = SystemClock.elapsedRealtimeNanos()
                if (previousFrame != 0L) {
                    val interval = (frameTimeNanos - previousFrame) / 1_000_000.0
                    averageInterval = if (averageInterval == 0.0) interval
                        else averageInterval * 0.9 + interval * 0.1
                }
                previousFrame = frameTimeNanos
                sequence++
                target.update((now - origin) / 1_000_000L, sequence, averageInterval, false)
            }
            choreographer.postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            origin = it.getLong("origin", origin)
            heldNanos = it.getLong("held")
            sequence = it.getLong("sequence")
            paused = it.getBoolean("paused")
            light = it.getBoolean("light")
            controlsHidden = it.getBoolean("hidden")
            minimal = it.getBoolean("minimal", true)
            redDigits = it.getBoolean("redDigits", true)
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContentView(R.layout.activity_main)
        target = findViewById(R.id.syncTarget)
        controls = findViewById(R.id.controls)
        pauseButton = findViewById(R.id.pauseButton)
        pauseButton.setOnClickListener {
            if (paused) origin = SystemClock.elapsedRealtimeNanos() - heldNanos
            else heldNanos = SystemClock.elapsedRealtimeNanos() - origin
            paused = !paused
            previousFrame = 0L
            refreshState()
        }
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            origin = SystemClock.elapsedRealtimeNanos()
            heldNanos = 0
            sequence = 0
            previousFrame = 0
            averageInterval = 0.0
            refreshState()
        }
        findViewById<Button>(R.id.themeButton).setOnClickListener {
            if (minimal) redDigits = !redDigits else light = !light
            refreshState()
        }
        findViewById<Button>(R.id.modeButton).setOnClickListener {
            minimal = !minimal
            controlsHidden = minimal
            refreshState()
        }
        findViewById<Button>(R.id.focusButton).setOnClickListener {
            controlsHidden = true
            refreshState()
            hideSystemBars()
        }
        target.setOnClickListener {
            if (controlsHidden) {
                controlsHidden = false
                refreshState()
            }
        }
        refreshState()
    }

    private fun refreshState() {
        controls.visibility = if (controlsHidden) View.GONE else View.VISIBLE
        pauseButton.setText(if (paused) R.string.control_resume else R.string.control_pause)
        findViewById<Button>(R.id.themeButton).setText(if (minimal) {
            if (redDigits) R.string.control_white_digits else R.string.control_red_digits
        } else if (light) R.string.control_dark else R.string.control_light)
        findViewById<Button>(R.id.modeButton).setText(
            if (minimal) R.string.control_target else R.string.control_minimal
        )
        target.minimal = minimal
        target.redDigits = redDigits
        target.light = light
        target.focused = controlsHidden
        target.update(if (paused) heldNanos / 1_000_000L
            else (SystemClock.elapsedRealtimeNanos() - origin) / 1_000_000L,
            sequence, averageInterval, paused)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        previousFrame = 0
        averageInterval = 0.0
        active = true
        choreographer.postFrameCallback(callback)
    }

    override fun onPause() {
        active = false
        choreographer.removeFrameCallback(callback)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("origin", origin)
        outState.putLong("held", heldNanos)
        outState.putLong("sequence", sequence)
        outState.putBoolean("paused", paused)
        outState.putBoolean("light", light)
        outState.putBoolean("hidden", controlsHidden)
        outState.putBoolean("minimal", minimal)
        outState.putBoolean("redDigits", redDigits)
        super.onSaveInstanceState(outState)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
