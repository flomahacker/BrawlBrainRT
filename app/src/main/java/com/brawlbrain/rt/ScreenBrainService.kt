package com.brawlbrain.rt

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import java.nio.ByteBuffer
import java.util.Locale
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

    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private lateinit var worker: HandlerThread
    private lateinit var workerHandler: Handler

    private val entityLabels = arrayOf("enemy", "teammate", "player")
    private val wallLabels = arrayOf("wall", "bush", "close_bush")

    private lateinit var entityDetector: YoloOnnxDetector
    private lateinit var wallDetector: YoloOnnxDetector

    private val brain = TacticalBrain()
    private val dodgeBrain = DodgeBrain()
    private val combatIntel = CombatIntel()
    private val enemyTracker = DetectionTracker()
    private val hudEstimator = HudStateEstimator()
    private val safeZoneEstimator = SafeZoneEstimator()
    private val alertEngine = AlertEngine()

    private lateinit var powerManager: PowerManager
    private lateinit var vibrator: Vibrator

    private val busy = AtomicBoolean(false)
    private var lastEntityAt = 0L
    private var lastWallAt = 0L
    private var lastInferenceAt = 0L
    private var lastRenderAt = 0L
    private var lastWarning = WarningType.NONE

    @Volatile
    private var config = BrainConfig()

    private var latestWalls = emptyList<Detection>()
    private var latestFps = 0f
    private var latestInferenceMs = 0L
    private var latestHud = HudState()
    private var latestZone = SafeZoneState()
    @Volatile private var latestSnapshot: BrainFrame? = null

    override fun onCreate() {
        super.onCreate()

        config = BrainPrefs.load(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        worker = HandlerThread(
            "BrawlBrainVision",
            android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE
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
                overlay?.contentDescription = "Vision model failed to load"
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
            .setContentText("Local real-time vision")
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

    private fun effectiveEntityInterval(): Long {
        var interval = config.entityIntervalMs.coerceIn(60L, 100L)

        if (Build.VERSION.SDK_INT >= 29) {
            interval += when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_LIGHT -> 10L
                PowerManager.THERMAL_STATUS_MODERATE -> 25L
                PowerManager.THERMAL_STATUS_SEVERE -> 50L
                PowerManager.THERMAL_STATUS_CRITICAL -> 90L
                PowerManager.THERMAL_STATUS_EMERGENCY -> 140L
                PowerManager.THERMAL_STATUS_SHUTDOWN -> 220L
                else -> 0L
            }
        }

        return interval
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
            image.use { currentImage ->
                val thermalInterval = effectiveEntityInterval()
                val runYolo = now - lastEntityAt >= thermalInterval
                val safeZoneEnabled = config.gameMode == "Showdown"

                latestZone = safeZoneEstimator.analyze(
                    currentImage,
                    captureW,
                    captureH,
                    config,
                    safeZoneEnabled
                )

                val predicted = enemyTracker.predictOnly(
                    now,
                    effectiveLeadMs
                )

                var active = latestSnapshot

                if (runYolo) {
                    lastEntityAt = now

                    val plane = currentImage.planes[0]
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

                    val frameBitmap =
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

                        val detected = entityDetector.detect(frameBitmap)
                        val tracked = enemyTracker.update(
                            detected.filter { it.label == "enemy" },
                            t0,
                            effectiveLeadMs
                        )

                        val entities = ArrayList<Detection>(detected.size)
                        for (d in detected) {
                            if (d.label != "enemy") entities += d
                        }
                        entities.addAll(tracked.visible)

                        if (config.showWalls &&
                            ::wallDetector.isInitialized &&
                            t0 - lastWallAt >= config.wallIntervalMs
                        ) {
                            latestWalls = wallDetector.detect(frameBitmap)
                            lastWallAt = t0
                        } else if (!config.showWalls) {
                            latestWalls = emptyList()
                        }

                        val t1 = SystemClock.elapsedRealtime()
                        latestInferenceMs = t1 - t0

                        latestFps =
                            if (lastInferenceAt == 0L) 0f
                            else 1000f / (t1 - lastInferenceAt).coerceAtLeast(1L)
                        lastInferenceAt = t1

                        active = brain.decide(
                            entities,
                            latestWalls,
                            latestFps,
                            latestInferenceMs,
                            entityDetector.backend,
                            config
                        )

                        latestHud = hudEstimator.analyze(
                            currentImage,
                            captureW,
                            captureH,
                            active.player,
                            config
                        )

                        val intel = combatIntel.decide(
                            active.player,
                            active.enemies,
                            active.teammates,
                            null,
                            config.brawler,
                            t1
                        )

                        active = active.copy(
                            intelActionTitle = intel.actionTitle,
                            intelActionDetail = intel.actionDetail,
                            intelFocusX = intel.focusX,
                            intelFocusY = intel.focusY,
                            intelFocusScore = intel.focusScore,
                            fireWindow = intel.fireWindow,
                            actionX = intel.actionX,
                            actionY = intel.actionY,
                            trackVisuals = tracked.visuals,
                            trackCount = tracked.trackCount,
                            hud = latestHud,
                            gasDetected = latestZone.detected,
                            safeZoneX = latestZone.x,
                            safeZoneY = latestZone.y
                        )
                    } finally {
                        frameBitmap.recycle()
                    }
                } else if (active != null) {
                    active = active.copy(
                        trackVisuals = predicted.visuals,
                        trackCount = predicted.trackCount,
                        hud = latestHud,
                        gasDetected = latestZone.detected,
                        safeZoneX = latestZone.x,
                        safeZoneY = latestZone.y
                    )
                }

                if (active != null) {
                    val visibleTracks = active.enemies

                    val warning = alertEngine.update(
                        active.player,
                        visibleTracks,
                        latestHud,
                        latestZone,
                        config,
                        now,
                        config.showWarnings,
                        safeZoneEnabled
                    )

                    if (warning.type != lastWarning) {
                        if (warning.type != WarningType.NONE) vibrateBriefly()
                        lastWarning = warning.type
                    }

                    val captureDelayMs = (
                        (System.nanoTime() - currentImage.timestamp) / 1_000_000L
                    ).coerceAtLeast(0L)

                    val debug = String.format(
                        Locale.US,
                        "delay %dms • infer %dms • fps %.1f • tracks %d • %s",
                        captureDelayMs,
                        latestInferenceMs,
                        latestFps,
                        predicted.trackCount,
                        if (::entityDetector.isInitialized) entityDetector.backend else "INIT"
                    )

                    active = active.copy(
                        warning = warning,
                        trackVisuals = if (runYolo) active.trackVisuals else predicted.visuals,
                        trackCount = predicted.trackCount,
                        projectileDetected = false,
                        projectileThreat = 0f,
                        projectileEtaMs = 0,
                        debugText = debug
                    )

                    latestSnapshot = active

                    if (runYolo || now - lastRenderAt >= 120L) {
                        lastRenderAt = now
                        val displayFrame = active
                        mainHandler.post {
                            overlay?.submit(displayFrame)
                        }
                    }

                    if (config.autoDodge && runYolo) {
                        val dodge = dodgeBrain.decide(
                            active.player,
                            visibleTracks,
                            now,
                            config,
                            null
                        )

                        if (dodge.shouldDodge) {
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
                }
            }
        } catch (_: Throwable) {
            // A malformed frame must never kill the real-time service.
        } finally {
            busy.set(false)
        }
    }

    private fun vibrateBriefly() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(42L, 80))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(42L)
            }
        } catch (_: Throwable) {
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

        try { enemyTracker.reset() } catch (_: Throwable) {}
        try { worker.quitSafely() } catch (_: Throwable) {}

        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
