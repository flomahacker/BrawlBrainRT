package com.brawlshade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class ReShadeService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "brawlshade_projection"
        private const val NOTIFICATION_ID = 4407
    }

    private lateinit var wm: WindowManager
    private var filterView: ReShadeView? = null
    private var controllerRoot: FrameLayout? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var started = false
    private lateinit var panel: ScrollView
    private val state = EffectState()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_STICKY

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startNotification()
        started = true
        wm = getSystemService(WindowManager::class.java)

        createFilterOverlay()
        createControlOverlay()

        val mgr = getSystemService(MediaProjectionManager::class.java)
        projection = mgr.getMediaProjection(resultCode, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, null)

        return START_STICKY
    }

    private fun startNotification() {
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val n = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_shade)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_shade)
                .setOngoing(true)
                .build()
        }

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun createFilterOverlay() {
        filterView = ReShadeView(this) { surface ->
            inputSurface = surface
            setupVirtualDisplay()
        }

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        wm.addView(filterView, p)
    }

    private fun createControlOverlay() {
        val root = FrameLayout(this)

        panel = ScrollView(this).apply {
            visibility = View.GONE
            background = rounded(0xF20E0B15.toInt(), 18)
        }

        val icon = TextView(this).apply {
            text = "◉"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF21182E.toInt(), 999)
            elevation = dp(8).toFloat()
            setOnClickListener {
                panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        panel.addView(content)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "BrawlShade"
            textSize = 19f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        })
        header.addView(Button(this).apply {
            text = "✕"
            setOnClickListener { panel.visibility = View.GONE }
        })
        content.addView(header)

        content.addView(Switch(this).apply {
            text = "Эффекты"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = state.enabled
            setOnCheckedChangeListener { _, v ->
                state.enabled = v
                filterView?.updateState(state)
            }
        })

        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf(
            "RESET" to {
                state.reset()
            },
            "VIVID" to {
                state.reset()
                state.saturation = 1.22f
                state.contrast = 1.10f
                state.sharpen = 0.18f
                state.vibrance = 0.20f
                state.blueBoost = 0.35f
            },
            "NEON" to {
                state.reset()
                state.saturation = 1.32f
                state.contrast = 1.14f
                state.bloom = 0.34f
                state.bloomThreshold = 0.62f
                state.chromatic = 0.18f
                state.blueBoost = 0.60f
            },
            "FILM" to {
                state.reset()
                state.contrast = 0.94f
                state.saturation = 0.90f
                state.grain = 0.20f
                state.vignette = 0.25f
                state.temperature = 0.22f
            }
        ).forEach { (name, apply) ->
            presets.addView(Button(this).apply {
                text = name
                textSize = 10f
                setOnClickListener {
                    apply()
                    rebuildRows(content)
                    filterView?.updateState(state)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(6))
                }
            })
        }

        content.addView(presets)
        rebuildRows(content)

        root.addView(
            panel,
            FrameLayout.LayoutParams(dp(330), dp(520)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(66)
                rightMargin = dp(8)
            }
        )

        root.addView(
            icon,
            FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(8)
                rightMargin = dp(8)
            }
        )

        val p = WindowManager.LayoutParams(
            dp(390),
            dp(600),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.END
        wm.addView(root, p)
        controllerRoot = root
    }

    private fun rebuildRows(content: LinearLayout) {
        while (content.childCount > 3) content.removeViewAt(3)

        addEffect(content, "Bloom", { state.bloom }, { state.bloom = it }, 0f, 1f)
        addEffect(content, "Bloom threshold", { state.bloomThreshold }, { state.bloomThreshold = it }, 0.35f, 0.95f)
        addEffect(content, "Sharpen", { state.sharpen }, { state.sharpen = it }, 0f, 0.8f)
        addEffect(content, "Saturation", { state.saturation }, { state.saturation = it }, 0.4f, 1.8f)
        addEffect(content, "Contrast", { state.contrast }, { state.contrast = it }, 0.5f, 1.5f)
        addEffect(content, "Brightness", { state.brightness }, { state.brightness = it }, -0.5f, 0.5f)
        addEffect(content, "Gamma", { state.gamma }, { state.gamma = it }, 0.5f, 1.7f)
        addEffect(content, "Temperature", { state.temperature }, { state.temperature = it }, -1f, 1f)
        addEffect(content, "Tint", { state.tint }, { state.tint = it }, -1f, 1f)
        addEffect(content, "Vibrance", { state.vibrance }, { state.vibrance = it }, 0f, 1f)
        addEffect(content, "Vignette", { state.vignette }, { state.vignette = it }, 0f, 1f)
        addEffect(content, "Chromatic aberration", { state.chromatic }, { state.chromatic = it }, 0f, 1f)
        addEffect(content, "RGB split", { state.rgbSplit }, { state.rgbSplit = it }, 0f, 1f)
        addEffect(content, "Film grain", { state.grain }, { state.grain = it }, 0f, 1f)
        addEffect(content, "Scanlines", { state.scanlines }, { state.scanlines = it }, 0f, 1f)
        addEffect(content, "CRT", { state.crt }, { state.crt = it }, 0f, 1f)
        addEffect(content, "Colorize", { state.colorize }, { state.colorize = it }, 0f, 1f)
        addEffect(content, "Hue", { state.hue }, { state.hue = it }, -3.14f, 3.14f)
        addEffect(content, "Posterize", { state.posterize }, { state.posterize = it }, 0f, 1f)
        addEffect(content, "Edge glow", { state.edgeGlow }, { state.edgeGlow = it }, 0f, 1f)
        addEffect(content, "Invert", { state.invert }, { state.invert = it }, 0f, 1f)
        addEffect(content, "Blue boost", { state.blueBoost }, { state.blueBoost = it }, 0f, 1f)
    }

    private fun addEffect(
        parent: LinearLayout,
        label: String,
        getter: () -> Float,
        setter: (Float) -> Unit,
        min: Float,
        max: Float
    ) {
        val title = TextView(this).apply {
            text = label + "   " + String.format("%.2f", getter())
            textSize = 12f
            setTextColor(Color.WHITE)
        }

        val seek = SeekBar(this).apply {
            this.max = 100
            progress = (((getter() - min) / (max - min)) * 100f).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = min + (max - min) * progress / 100f
                    setter(value)
                    title.text = label + "   " + String.format("%.2f", value)
                    filterView?.updateState(state)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }

        parent.addView(title)
        parent.addView(seek, LinearLayout.LayoutParams(-1, dp(38)))
    }

    private fun setupVirtualDisplay() {
        val surface = inputSurface ?: return
        if (virtualDisplay != null) return

        val dm = resources.displayMetrics
        try {
            virtualDisplay = projection?.createVirtualDisplay(
                "BrawlShade",
                dm.widthPixels,
                dm.heightPixels,
                dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
        } catch (t: Throwable) {
            Toast.makeText(
                this,
                "Не удалось запустить захват: " + (t.message ?: "unknown"),
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        virtualDisplay = null

        try { projection?.stop() } catch (_: Throwable) {}
        projection = null

        filterView?.let {
            try { it.releaseInput() } catch (_: Throwable) {}
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        filterView = null

        controllerRoot?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        controllerRoot = null
        inputSurface = null

        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), 0x44FFFFFF)
        }
}
