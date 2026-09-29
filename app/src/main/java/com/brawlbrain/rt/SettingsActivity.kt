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

        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF090B11.toInt()) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 30, 26, 42)
        }

        root.addView(title("BRAWLBRAIN PRO"))
        root.addView(body("Глубокая настройка vision, HUD и тактического мозга. Профиль хранится локально."))

        addSection("БОЕВОЙ ПРОФИЛЬ")
        modeSpinner = makeSpinner("Режим", listOf(
            "Universal", "Showdown", "Gem Grab", "Brawl Ball", "Knockout",
            "Wipeout", "Bounty", "Hot Zone", "Heist", "Paint Brawl", "Basket Brawl", "Duels"
        ), cfg.gameMode)

        roleSpinner = makeSpinner("Архетип", listOf(
            "Universal", "Shooter", "Assassin", "Tank", "Thrower", "Support"
        ), cfg.role)

        performanceSpinner = makeSpinner("Производительность", listOf(
            "Battery Saver", "Balanced", "Quality"
        ), cfg.performance)

        addSection("VISION")
        entitySeek = addSeek("Интервал детектора врагов", 70, 240, cfg.entityIntervalMs.toInt(), " мс")
        wallSeek = addSeek("Интервал стен/укрытий", 250, 1600, cfg.wallIntervalMs.toInt(), " мс")
        confidenceSeek = addSeek("Порог уверенности", 20, 70, cfg.confidencePercent, "%")

        addSection("HUD")
        opacitySeek = addSeek("Прозрачность", 40, 100, cfg.hudOpacityPercent, "%")
        scaleSeek = addSeek("Размер", 75, 130, cfg.hudScalePercent, "%")

        addSection("СЛОИ")
        addToggle("Показывать врагов", "enemy", cfg.showEnemies)
        addToggle("Показывать тиммейтов", "teammate", cfg.showTeammates)
        addToggle("Показывать стены/укрытия", "walls", cfg.showWalls)
        addToggle("Линия приоритетной цели", "target", cfg.showTargetLine)
        addToggle("Кольцо угрозы", "threat", cfg.showThreat)
        addToggle("Главный совет", "advice", cfg.showAdvice)
        addToggle("FPS / latency", "debug", cfg.showDebug)
        addToggle("Минимум анимаций", "motion", cfg.reducedMotion)

        addSection("ПРИМЕЧАНИЕ")
        root.addView(body("BrawlBrain анализирует экран и даёт тактические подсказки. Он не нажимает кнопки игры автоматически."))

        val save = Button(this).apply {
            text = "СОХРАНИТЬ"
            setOnClickListener {
                saveConfig()
                Toast.makeText(this@SettingsActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        styleButton(save, true)
        root.addView(save)

        val reset = Button(this).apply {
            text = "СБРОСИТЬ"
            setOnClickListener {
                BrainPrefs.reset(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, "Профиль сброшен", Toast.LENGTH_SHORT).show()
                render()
            }
        }
        styleButton(reset, false)
        root.addView(reset)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun addSection(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF7FA4FF.toInt())
            setPadding(4, 25, 4, 7)
        })
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 28f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFF9AA4B8.toInt())
        setPadding(8, 10, 8, 4)
    }

    private fun makeSpinner(label: String, values: List<String>, selected: String): Spinner {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 2, 0, 8)
        }
        wrapper.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF727B8D.toInt())
        })

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
        spinner.adapter = adapter
        spinner.setSelection(values.indexOf(selected).coerceAtLeast(0))
        wrapper.addView(spinner)
        root.addView(wrapper)
        return spinner
    }

    private fun addSeek(label: String, minValue: Int, maxValue: Int, current: Int, suffix: String): SeekBar {
        val labelView = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF727B8D.toInt())
        }
        val bar = SeekBar(this).apply {
            max = maxValue - minValue
            progress = (current - minValue).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    labelView.text = label + ": " + (minValue + p) + suffix
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
        labelView.text = label + ": " + current + suffix
        bar.tag = minValue
        root.addView(labelView)
        root.addView(bar)
        return bar
    }

    private fun addToggle(text: String, key: String, checked: Boolean) {
        val sw = Switch(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = checked
            setPadding(0, 4, 0, 4)
        }
        toggles[key] = sw
        root.addView(sw)
    }

    private fun styleButton(button: Button, primary: Boolean) {
        button.background = GradientDrawable().apply {
            cornerRadius = 30f
            setColor(if (primary) 0xFF557EFF.toInt() else 0xFF191D27.toInt())
        }
        button.setTextColor(Color.WHITE)
        button.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 58
        ).apply { setMargins(0, 7, 0, 7) }
    }

    private fun value(bar: SeekBar): Int {
        return (bar.tag as Int) + bar.progress
    }

    private fun saveConfig() {
        BrainPrefs.save(this, BrainConfig(
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
        ))
    }
}