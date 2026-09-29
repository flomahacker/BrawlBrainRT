package com.brawlbrain.rt

import android.app.*
import android.content.Intent
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
import android.widget.TextView
import java.nio.ByteBuffer

class ScreenBrainService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        @Volatile var running = false
    }

    private lateinit var projection: MediaProjection
    private lateinit var reader: ImageReader
    private var display: VirtualDisplay? = null
    private lateinit var wm: WindowManager
    private var hud: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val analyzer = FrameAnalyzer()
    private val brain = TacticalBrain()
    private var lastProcess = 0L
    private var lastState = BrainState()

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(7, notification())
        running = true
        showHud()
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
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startProjection(code: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ ir -> processLatest(ir, w, h) }, handler)
        display = projection.createVirtualDisplay(
            "BrawlBrainRT", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
    }

    private fun processLatest(ir: ImageReader, w: Int, h: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastProcess < 82L) {
            ir.acquireLatestImage()?.close()
            return
        }
        lastProcess = now
        val image = ir.acquireLatestImage() ?: return
        image.use {
            val plane = it.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * w
            val tmp = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
            tmp.copyPixelsFromBuffer(buffer)
            val scaled = Bitmap.createScaledBitmap(tmp, 320, 180, false)
            tmp.recycle()
            lastState = analyzer.analyze(scaled)
            val action = brain.decide(lastState)
            scaled.recycle()
            updateHud(action)
            BrainAccessibilityService.instance?.apply(action, w, h)
        }
    }

    private fun showHud() {
        if (!Settings.canDrawOverlays(this)) return
        hud = TextView(this).apply {
            setTextColor(0xFFF4F7FF.toInt())
            setBackgroundColor(0xAA101217.toInt())
            textSize = 11f
            setPadding(16, 10, 16, 10)
            text = "BRAIN • RT\nSCANNING..."
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 14; y = 44 }
        wm.addView(hud, lp)
    }

    private fun updateHud(a: Action) {
        handler.post {
            hud?.text = "BRAIN • ${if (BrainAccessibilityService.autoEnabled) "AUTO" else "ASSIST"}\n" +
                "${a.reason}  conf ${(a.confidence * 100).toInt()}%\n" +
                "danger ${(lastState.danger * 100).toInt()}%  target ${(lastState.enemyConfidence * 100).toInt()}%\n" +
                "RT ~12 FPS"
        }
    }

    private fun notification(): Notification {
        val ch = "brain_rt"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(ch, "BrawlBrain RT", NotificationManager.IMPORTANCE_LOW))
        }
        return Notification.Builder(this, ch)
            .setContentTitle("BrawlBrain RT")
            .setContentText("Real-time screen analysis is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        try { hud?.let { wm.removeView(it) } } catch (_: Throwable) {}
        display?.release()
        if (::reader.isInitialized) reader.close()
        if (::projection.isInitialized) projection.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}