package com.brawlshade

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQ_CAPTURE = 4101
        private const val REQ_OVERLAY = 4102
    }

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(10, 8, 16))
        }

        root.addView(TextView(this).apply {
            text = "BrawlShade"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "ReShade-подобный screen shader\nBloom • Color • CRT • Grain • RGB Split • Sharpen"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        })

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Запустить BrawlShade"
            setOnClickListener { ensureAndStart() }
            layoutParams = LinearLayout.LayoutParams(dp(280), dp(54))
        })

        root.addView(Button(this).apply {
            text = "Остановить эффекты"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ReShadeService::class.java))
                Toast.makeText(this@MainActivity, "BrawlShade остановлен", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
            layoutParams = LinearLayout.LayoutParams(dp(280), dp(54))
        })

        setContentView(root)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun ensureAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разреши показ поверх других приложений", Toast.LENGTH_LONG).show()
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 6001)
        }

        val intent = if (Build.VERSION.SDK_INT >= 34) {
            projectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForUserChoice()
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        startActivityForResult(intent, REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> updateStatus()
            REQ_CAPTURE -> {
                if (resultCode == RESULT_OK && data != null) {
                    val service = Intent(this, ReShadeService::class.java).apply {
                        putExtra(ReShadeService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(ReShadeService.EXTRA_RESULT_DATA, data)
                    }
                    startForegroundService(service)
                    Toast.makeText(
                        this,
                        "Захват запущен",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun updateStatus() {
        status.text = if (Settings.canDrawOverlays(this)) {
            "Слой: разрешён" + System.lineSeparator() + "Нажми запуск и выдай разрешение на захват"
        } else {
            "Нужно разрешение «поверх других приложений»"
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
