package com.brawlbrain.rt

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenBrainService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        @Volatile var running = false
    }

    private lateinit var projection: MediaProjection
    private lateinit var reader: ImageReader
    private var display: VirtualDisplay? = null

    private lateinit var windowManager: WindowManager
    private var overlay: OverlayView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var worker: HandlerThread
    private lateinit var workerHandler: Handler

    private val entityLabels = arrayOf("enemy", "teammate", "player")
    private val wallLabels = arrayOf("wall", "bush", "close_bush")

    private lateinit var entityDetector: YoloOnnxDetector
    private lateinit var wallDetector: YoloOnnxDetector
    private val brain = TacticalBrain()

    private val busy = AtomicBoolean(false)
    private var lastEntityAt = 0L
    private var lastWallAt = 0L
    private var lastInferenceAt = 0L

    private var latestEntities = emptyList<Detection>()
    private var latestWalls = emptyList<Detection>()
    private var latestFps = 0f
    private var latestInferenceMs = 0L

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        worker = HandlerThread(
            "BrawlBrainVision",
            Process.THREAD_PRIORITY_MORE_FAVORABLE
        )
        worker.start()
        workerHandler = Handler(worker.looper)

        startBrainForeground()
        running = true
        attachOverlay()

        try {
            entityDetector = YoloOnnxDetector(
                assets.open("models/PylaEntityDetectorV2.onnx").use { it.readBytes() },
                entityLabels,
                confidenceThreshold = 0.34f
            )
            wallDetector = YoloOnnxDetector(
                assets.open("models/PylaWallDetectorV2.onnx").use { it.readBytes() },
                wallLabels,
                confidenceThreshold = 0.30f
            )
        } catch (_: Throwable) {
            mainHandler.post {
                overlay?.contentDescription = "Не удалось загрузить YOLO"
            }
            stopSelf()
        }
    }

    private fun startBrainForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "brawlbrain_rt"

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "BrawlBrain RT",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("BrawlBrain RT")
            .setContentText("Vision работает локально на телефоне")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                7,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(7, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::projection.isInitialized && intent != null) {
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)

            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

            if (code != Activity.RESULT_OK || data == null) {
                stopSelf()
                return START_NOT_STICKY
            }

            startProjection(code, data)
        }

        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startProjection(code: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)

        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            },
            mainHandler
        )

        val dm = resources.displayMetrics
        val fullW = dm.widthPixels
        val fullH = dm.heightPixels

        reader = ImageReader.newInstance(
            fullW,
            fullH,
            PixelFormat.RGBA_8888,
            2
        )

        reader.setOnImageAvailableListener(
            { ir -> consumeLatest(ir, fullW, fullH) },
            workerHandler
        )

        display = projection.createVirtualDisplay(
            "BrawlBrainRT",
            fullW,
            fullH,
            dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            workerHandler
        )
    }

    private fun consumeLatest(ir: ImageReader, fullW: Int, fullH: Int) {
        val now = SystemClock.elapsedRealtime()

        if (!busy.compareAndSet(false, true)) {
            ir.acquireLatestImage()?.close()
            return
        }

        if (now - lastEntityAt < 125L) {
            busy.set(false)
            ir.acquireLatestImage()?.close()
            return
        }

        lastEntityAt = now

        val image = ir.acquireLatestImage()
        if (image == null) {
            busy.set(false)
            return
        }

        try {
            image.use {
                val plane = it.planes[0]
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * fullW
                val rawWidth = fullW + rowPadding / pixelStride.coerceAtLeast(1)

                val raw = Bitmap.createBitmap(
                    rawWidth,
                    fullH,
                    Bitmap.Config.ARGB_8888
                )
                raw.copyPixelsFromBuffer(buffer)

                val targetWidth = minOf(1280, rawWidth)
                val targetHeight = (fullH * targetWidth.toFloat() / rawWidth)
                    .toInt()
                    .coerceAtLeast(1)

                val frame = Bitmap.createScaledBitmap(
                    raw,
                    targetWidth,
                    targetHeight,
                    true
                )
                raw.recycle()

                val t0 = SystemClock.elapsedRealtime()

                latestEntities = entityDetector.detect(frame)

                val wallNow = SystemClock.elapsedRealtime()
                if (wallNow - lastWallAt >= 600L) {
                    latestWalls = wallDetector.detect(frame)
                    lastWallAt = wallNow
                }

                val t1 = SystemClock.elapsedRealtime()
                latestInferenceMs = t1 - t0

                latestFps =
                    if (lastInferenceAt == 0L) 0f
                    else 1000f / (t1 - lastInferenceAt).coerceAtLeast(1L)

                lastInferenceAt = t1

                val snapshot = brain.decide(
                    latestEntities,
                    latestWalls,
                    latestFps,
                    latestInferenceMs,
                    "YOLOv11"
                )

                mainHandler.post {
                    overlay?.submit(snapshot)
                }

                frame.recycle()
            }
        } catch (_: Throwable) {
            // Ignore one malformed frame; keep the real-time loop alive.
        } finally {
            busy.set(false)
        }
    }

    private fun attachOverlay() {
        if (!Settings.canDrawOverlays(this)) return

        val view = OverlayView(this)
        overlay = view

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(view, lp)
    }

    override fun onDestroy() {
        running = false

        try { overlay?.let { windowManager.removeView(it) } } catch (_: Throwable) {}
        overlay = null

        try { display?.release() } catch (_: Throwable) {}
        try { if (::reader.isInitialized) reader.close() } catch (_: Throwable) {}
        try { if (::projection.isInitialized) projection.stop() } catch (_: Throwable) {}

        try { if (::entityDetector.isInitialized) entityDetector.close() } catch (_: Throwable) {}
        try { if (::wallDetector.isInitialized) wallDetector.close() } catch (_: Throwable) {}

        try { worker.quitSafely() } catch (_: Throwable) {}

        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}