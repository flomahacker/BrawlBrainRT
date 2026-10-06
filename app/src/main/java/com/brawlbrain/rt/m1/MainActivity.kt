package com.brawlbrain.rt.m1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(11, 15, 20))
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "Brawl Dodge AI — Milestone 1"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        }
        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "MediaProjection + native CV + debug overlay. Управление игрой полностью выключено."
            textSize = 14f
            setTextColor(Color.rgb(182, 194, 208))
            setPadding(0, 0, 0, dp(14))
        }
        content.addView(subtitle)

        statusText = TextView(this).apply {
            text = "Статус: остановлено"
            textSize = 14f
            setTextColor(Color.rgb(39, 230, 165))
            setPadding(0, 0, 0, dp(14))
        }
        content.addView(statusText)

        val overlay = Button(this).apply {
            text = "1. Разрешение поверх других приложений"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    statusText.text = "Статус: разрешение overlay уже есть"
                }
            }
        }
        content.addView(buttonRow(overlay))

        val start = Button(this).apply {
            text = "2. Запустить захват + debug CV"
            setOnClickListener { requestCapture() }
        }
        content.addView(buttonRow(start))

        val stop = Button(this).apply {
            text = "Остановить M1"
            setOnClickListener {
                stopService(Intent(this@MainActivity, M1CaptureService::class.java))
                statusText.text = "Статус: остановлено"
            }
        }
        content.addView(buttonRow(stop))

        val info = TextView(this).apply {
            text = buildString {
                append("Профиль: Xiaomi 12X / Snapdragon 870\n")
                append("Захват: 640×288, newest-frame only\n")
                append("Целевой rate: 60 FPS, thermal-safe автоматически снижает обработку\n")
                append("Распознавание M1: игрок / враг / снаряд / оранжевая зона по цвету и форме\n")
                append("Touch injection: OFF\n\n")
                append("Тест: открой Training Cave, затем запусти захват. ")
                append("Если игра не показывается в overlay, проверь разрешение поверх других приложений.")
            }
            textSize = 13f
            setTextColor(Color.rgb(140, 154, 170))
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(info)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    private fun buttonRow(button: Button): View {
        button.isAllCaps = false
        button.setPadding(dp(12), dp(10), dp(12), dp(10))
        return button
    }

    private fun requestCapture() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = "Сначала включи разрешение overlay."
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        statusText.text = "Открой системный диалог записи экрана..."
        @Suppress("DEPRECATION")
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        )
    }

    @Deprecated("Activity Result API is intentionally avoided for this small non-UI control flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "Статус: доступ к захвату экрана отклонён"
            return
        }

        val serviceIntent = Intent(this, M1CaptureService::class.java).apply {
            putExtra(M1CaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(M1CaptureService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusText.text = "Статус: M1 запущен · смотри debug overlay"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CAPTURE = 4101
        private const val REQUEST_NOTIFICATIONS = 4102
    }
}
