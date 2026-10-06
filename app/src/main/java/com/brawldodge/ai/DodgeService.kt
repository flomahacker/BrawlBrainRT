package com.brawldodge.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class DodgeService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "screen_capture_data"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var latestSummary = "ожидание"
            private set
    }

    private lateinit var worker: HandlerThread
    private lateinit var workerHandler: Handler
    private lateinit var detector: VisionDetector
    private lateinit var powerManager: PowerManager
    private lateinit var windowManager: WindowManager

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var overlay: DodgeOverlayView? = null

    private val processing = AtomicBoolean(false)
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    private var captureW = 720
    private var captureH = 324
    private var lastProcessAt = 0L

    private var fpsWindowStart = 0L
    private var fpsFrames = 0
    private var fps = 0f

    @Volatile
    private var intervalMs = 80L

    override fun onCreate() {
        super.onCreate()

        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        detector = VisionDetector()

        worker = HandlerThread(
            "BrawlDodgeVision",
            android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE
        )
        worker.start()
        workerHandler = Handler(worker.looper)

        createNotificationChannel()
        registerThermalListener()

        if (!Settings.canDrawOverlays(this)) {
            latestSummary = "нет разрешения overlay"
            stopSelf()
            return
        }

        try {
            attachOverlay()
            isRunning = true
            submit(VisionSnapshot(online = false, recommendation = "ЗАХВАТ"))
        } catch (t: Throwable) {
            latestSummary = "overlay: " + t.javaClass.simpleName
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode != -1 && data != null && projection == null) {
            try {
                startAsForeground()
                startProjection(resultCode, data)
            } catch (t: Throwable) {
                latestSummary = "ошибка: " + t.javaClass.simpleName
                submit(
                    VisionSnapshot(
                        online = false,
                        recommendation = "ОШИБКА",
                        error = t.javaClass.simpleName + ": " + (t.message ?: "")
                    )
                )
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val active = manager.getMediaProjection(resultCode, data)
            ?: error("MediaProjection unavailable")
        projection = active

        val metrics = resources.displayMetrics
        val screenLong = max(metrics.widthPixels, metrics.heightPixels)
        val screenShort = min(metrics.widthPixels, metrics.heightPixels)

        val scale = min(1f, 720f / screenLong.toFloat())
        captureW = max(2, ((screenLong * scale).toInt() and -2))
        captureH = max(2, ((screenShort * scale).toInt() and -2))

        active.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            },
            workerHandler
        )

        reader = ImageReader.newInstance(
            captureW,
            captureH,
            PixelFormat.RGBA_8888,
            2
        )

        reader?.setOnImageAvailableListener(
            { processLatest(it) },
            workerHandler
        )

        virtualDisplay = active.createVirtualDisplay(
            "BrawlDodgeAI",
            captureW,
            captureH,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            workerHandler
        ) ?: error("VirtualDisplay unavailable")

        latestSummary = "захват " + captureW + "x" + captureH
        submit(
            VisionSnapshot(
                online = true,
                recommendation = "СКАНИРОВАНИЕ"
            )
        )
    }

    private fun processLatest(reader: ImageReader) {
        if (!isRunning || !processing.compareAndSet(false, true)) {
            reader.acquireLatestImage()?.close()
            return
        }

        val image = reader.acquireLatestImage()
        if (image == null) {
            processing.set(false)
            return
        }

        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastProcessAt < intervalMs) return
            lastProcessAt = now

            val start = System.nanoTime()
            val boxes = detector.analyze(image, captureW, captureH)
            val processMs = (System.nanoTime() - start) / 1_000_000f

            fpsFrames++
            if (fpsWindowStart == 0L) fpsWindowStart = now
            val elapsed = now - fpsWindowStart
            if (elapsed >= 1000L) {
                fps = fpsFrames * 1000f / elapsed
                fpsFrames = 0
                fpsWindowStart = now
            }

            val player = boxes
                .filter { it.kind == Box.Kind.PLAYER }
                .maxByOrNull { it.confidence }

            val threats = boxes.filter {
                it.kind == Box.Kind.ENEMY || it.kind == Box.Kind.PROJECTILE
            }

            val danger = threatScore(player, threats)

            val captureDelay = if (image.timestamp > 0L) {
                ((System.nanoTime() - image.timestamp).coerceAtLeast(0L) / 1_000_000f)
            } else {
                0f
            }

            val recommendation = when {
                danger.first >= 0.72f -> "ОТХОД"
                danger.first >= 0.45f -> "ОСТОРОЖНО"
                else -> "СПОКОЙНО"
            }

            latestSummary =
                "CV " + String.format(java.util.Locale.US, "%.1f", processMs) +
                    "ms · " + boxes.size + " объектов"

            submit(
                VisionSnapshot(
                    online = true,
                    fps = fps,
                    processMs = processMs,
                    captureDelayMs = captureDelay,
                    boxes = boxes,
                    danger = danger.first,
                    dangerX = danger.second,
                    dangerY = danger.third,
                    recommendation = recommendation
                )
            )
        } catch (t: Throwable) {
            latestSummary = "CV ошибка: " + t.javaClass.simpleName
            submit(
                VisionSnapshot(
                    online = projection != null,
                    recommendation = "CV ОШИБКА",
                    error = t.javaClass.simpleName
                )
            )
        } finally {
            image.close()
            processing.set(false)
        }
    }

    private fun threatScore(
        player: Box?,
        threats: List<Box>
    ): Triple<Float, Float, Float> {
        if (player == null || threats.isEmpty()) {
            return Triple(0f, 0f, 0f)
        }

        var best = 0f
        var bestDx = 0f
        var bestDy = 0f

        for (threat in threats) {
            val dx = threat.cx - player.cx
            val dy = threat.cy - player.cy
            val distance = max(0.01f, hypot(dx.toDouble(), dy.toDouble()).toFloat())

            val proximity = (1f - distance / 0.55f).coerceIn(0f, 1f)
            val base = when (threat.kind) {
                Box.Kind.PROJECTILE -> 0.92f
                Box.Kind.ENEMY -> 0.55f
                else -> 0f
            }

            val score = (base * 0.62f + proximity * 0.38f) * threat.confidence
            if (score > best) {
                best = score
                bestDx = dx
                bestDy = dy
            }
        }

        return Triple(best.coerceIn(0f, 1f), bestDx, bestDy)
    }

    private fun submit(snapshot: VisionSnapshot) {
        mainHandler.post {
            overlay?.submit(snapshot)
        }
    }

    private fun attachOverlay() {
        val view = DodgeOverlayView(this)
        overlay = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Brawl Dodge AI"
        }

        windowManager.addView(view, params)
    }

    private fun startAsForeground() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Brawl Dodge AI")
            .setContentText("Vision assistant is running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Brawl Dodge AI",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < 29) return

        powerManager.addThermalStatusListener(mainExecutor) { status ->
            intervalMs = when (status) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT -> 80L

                PowerManager.THERMAL_STATUS_MODERATE -> 110L
                PowerManager.THERMAL_STATUS_SEVERE -> 150L
                PowerManager.THERMAL_STATUS_CRITICAL -> 220L
                PowerManager.THERMAL_STATUS_EMERGENCY -> 320L
                PowerManager.THERMAL_STATUS_SHUTDOWN -> 500L
                else -> 140L
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        latestSummary = "остановлено"

        try {
            reader?.setOnImageAvailableListener(null, null)
            reader?.close()
        } catch (_: Throwable) {
        }
        reader = null

        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        virtualDisplay = null

        try {
            projection?.stop()
        } catch (_: Throwable) {
        }
        projection = null

        try {
            overlay?.let { windowManager.removeViewImmediate(it) }
        } catch (_: Throwable) {
        }
        overlay = null

        try {
            worker.quitSafely()
        } catch (_: Throwable) {
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "brawl_dodge_ai"
        private const val NOTIFICATION_ID = 701
    }
}
