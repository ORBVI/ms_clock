package com.huanshi.timesync

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.huanshi.timesync.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val choreographer = Choreographer.getInstance()
    private val timeText = StringBuilder()
    private val elapsedText = StringBuilder()
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)

    private var baseNanoTime: Long = 0
    private var baseEpochMillis: Long = 0
    private var isRunning = false
    private var isDimmed = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            val elapsedNanos = frameTimeNanos - baseNanoTime
            val micros = (baseEpochMillis * 1000) + (elapsedNanos / 1000)

            val hours = Math.floorMod(micros / 3_600_000_000L, 24L)
            val minutes = Math.floorMod(micros / 60_000_000L, 60L)
            val seconds = Math.floorMod(micros / 1_000_000L, 60L)
            val millis = Math.floorMod(micros / 1_000L, 1_000L)

            updateTimerText(hours, minutes, seconds, millis)
            updateElapsedText()

            if (isRunning) {
                choreographer.postFrameCallback(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val backgroundColor = getColor(R.color.time_background)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        hideSystemBars()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fullscreenButton.setOnClickListener { hideSystemBars() }
        binding.nightButton.setOnClickListener { toggleDimMode() }
    }

    override fun onResume() {
        super.onResume()
        calibrateTimebase()
        updateDateText()
        updateBatteryText()

        if (!isRunning) {
            isRunning = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    override fun onPause() {
        isRunning = false
        choreographer.removeFrameCallback(frameCallback)
        super.onPause()
    }

    private fun calibrateTimebase() {
        val nanoBefore = System.nanoTime()
        val epochMillis = System.currentTimeMillis()
        val nanoAfter = System.nanoTime()

        baseEpochMillis = epochMillis
        baseNanoTime = nanoBefore + ((nanoAfter - nanoBefore) / 2L)
    }

    private fun hideSystemBars() {
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun toggleDimMode() {
        isDimmed = !isDimmed
        val attributes = window.attributes
        attributes.screenBrightness = if (isDimmed) {
            DIMMED_SCREEN_BRIGHTNESS
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attributes
        binding.nightButton.alpha = if (isDimmed) ACTIVE_CONTROL_ALPHA else INACTIVE_CONTROL_ALPHA
    }

    private fun updateTimerText(hours: Long, minutes: Long, seconds: Long, millis: Long) {
        timeText.clear()

        appendTwoDigits(hours)
        timeText.append(':')
        appendTwoDigits(minutes)
        timeText.append(':')
        appendTwoDigits(seconds)
        timeText.append('.')
        appendThreeDigits(millis)

        val nextTime = timeText.toString()
        if (binding.timerText.text.toString() != nextTime) {
            binding.timerText.text = nextTime
        }
    }

    private fun updateElapsedText() {
        val elapsedMillis = SystemClock.elapsedRealtime()
        val hours = elapsedMillis / MILLIS_PER_HOUR
        val minutes = Math.floorMod(elapsedMillis / MILLIS_PER_MINUTE, 60L)
        val seconds = Math.floorMod(elapsedMillis / MILLIS_PER_SECOND, 60L)
        val millis = Math.floorMod(elapsedMillis, MILLIS_PER_SECOND)

        elapsedText.clear()
        elapsedText.append(hours)
        elapsedText.append(':')
        appendTwoDigits(elapsedText, minutes)
        elapsedText.append(':')
        appendTwoDigits(elapsedText, seconds)
        elapsedText.append('.')
        appendThreeDigits(elapsedText, millis)
        val nextElapsed = elapsedText.toString()
        if (binding.elapsedStatus.text.toString() != nextElapsed) {
            binding.elapsedStatus.text = nextElapsed
        }
    }

    private fun updateDateText() {
        binding.dateText.text = dateFormat.format(Date(baseEpochMillis))
    }

    private fun updateBatteryText() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_BATTERY_LEVEL)
            ?: UNKNOWN_BATTERY_LEVEL
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_BATTERY_LEVEL)
            ?: UNKNOWN_BATTERY_LEVEL

        if (level >= 0 && scale > 0) {
            val percent = (level * BATTERY_PERCENT_SCALE) / scale
            binding.batteryStatus.text = getString(R.string.battery_percent, percent)
        } else {
            binding.batteryStatus.setText(R.string.battery_unknown)
        }
    }

    private fun appendTwoDigits(value: Long) {
        appendDigit(value / 10L)
        appendDigit(Math.floorMod(value, 10L))
    }

    private fun appendThreeDigits(value: Long) {
        appendDigit(value / 100L)
        appendDigit(Math.floorMod(value / 10L, 10L))
        appendDigit(Math.floorMod(value, 10L))
    }

    private fun appendTwoDigits(builder: StringBuilder, value: Long) {
        builder.append(('0'.code + (value / 10L).toInt()).toChar())
        builder.append(('0'.code + Math.floorMod(value, 10L).toInt()).toChar())
    }

    private fun appendThreeDigits(builder: StringBuilder, value: Long) {
        builder.append(('0'.code + (value / 100L).toInt()).toChar())
        builder.append(('0'.code + Math.floorMod(value / 10L, 10L).toInt()).toChar())
        builder.append(('0'.code + Math.floorMod(value, 10L).toInt()).toChar())
    }

    private fun appendDigit(value: Long) {
        timeText.append(('0'.code + value.toInt()).toChar())
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val BATTERY_PERCENT_SCALE = 100
        const val UNKNOWN_BATTERY_LEVEL = -1
        const val DIMMED_SCREEN_BRIGHTNESS = 0.08f
        const val ACTIVE_CONTROL_ALPHA = 1f
        const val INACTIVE_CONTROL_ALPHA = 0.72f
    }
}
