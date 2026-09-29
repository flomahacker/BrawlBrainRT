package com.brawlbrain.rt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val captureCode = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(16,18,23))
        }
        val title = TextView(this).apply {
            text = "BrawlBrain RT"
            textSize = 28f
            setTextColor(Color.WHITE)
        }
        val sub = TextView(this).apply {
            text = "Локальный real-time мозг: экран → анализ → тактика"
            textSize = 14f
            setTextColor(0xFFB9C2D9.toInt())
            setPadding(0, 8, 0, 20)
        }
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF9FB5FF.toInt())
            setPadding(0, 10, 0, 16)
        }
        val overlay = Button(this).apply {
            text = "1. Разрешить поверх других приложений"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        }
        val access = Button(this).apply {
            text = "2. Включить управление"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val start = Button(this).apply {
            text = "3. Запустить real-time мозг"
            setOnClickListener { requestCapture() }
        }
        val auto = Switch(this).apply {
            text = "AUTO: отправлять жесты"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> BrainAccessibilityService.autoEnabled = checked }
        }
        val stop = Button(this).apply {
            text = "Остановить"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenBrainService::class.java))
                refreshStatus()
            }
        }
        root.addView(title)
        root.addView(sub)
        root.addView(status)
        root.addView(overlay)
        root.addView(access)
        root.addView(start)
        root.addView(auto)
        root.addView(stop)
        setContentView(root)
        refreshStatus()
    }

    private fun refreshStatus() {
        status.text = "Overlay: ${if (Settings.canDrawOverlays(this)) "OK" else "нет"}\n" +
            "Управление: ${if (BrainAccessibilityService.instance != null) "OK" else "нужно включить"}\n" +
            "Brain: ${if (ScreenBrainService.running) "RUNNING" else "STOPPED"}"
    }

    private fun requestCapture() {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), captureCode)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureCode || data == null || resultCode != RESULT_OK) return
        val intent = Intent(this, ScreenBrainService::class.java).apply {
            putExtra(ScreenBrainService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenBrainService.EXTRA_DATA, data)
        }
        startForegroundService(intent)
        refreshStatus()
    }
}