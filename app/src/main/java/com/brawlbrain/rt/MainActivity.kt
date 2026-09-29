package com.brawlbrain.rt

import android.Manifest
import android.app.Activity
import android.content.Context
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
import android.widget.*

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var modeSpinner: Spinner
    private lateinit var roleSpinner: Spinner
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
        val cfg = BrainPrefs.load(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF080A10.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 36)
        }

        root.addView(TextView(this).apply {
            text = "BRAWLBRAIN"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(this).apply {
            text = "REAL-TIME AI COMPANION"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF7FA3FF.toInt())
            setPadding(0, 5, 0, 20)
        })

        val statusCard = card()
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFDDE4F4.toInt())
            setPadding(20, 17, 20, 17)
        }
        statusCard.addView(status)
        root.addView(statusCard)

        root.addView(sectionLabel("БЫСТРЫЙ ПРОФИЛЬ"))

        modeSpinner = spinner("Режим", listOf(
            "Universal", "Showdown", "Gem Grab", "Brawl Ball", "Knockout",
            "Wipeout", "Bounty", "Hot Zone", "Heist", "Paint Brawl", "Basket Brawl", "Duels"
        ), cfg.gameMode)

        roleSpinner = spinner("Архетип", listOf(
            "Universal", "Shooter", "Assassin", "Tank", "Thrower", "Support"
        ), cfg.role)

        val start = button("ЗАПУСТИТЬ AI", true)
        start.setOnClickListener { requestCapture() }

        val stop = button("ОСТАНОВИТЬ", false)
        stop.setOnClickListener {
            stopService(Intent(this@MainActivity, ScreenBrainService::class.java))
            refreshStatus()
        }

        val settings = button("⚙  НАСТРОЙКИ МОЗГА И HUD", false)
        settings.setOnClickListener {
            BrainPrefs.save(
                this,
                BrainPrefs.load(this).copy(
                    gameMode = modeSpinner.selectedItem.toString(),
                    role = roleSpinner.selectedItem.toString()
                )
            )
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        root.addView(start)
        root.addView(stop)
        root.addView(settings)

        root.addView(sectionLabel("ЧТО УМЕЕТ МОДЕЛЬ"))

        val features = card()
        features.addView(TextView(this).apply {
            text = "👁 Vision\n" +
                "• враги / игрок / тиммейты\n" +
                "• стены и укрытия\n" +
                "• приоритетная цель\n\n" +
                "🧠 Tactical Brain\n" +
                "• память движения цели\n" +
                "• прогноз позиции\n" +
                "• угроза / окно для давления\n" +
                "• изоляция / численный риск\n" +
                "• профиль режима и архетипа\n\n" +
                "⚡ Xiaomi 12X profile\n" +
                "ARM64 • reduced working resolution • локальный ONNX"
            textSize = 14f
            setTextColor(0xFFB1B9CC.toInt())
            setPadding(20, 18, 20, 18)
        })
        root.addView(features)

        root.addView(TextView(this).apply {
            text = "Подсказки отображаются поверх игры. Автоматических нажатий по игре нет."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF697286.toInt())
            setPadding(8, 20, 8, 0)
        })

        scroll.addView(root)
        setContentView(scroll)
        refreshStatus()
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(0xFF7297FF.toInt())
        setPadding(5, 22, 5, 7)
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0xFF11151F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 10) }
        }
    }

    private fun button(textValue: String, primary: Boolean): Button {
        return Button(this).apply {
            text = textValue
            textSize = 14f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(if (primary) 0xFF557DFF.toInt() else 0xFF171C27.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply { setMargins(0, 7, 0, 7) }
        }
    }

    private fun spinner(label: String, values: List<String>, selected: String): Spinner {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 3, 0, 6)
        }

        wrapper.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF70798C.toInt())
        })

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            values
        )
        spinner.setSelection(values.indexOf(selected).coerceAtLeast(0))
        wrapper.addView(spinner)
        (rootContainer(wrapper))?.let { it.addView(wrapper) }
        return spinner
    }

    private fun rootContainer(view: android.view.View): LinearLayout? {
        return view.parent as? LinearLayout
    }

    private fun refreshStatus() {
        val hud = if (Settings.canDrawOverlays(this)) "ГОТОВ" else "НЕТ РАЗРЕШЕНИЯ"
        val ai = if (ScreenBrainService.running) "РАБОТАЕТ" else "СТОП"
        status.text = "HUD: $hud    •    AI: $ai\n" +
            "Профиль: ${BrainPrefs.load(this).role} / ${BrainPrefs.load(this).gameMode}"
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
        startActivityForResult(manager.createScreenCaptureIntent(), captureCode)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureCode || resultCode != RESULT_OK || data == null) return

        BrainPrefs.save(
            this,
            BrainPrefs.load(this).copy(
                gameMode = modeSpinner.selectedItem.toString(),
                role = roleSpinner.selectedItem.toString()
            )
        )

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

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}