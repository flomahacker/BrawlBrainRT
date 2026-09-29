package com.brawlbrain.rt

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable

class SettingsActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var modeSpinner: Spinner
    private lateinit var roleSpinner: Spinner
    private lateinit var performanceSpinner: Spinner
    private lateinit var entitySeek: SeekBar
    private lateinit var wallSeek: SeekBar
    private lateinit var confidenceSeek: SeekBar
    private lateinit var opacitySeek: SeekBar
    private lateinit var scaleSeek: SeekBar

    private val toggles = LinkedHashMap<String, Switch>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val cfg = BrainPrefs.load(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0A0C12.toInt())
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 34, 26, 42)
        }

        root.addView(title("BRAWLBRAIN PRO"))
        root.addView(body("Настрой мозг под свой стиль игры. Все параметры сохраняются на телефоне."))

        modeSpinner = spinner("РЕЖИМ", listOf(
            "Universal", "Showdown", "Gem Grab", "Brawl Ball", "Knockout",
            "Wipeout", "Bounty", "Hot Zone", "Heist", "Paint Brawl", "Basket Brawl", "Duels"
        ), cfg.gameMode)

        roleSpinner = spinner("АРХЕТИП БРАВЛЕРА", listOf(
            "Universal", "Shooter", "Assassin", "Tank", "Thrower", "Support"
        ), cfg.role)

        performanceSpinner = spinner("ПРОФИЛЬ ПРОИЗВОДИТЕЛЬНОСТИ", listOf(
            "Battery Saver", "Balanced", "Quality"
        ), cfg.performance)

        addSection("МОЗГ")
        addSpinner(modeSpinner)
        addSpinner(roleSpinner)
        addSpinner(performanceSpinner)

        addSection("VISION")
        entitySeek = seek("Частота анализа врагов", 80, 220, cfg.entityIntervalMs.toInt(), " мс")
        wallSeek = seek("Частота анализа стен/укрытий", 300, 1500, cfg.wallIntervalMs.toInt(), " мс")
        confidenceSeek = seek("Порог уверенности", 20, 70, cfg.confidencePercent, "%")

        addSection("HUD")
        opacitySeek = seek("Прозрачность HUD", 45, 100, cfg.hudOpacityPercent, "%")
        scaleSeek = seek("Размер HUD", 75, 130, cfg.hudScalePercent, "%")

        addSection("СЛОИ")
        toggle("Показывать врагов", "enemy", cfg.showEnemies)
        toggle("Показывать тиммейтов", "teammate", cfg.showTeammates)
        toggle("Показывать стены/укрытия", "walls", cfg.showWalls)
        toggle("Линия на приоритетную цель", "target", cfg.showTargetLine)
        toggle("Кольцо угрозы", "threat", cfg.showThreat)
        toggle("Главный тактический совет", "advice", cfg.showAdvice)
        toggle("Диагностика FPS / latency", "debug", cfg.showDebug)
        toggle("Минимум анимаций", "motion", cfg.reducedMotion)

        addSection("УПРАВЛЕНИЕ")
        val save = Button(this).apply {
            text = "СОХРАНИТЬ"
            setOnClickListener {
                saveConfig()
                Toast.makeText(this@SettingsActivity, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        styleButton(save, true)
        root.addView(save)

        val reset = Button(this).apply {
            text = "СБРОСИТЬ В ПРЕСЕТ"
            setOnClickListener {
                BrainPrefs.reset(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, "Сброшено", Toast.LENGTH_SHORT).show()
                render()
            }
        }
        styleButton(reset, false)
        root.addView(reset)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun addSection(label: String) {
        val tv = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF85A8FF.toInt())
            setPadding(4, 28, 4, 8)
        }
        root.addView(tv)
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 28f
        gravity = Gravity.CENTER_HORIZONTAL
        setTextColor(Color.WHITE)
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFF9BA4B7.toInt())
        setPadding(8, 10, 8, 6)
    }

    private fun addSpinner(spinner: Spinner) {
        root.addView(spinner)
    }

    private fun spinner(label: String, values: List<String>, selected: String): Spinner {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 8)
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF777F92.toInt())
        }
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            values
        )
        spinner.adapter = adapter
        val idx = values.indexOf(selected).coerceAtLeast(0)
        spinner.setSelection(idx)
        wrapper.addView(labelView)
        wrapper.addView(spinner)
        root.addView(wrapper)
        return spinner
    }

    private fun seek(label: String, min: Int, max: Int, current: Int, suffix: String): SeekBar {
        val titleView = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF777F92.toInt())
            setPadding(0, 6, 0, 0)
        }
        val bar = SeekBar(this).apply {
            max = max - min
            progress = (current - min).coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    titleView.text = label + ": " + (min + p) + suffix
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
        bar.tag = min
        root.addView(titleView)
        root.addView(bar)
        return bar
    }

    private fun toggle(text: String, key: String, checked: Boolean) {
        val sw = Switch(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = checked
            setPadding(0, 5, 0, 5)
        }
        toggles[key] = sw
        root.addView(sw)
    }

    private fun styleButton(button: Button, primary: Boolean) {
        val bg = GradientDrawable().apply {
            cornerRadius = 30f
            setColor(if (primary) 0xFF547CFF.toInt() else 0xFF1A1E28.toInt())
        }
        button.background = bg
        button.setTextColor(Color.WHITE)
        button.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            58
        ).apply { setMargins(0, 8, 0, 8) }
    }

    private fun value(bar: SeekBar): Int {
        val min = (bar.tag as Int)
        return min + bar.progress
    }

    private fun saveConfig() {
        val cfg = BrainConfig(
            gameMode = modeSpinner.selectedItem.toString(),
            role = roleSpinner.selectedItem.toString(),
            performance = performanceSpinner.selectedItem.toString(),
            entityIntervalMs = value(entitySeek).toLong(),
            wallIntervalMs = value(wallSeek).toLong(),
            confidencePercent = value(confidenceSeek),
            hudOpacityPercent = value(opacitySeek),
            hudScalePercent = value(scaleSeek),
            showEnemies = toggles["enemy"]?.isChecked ?: true,
            showTeammates = toggles["teammate"]?.isChecked ?: true,
            showWalls = toggles["walls"]?.isChecked ?: true,
            showTargetLine = toggles["target"]?.isChecked ?: true,
            showThreat = toggles["threat"]?.isChecked ?: true,
            showAdvice = toggles["advice"]?.isChecked ?: true,
            showDebug = toggles["debug"]?.isChecked ?: true,
            reducedMotion = toggles["motion"]?.isChecked ?: true
        )
        BrainPrefs.save(this, cfg)
    }
}