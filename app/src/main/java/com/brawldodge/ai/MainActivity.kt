package com.brawldodge.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var overlayButton: Button
    private val captureRequest = 4107

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7701)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(9, 13, 19))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "BRAWL DODGE AI"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(this).apply {
            text = "REAL-TIME VISION ASSISTANT"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(112, 148, 255))
            setPadding(0, dp(4), 0, dp(16))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.rgb(17, 24, 35))
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(230, 235, 243))
        }
        card.addView(status)
        root.addView(card, fullWidth().withMargins(0, 0, 0, 12))

        overlayButton = Button(this).apply {
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            text = "1. Разрешение поверх других приложений"
            setOnClickListener { openOverlaySettings() }
        }
        root.addView(overlayButton, fullWidth().withMargins(0, 6, 0, 6))

        root.addView(Button(this).apply {
            isAllCaps = false
            textSize = 14f
            text = "2. Запустить Brawl Dodge AI"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.rgb(82, 120, 235))
            }
            setOnClickListener { requestScreenCapture() }
        }, fullWidth().withMargins(0, 6, 0, 6))

        root.addView(Button(this).apply {
            isAllCaps = false
            textSize = 14f
            text = "Остановить"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.rgb(28, 36, 49))
            }
            setOnClickListener {
                stopService(Intent(this@MainActivity, DodgeService::class.java))
                refreshStatus()
            }
        }, fullWidth().withMargins(0, 6, 0, 12))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.rgb(13, 18, 27))
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        info.addView(TextView(this).apply {
            text = "Что делает приложение"
            textSize = 15f
            setTextColor(Color.WHITE)
        })

        info.addView(TextView(this).apply {
            text = "👁 Смотрит на экран через MediaProjection\n" +
                "🎯 Ищет игрока, врагов и быстрые угрозы\n" +
                "🧠 Оценивает ближайшую опасность\n" +
                "⚡ Оптимизировано под Xiaomi 12X\n" +
                "🛡 Не нажимает кнопки игры"
            textSize = 13f
            setTextColor(Color.rgb(157, 169, 186))
            setPadding(0, dp(10), 0, 0)
        })

        root.addView(info, fullWidth().withMargins(0, 0, 0, 14))

        root.addView(TextView(this).apply {
            text = "HUD появляется сразу после запуска сервиса. Тестируй в Training Cave / Friendly Battle."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(107, 120, 138))
        })

        scroll.addView(root)
        return scroll
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val running = DodgeService.isRunning
        val summary = DodgeService.latestSummary

        status.text = String.format(
            Locale.US,
            "OVERLAY: %s\nENGINE: %s\nVISION: %s",
            if (overlayOk) "OK" else "НУЖНО РАЗРЕШЕНИЕ",
            if (running) "РАБОТАЕТ" else "СТОП",
            summary.ifBlank { "ожидание" }
        )

        overlayButton.text =
            if (overlayOk) "✅ Разрешение overlay включено"
            else "1. Разрешение поверх других приложений"
    }

    private fun openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            refreshStatus()
            return
        }

        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
        )
    }

    private fun requestScreenCapture() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Legacy result API is retained for the Android screen-capture permission callback.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequest) return

        if (resultCode != RESULT_OK || data == null) {
            status.text = "Захват экрана: доступ отклонён"
            return
        }

        val serviceIntent = Intent(this, DodgeService::class.java).apply {
            putExtra(DodgeService.EXTRA_RESULT_CODE, resultCode)
            putExtra(DodgeService.EXTRA_DATA, data)
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            status.text = "OVERLAY: OK\nENGINE: запускается…\nHUD: появится сразу"
        } catch (err: Throwable) {
            status.text = "Не удалось запустить: " + err.javaClass.simpleName
        }
    }

    private fun fullWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun LinearLayout.LayoutParams.withMargins(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {
        setMargins(dp(left), dp(top), dp(right), dp(bottom))
        return this
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
