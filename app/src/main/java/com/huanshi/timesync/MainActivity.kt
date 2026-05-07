package com.huanshi.timesync

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.huanshi.timesync.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val handler = Handler(Looper.getMainLooper())

    // nanoTime 和 currentTimeMillis 在同一时刻采样，建立映射关系
    private var baseNanoTime: Long = 0
    private var baseEpochMillis: Long = 0

    private val clockRunnable = object : Runnable {
        override fun run() {
            binding.timerText.text = formatTime()
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())
            .hide(WindowInsetsCompat.Type.navigationBars())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 校准：在同一时刻采样两个时钟
        baseEpochMillis = System.currentTimeMillis()
        baseNanoTime = System.nanoTime()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(clockRunnable)
        super.onPause()
    }

    /**
     * 用 nanoTime 推算当前挂钟时间（微秒精度）
     * nanoTime 单调递增，精度可达纳秒，不受系统时钟调整影响
     */
    private fun currentMicros(): Long {
        val elapsedNanos = System.nanoTime() - baseNanoTime
        return (baseEpochMillis * 1000) + (elapsedNanos / 1000)
    }

    /**
     * 格式化为 MM:SS.mmmµµµ
     * 例如 32:56.789123
     */
    private fun formatTime(): String {
        val micros = currentMicros()
        val totalSeconds = (micros / 1_000_000) % 60
        val totalMinutes = (micros / 60_000_000) % 60
        val millis = (micros / 1000) % 1000
        val microsPart = micros % 1000
        return String.format(Locale.US, "%02d:%02d.%03d%03d", totalMinutes, totalSeconds, millis, microsPart)
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 1L  // ~1000fps，尽量快
    }
}
