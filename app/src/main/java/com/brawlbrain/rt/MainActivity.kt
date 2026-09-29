package com.brawlbrain.rt

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val captureCode = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30, 36, 30, 30)
            setBackgroundColor(Color.rgb(10, 12, 18))
        }

        val title = TextView(this).apply {
            text = "BRAWLBRAIN RT"
            textSize = 30f
            setTextColor(Color.WHITE)
        }

        val subtitle = TextView(this).apply {
            text = "AI-помощник в бою • vision работает прямо на Xiaomi 12X"
            textSize = 15f
            setTextColor(0xFFB8C0D2.toInt())
            setPadding(0, 8, 0, 24)
        }

        status = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF8AA8FF.toInt())
            setPadding(0, 12, 0, 20)
        }

        val overlayButton = Button(this).apply {
            text = "1 • Разрешить HUD поверх игры"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + packageName)
                    )
                )
            }
        }

        val startButton = Button(this).apply {
            text = "2 • Запустить AI"
            setOnClickListener { requestCapture() }
        }

        val stopButton = Button(this).apply {
            text = "Остановить AI"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenBrainService::class.java))
                refreshStatus()
            }
        }

        val info = TextView(this).apply {
            text = "AI подсказывает в реальном времени:\n" +
                "• позиции игрока и врагов\n" +
                "• ближайшую угрозу\n" +
                "• цель и направление\n" +
                "• стены и укрытия\n" +
                "• тактический режим: давить / держать / отходить\n\n" +
                "Профиль: ARM64, упор на стабильность и низкую задержку."
            textSize = 14f
            setTextColor(0xFF8F98AB.toInt())
            setPadding(4, 18, 4, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(overlayButton)
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(info)

        setContentView(root)
        refreshStatus()
    }

    private fun refreshStatus() {
        val hud = if (Settings.canDrawOverlays(this)) "ГОТОВ" else "нужно разрешение"
        val ai = if (ScreenBrainService.running) "ЗАПУЩЕН" else "остановлен"

        status.text = "HUD: " + hud + "\nAI: " + ai
    }

    private fun requestCapture() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
            return
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            captureCode
        )
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != captureCode || resultCode != RESULT_OK || data == null) {
            return
        }

        val intent = Intent(this, ScreenBrainService::class.java).apply {
            putExtra(ScreenBrainService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenBrainService.EXTRA_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
        else startService(intent)

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }
}