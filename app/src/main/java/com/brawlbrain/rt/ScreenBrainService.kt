package com.brawlbrain.rt

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
    private var inGameControl: InGameControl? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var worker: HandlerThread
    private lateinit var workerHandler: Handler

    private val entityLabels = arrayOf("enemy", "teammate", "player")
    private val wallLabels = arrayOf("wall", "bush", "close_bush")

    private lateinit var entityDetector: YoloOnnxDetector
    private lateinit var wallDetector: YoloOnnxDetector
    private val brain = TacticalBrain()
    private val dodgeBrain = DodgeBrain()
    private val projectileAnalyzer = ProjectileThreatAnalyzer()

    private val busy = AtomicBoolean(false)
    private var lastEntityAt = 0L
    private var lastWallAt = 0L
    private var lastInferenceAt = 0L

    @Volatile
    private var config = BrainConfig()
    private var latestEntities = emptyList<Detection>()
    private var latestWalls = emptyList<Detection>()
    private var latestFps = 0f
    private var latestInferenceMs = 0L
    @Volatile private var latestSnapshot: BrainFrame? = null

    override fun onCreate() {
        super.onCreate()

        config = BrainPrefs.load(this)
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
        inGameControl = InGameControl(this, windowManager) { updated ->
            config = updated
            overlay?.updateConfig(updated)
        }
        if (Settings.canDrawOverlays(this)) {
            inGameControl?.show()
        }

        try {
            entityDetector = YoloOnnxDetector(
                assets.open("models/PylaEntityDetectorV2.onnx").use { it.readBytes() },
                entityLabels,
                confidenceThreshold = config.confidencePercent / 100f
            )

            if (config.showWalls) {
                wallDetector = YoloOnnxDetector(
                    assets.open("models/PylaWallDetectorV2.onnx").use { it.readBytes() },
                    wallLabels,
                    confidenceThreshold = (config.confidencePercent - 4).coerceAtLeast(16) / 100f
                )
            }
        } catch (_: Throwable) {
            mainHandler.post {
                overlay?.contentDescription = "YOLO не загрузился"
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
            .setContentText("AI vision работает локально")
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
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val longEdge = minOf(config.frameLongEdge, maxOf(screenW, screenH))
        val scale = longEdge.toFloat() / maxOf(screenW, screenH).toFloat()
        val captureW = maxOf(2, (screenW * scale).toInt() and -2)
        val captureH = maxOf(2, (screenH * scale).toInt() and -2)

        reader = ImageReader.newInstance(
            captureW,
            captureH,
            PixelFormat.RGBA_8888,
            3
        )

        reader.setOnImageAvailableListener(
            { ir -> consumeLatest(ir, captureW, captureH) },
            workerHandler
        )

        display = projection.createVirtualDisplay(
            "BrawlBrainRT",
            captureW,
            captureH,
            dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            workerHandler
        )
    }

    private fun consumeLatest(ir: ImageReader, captureW: Int, captureH: Int) {
        val now = SystemClock.elapsedRealtime()

        if (!busy.compareAndSet(false, true)) {
            ir.acquireLatestImage()?.close()
            return
        }

        val image = ir.acquireLatestImage()
        if (image == null) {
            busy.set(false)
            return
        }

        try {
            image.use {
                val dodgeEnabled = config.autoDodge
                val cached = latestSnapshot

                // This layer runs on every available screen frame when Dodge is ON.
                // It uses a tiny grayscale grid instead of another neural network.
                val projectileThreat = if (dodgeEnabled) {
                    projectileAnalyzer.analyze(
                        it,
                        captureW,
                        captureH,
                        cached?.player,
                        cached?.enemies ?: latestEntities.filter { d -> d.label == "enemy" }
                            .take(3),
                        now
                    )
                } else {
                    projectileAnalyzer.reset()
                    null
                }

                val dodgeVisionInterval = if (dodgeEnabled) 68L else config.entityIntervalMs
                val runYolo = now - lastEntityAt >= dodgeVisionInterval

                var snapshot = latestSnapshot

                if (runYolo) {
                    lastEntityAt = now

                    val plane = it.planes[0]
                    val buffer: ByteBuffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * captureW
                    val rawWidth = captureW + rowPadding / pixelStride.coerceAtLeast(1)

                    val raw = Bitmap.createBitmap(
                        rawWidth,
                        captureH,
                        Bitmap.Config.ARGB_8888
                    )
                    raw.copyPixelsFromBuffer(buffer)

                    val frame =
                        if (rawWidth == captureW) {
                            raw
                        } else {
                            val cropped = Bitmap.createBitmap(
                                captureW,
                                captureH,
                                Bitmap.Config.ARGB_8888
                            )
                            Canvas(cropped).drawBitmap(
                                raw,
                                0f,
                                0f,
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            raw.recycle()
                            cropped
                        }

                    try {
                        val t0 = SystemClock.elapsedRealtime()

                        latestEntities = entityDetector.detect(frame)

                        val wallNow = SystemClock.elapsedRealtime()
                        if (config.showWalls &&
                            ::wallDetector.isInitialized &&
                            wallNow - lastWallAt >= config.wallIntervalMs
                        ) {
                            latestWalls = wallDetector.detect(frame)
                            lastWallAt = wallNow
                        } else if (!config.showWalls) {
                            latestWalls = emptyList()
                        }

                        val t1 = SystemClock.elapsedRealtime()
                        latestInferenceMs = t1 - t0

                        latestFps =
                            if (lastInferenceAt == 0L) 0f
                            else 1000f / (t1 - lastInferenceAt).coerceAtLeast(1L)
                        lastInferenceAt = t1

                        snapshot = brain.decide(
                            latestEntities,
                            latestWalls,
                            latestFps,
                            latestInferenceMs,
                            "YOLOv11",
                            config
                        )
                        latestSnapshot = snapshot
                    } finally {
                        frame.recycle()
                    }
                }

                val active = snapshot
                if (active != null) {
                    // The normal predictive dodge updates on fresh YOLO detections.
                    // The emergency projectile path can interrupt on any screen frame.
                    if (runYolo || projectileThreat?.urgent == true) {
                        val dodge = dodgeBrain.decide(
                            active.player,
                            active.enemies,
                            now,
                            config,
                            projectileThreat
                        )

                        if (dodge.shouldDodge && config.autoDodge) {
                            val controller = DodgeAccessibilityService.instance
                            if (controller != null) {
                                mainHandler.postAtFrontOfQueue {
                                    if (config.autoDodge) {
                                        controller.dodge(
                                            dodge.x,
                                            dodge.y,
                                            config.dodgeStrengthPercent / 100f
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // HUD updates stay on YOLO cadence to avoid visual jitter.
                    // An urgent projectile event is pushed immediately.
                    if (runYolo || projectileThreat?.urgent == true) {
                        val displayFrame = active.copy(
                            projectileThreat = projectileThreat?.score ?: 0f,
                            projectileEtaMs = projectileThreat?.etaMs ?: 0,
                            projectileDetected = projectileThreat?.detected == true
                        )
                        mainHandler.post {
                            overlay?.submit(displayFrame)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // One malformed capture must never kill the real-time service.
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

        try { inGameControl?.hide() } catch (_: Throwable) {}
        inGameControl = null
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