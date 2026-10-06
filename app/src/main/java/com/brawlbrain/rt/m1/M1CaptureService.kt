package com.brawlbrain.rt.m1

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
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

class M1CaptureService : Service() {

    private val running = AtomicBoolean(false)
    private val overlayState = M1OverlayState()

    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projection: MediaProjection? = null
    private var overlay: M1OverlayView? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private val output: ByteBuffer = ByteBuffer
        .allocateDirect(NativeDetector.OUTPUT_BYTES)
        .order(ByteOrder.nativeOrder())

    private var sourceWidth = 640
    private var sourceHeight = 288
    private var screenWidth = 2400
    private var screenHeight = 1080

    @Volatile
    private var thermalStatus = 0

    @Volatile
    private var processIntervalNs = 16_666_667L

    private var lastProcessNs = 0L
    private var fpsWindowStartNs = 0L
    private var fpsFrames = 0
    private var fpsValue = 0f

    override fun onCreate() {
        super.onCreate()

        worker = HandlerThread("brawl-m1-capture", android.os.Process.THREAD_PRIORITY_DEFAULT)
        worker.start()
        handler = Handler(worker.looper)

        createNotificationChannel()
        registerThermalListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData = intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent

        if (resultCode != -1 && resultData != null && running.compareAndSet(false, true)) {
            try {
                startAsForeground()
                attachOverlay()
                startCapture(resultCode, resultData)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start M1", t)
                running.set(false)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Brawl Dodge AI M1")
            .setContentText("Screen capture + debug CV active. Touch injection is OFF.")
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

    private fun attachOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            throw SecurityException("Overlay permission is not granted")
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        screenWidth = max(metrics.widthPixels, metrics.heightPixels)
        screenHeight = minOf(metrics.widthPixels, metrics.heightPixels)

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCHABLE or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Brawl Dodge AI M1 Debug"
        }

        overlay = M1OverlayView(this, overlayState)
        wm.addView(overlay, params)
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        screenWidth = max(metrics.widthPixels, metrics.heightPixels)
        screenHeight = minOf(metrics.widthPixels, metrics.heightPixels)

        sourceWidth = 640
        sourceHeight = (sourceWidth.toFloat() * screenHeight / screenWidth)
            .roundToInt()
            .coerceAtLeast(240)

        overlayState.update(
            output,
            0,
            0f,
            0f,
            0f,
            thermalStatus,
            sourceWidth,
            sourceHeight
        )

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        val activeProjection = projection ?: error("MediaProjection returned null")

        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped")
                stopSelf()
            }
        }
        activeProjection.registerCallback(projectionCallback, handler)

        imageReader = ImageReader.newInstance(
            sourceWidth,
            sourceHeight,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            processLatest(reader)
        }, handler)

        virtualDisplay = activeProjection.createVirtualDisplay(
            "BrawlBrainM1",
            sourceWidth,
            sourceHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            object : VirtualDisplay.Callback() {},
            handler
        )
    }

    private fun processLatest(reader: ImageReader) {
        if (!running.get()) return

        val image = reader.acquireLatestImage() ?: return
        try {
            val now = System.nanoTime()
            if (lastProcessNs != 0L && now - lastProcessNs < processIntervalNs) {
                return
            }
            lastProcessNs = now

            val captureAgeMs = if (image.timestamp > 0L) {
                ((now - image.timestamp).coerceAtLeast(0L) / 1_000_000f)
            } else {
                0f
            }

            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val rowStride = plane.rowStride

            val start = System.nanoTime()
            val count = NativeDetector.analyze(
                buffer,
                sourceWidth,
                sourceHeight,
                rowStride,
                output
            )
            val processMs = (System.nanoTime() - start) / 1_000_000f

            fpsFrames++
            if (fpsWindowStartNs == 0L) fpsWindowStartNs = now
            val windowNs = now - fpsWindowStartNs
            if (windowNs >= 1_000_000_000L) {
                fpsValue = fpsFrames * 1_000_000_000f / windowNs
                fpsFrames = 0
                fpsWindowStartNs = now
            }

            overlayState.update(
                output,
                count,
                fpsValue,
                captureAgeMs,
                processMs,
                thermalStatus,
                sourceWidth,
                sourceHeight
            )
            overlay?.postInvalidateOnAnimation()
        } catch (t: Throwable) {
            Log.e(TAG, "Frame processing failed", t)
            stopSelf()
        } finally {
            image.close()
        }
    }

    private fun registerThermalListener() {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT < 29) return

        thermalStatus = power.currentThermalStatus
        applyThermalPolicy(thermalStatus)

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            thermalStatus = status
            applyThermalPolicy(status)
        }
        thermalListener = listener
        power.addThermalStatusListener(mainExecutor, listener)
    }

    private fun applyThermalPolicy(status: Int) {
        processIntervalNs = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> 16_666_667L
            PowerManager.THERMAL_STATUS_LIGHT -> 16_666_667L
            PowerManager.THERMAL_STATUS_MODERATE -> 25_000_000L
            PowerManager.THERMAL_STATUS_SEVERE -> 33_333_333L
            PowerManager.THERMAL_STATUS_EMERGENCY -> 50_000_000L
            PowerManager.THERMAL_STATUS_SHUTDOWN -> 66_666_667L
            else -> 33_333_333L
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

    override fun onDestroy() {
        running.set(false)

        thermalListener?.let {
            if (Build.VERSION.SDK_INT >= 29) {
                val power = getSystemService(POWER_SERVICE) as PowerManager
                power.removeThermalStatusListener(it)
            }
        }
        thermalListener = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        virtualDisplay?.release()
        virtualDisplay = null

        projection?.stop()
        projection = null

        overlay?.let {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            try {
                wm.removeViewImmediate(it)
            } catch (_: Throwable) {
            }
        }
        overlay = null

        if (::worker.isInitialized) {
            worker.quitSafely()
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BrawlBrainM1"
        private const val CHANNEL_ID = "brawlbrain_m1"
        private const val NOTIFICATION_ID = 1701
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP = "com.brawlbrain.rt.m1.STOP"
    }
}
