package com.huanshi.timesync

import android.graphics.Color
import android.os.Bundle
import android.view.Choreographer
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.huanshi.timesync.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val choreographer = Choreographer.getInstance()

    // nanoTime 和 currentTimeMillis 在同一时刻采样，建立映射关系
    private var baseNanoTime: Long = 0
    private var baseEpochMillis: Long = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // frameTimeNanos 就是这一帧的 VSync 时刻，直接用它计算时间
            binding.timerText.text = formatTimeFromNano(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 校准：在同一时刻采样两个时钟
        baseEpochMillis = System.currentTimeMillis()
        baseNanoTime = System.nanoTime()
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        choreographer.removeFrameCallback(frameCallback)
        super.onPause()
    }

    /**
     * 用 Choreographer 回调的 frameTimeNanos 直接推算挂钟时间
     * frameTimeNanos 是这一帧的 VSync 时刻（nanoTime 基准），精度到纳秒
     * 比用 System.nanoTime() 重新采样更准确，因为它就是屏幕实际显示这一帧的时刻
     */
    private fun formatTimeFromNano(frameTimeNanos: Long): String {
        val elapsedNanos = frameTimeNanos - baseNanoTime
        val micros = (baseEpochMillis * 1000) + (elapsedNanos / 1000)
        val totalSeconds = (micros / 1_000_000) % 60
        val totalMinutes = (micros / 60_000_000) % 60
        val millis = (micros / 1000) % 1000
        val microsPart = micros % 1000
        return String.format(Locale.US, "%02d:%02d.%03d%03d", totalMinutes, totalSeconds, millis, microsPart)
    }
}
