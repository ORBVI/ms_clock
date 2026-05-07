package com.huanshi.timesync

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.huanshi.timesync.databinding.ActivityMainBinding
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainExecutor: Executor

    private val handler = Handler(Looper.getMainLooper())
    private val timeZone = TimeZone.getDefault()
    private var activeMode = Mode.TIMER
    private var cameraProvider: ProcessCameraProvider? = null

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateTimestamps()
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCurrentCameraMode()
            } else {
                showPermissionStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainExecutor = ContextCompat.getMainExecutor(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupBottomNavigation()
        showMode(Mode.TIMER)
    }

    override fun onResume() {
        super.onResume()
        startClock()
        if (activeMode != Mode.TIMER && hasCameraPermission()) {
            startCurrentCameraMode()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(clockRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentContainer.setPadding(0, bars.top, 0, 0)
            binding.bottomNavigation.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.menu.apply {
            add(Menu.NONE, NAV_TIMER, 0, R.string.tab_timer).setIcon(R.drawable.ic_timer)
            add(Menu.NONE, NAV_CAMERA, 1, R.string.tab_camera).setIcon(R.drawable.ic_camera)
            add(Menu.NONE, NAV_SYNC, 2, R.string.tab_sync).setIcon(R.drawable.ic_sync)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                NAV_TIMER -> showMode(Mode.TIMER)
                NAV_CAMERA -> showMode(Mode.CAMERA)
                NAV_SYNC -> showMode(Mode.SYNC)
            }
            true
        }

        binding.bottomNavigation.selectedItemId = NAV_TIMER
    }

    private fun showMode(mode: Mode) {
        activeMode = mode
        binding.timerText.isVisible = mode == Mode.TIMER
        binding.cameraPane.isVisible = mode == Mode.CAMERA
        binding.syncPane.isVisible = mode == Mode.SYNC

        when (mode) {
            Mode.TIMER -> cameraProvider?.unbindAll()
            Mode.CAMERA,
            Mode.SYNC -> ensureCameraPermission()
        }
    }

    private fun ensureCameraPermission() {
        if (hasCameraPermission()) {
            startCurrentCameraMode()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCurrentCameraMode() {
        when (activeMode) {
            Mode.CAMERA -> startCameraPreview()
            Mode.SYNC -> startSyncPreview()
            Mode.TIMER -> Unit
        }
    }

    private fun withCameraProvider(action: (ProcessCameraProvider) -> Unit) {
        val provider = cameraProvider
        if (provider != null) {
            action(provider)
            return
        }

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    val resolvedProvider = future.get()
                    cameraProvider = resolvedProvider
                    action(resolvedProvider)
                } catch (error: Exception) {
                    showCameraError(getString(R.string.camera_start_failed, error.shortMessage()))
                }
            },
            mainExecutor
        )
    }

    private fun startCameraPreview() {
        withCameraProvider { provider ->
            provider.unbindAll()
            binding.cameraStatus.isVisible = false

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.cameraPreview.surfaceProvider) }

            try {
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (error: Exception) {
                showCameraError(getString(R.string.camera_start_failed, error.shortMessage()))
            }
        }
    }

    private fun startSyncPreview() {
        withCameraProvider { provider ->
            provider.unbindAll()
            binding.syncStatus.isVisible = false
            binding.backStatus.isVisible = false
            binding.frontStatus.isVisible = false

            val backPreview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.backPreview.surfaceProvider) }
            val frontPreview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.frontPreview.surfaceProvider) }

            val selectors = provider.concurrentFrontBackSelectors()
            if (selectors == null) {
                showSyncFallback(
                    provider,
                    IllegalStateException(getString(R.string.concurrent_camera_unsupported))
                )
                return@withCameraProvider
            }

            val backConfig = SingleCameraConfig(
                selectors.back,
                UseCaseGroup.Builder().addUseCase(backPreview).build(),
                this
            )
            val frontConfig = SingleCameraConfig(
                selectors.front,
                UseCaseGroup.Builder().addUseCase(frontPreview).build(),
                this
            )

            try {
                if (supportsConcurrentCamera()) {
                    provider.bindToLifecycle(listOf(backConfig, frontConfig))
                } else {
                    throw IllegalStateException(getString(R.string.concurrent_camera_unsupported))
                }
            } catch (error: Exception) {
                showSyncFallback(provider, error)
            }
        }
    }

    private fun showSyncFallback(provider: ProcessCameraProvider, error: Exception) {
        provider.unbindAll()
        binding.syncStatus.text = getString(R.string.sync_fallback, error.shortMessage())
        binding.syncStatus.isVisible = true
        binding.frontStatus.text = getString(R.string.front_camera_unavailable)
        binding.frontStatus.isVisible = true

        val backOnlyPreview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(binding.backPreview.surfaceProvider) }

        try {
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, backOnlyPreview)
        } catch (bindError: Exception) {
            binding.backStatus.text = getString(R.string.camera_start_failed, bindError.shortMessage())
            binding.backStatus.isVisible = true
        }
    }

    private fun supportsConcurrentCamera(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)

    private fun ProcessCameraProvider.concurrentFrontBackSelectors(): CameraSelectorPair? {
        if (!supportsConcurrentCamera()) return null

        for (cameraInfoList in availableConcurrentCameraInfos) {
            var backSelector: CameraSelector? = null
            var frontSelector: CameraSelector? = null

            for (cameraInfo in cameraInfoList) {
                when (cameraInfo.lensFacing) {
                    CameraSelector.LENS_FACING_BACK -> backSelector = cameraInfo.getCameraSelector()
                    CameraSelector.LENS_FACING_FRONT -> frontSelector = cameraInfo.getCameraSelector()
                }
            }

            if (backSelector != null && frontSelector != null) {
                return CameraSelectorPair(backSelector, frontSelector)
            }
        }

        return null
    }

    private fun showPermissionStatus() {
        val message = getString(R.string.camera_permission_required)
        binding.cameraStatus.text = message
        binding.cameraStatus.isVisible = activeMode == Mode.CAMERA
        binding.syncStatus.text = message
        binding.syncStatus.isVisible = activeMode == Mode.SYNC
    }

    private fun showCameraError(message: String) {
        binding.cameraStatus.text = message
        binding.cameraStatus.isVisible = activeMode == Mode.CAMERA
        binding.syncStatus.text = message
        binding.syncStatus.isVisible = activeMode == Mode.SYNC
    }

    private fun startClock() {
        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)
    }

    private fun updateTimestamps() {
        val timestamp = formatTimestamp(System.currentTimeMillis())
        binding.timerText.text = timestamp
        binding.cameraTimestamp.text = timestamp
        binding.backTimestamp.text = timestamp
        binding.frontTimestamp.text = timestamp
    }

    private fun formatTimestamp(nowMillis: Long): String {
        val localMillis = Math.floorMod(nowMillis + timeZone.getOffset(nowMillis), MILLIS_PER_DAY)
        val hours = localMillis / MILLIS_PER_HOUR
        val minutes = localMillis % MILLIS_PER_HOUR / MILLIS_PER_MINUTE
        val seconds = localMillis % MILLIS_PER_MINUTE / MILLIS_PER_SECOND
        val millis = localMillis % MILLIS_PER_SECOND
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    private fun Exception.shortMessage(): String =
        localizedMessage ?: javaClass.simpleName

    private enum class Mode {
        TIMER,
        CAMERA,
        SYNC
    }

    private data class CameraSelectorPair(
        val back: CameraSelector,
        val front: CameraSelector
    )

    private companion object {
        private const val NAV_TIMER = 1
        private const val NAV_CAMERA = 2
        private const val NAV_SYNC = 3
        private const val FRAME_INTERVAL_MS = 16L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
        private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
        private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
    }
}
