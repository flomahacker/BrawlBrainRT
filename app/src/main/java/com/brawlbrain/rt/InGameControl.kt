package com.brawlbrain.rt

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class InGameControl(
    private val context: Context,
    private val wm: WindowManager,
    private val onChanged: (BrainConfig) -> Unit
) {
    private var brainButton: TextView? = null
    private var panel: LinearLayout? = null

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    fun show() {
        if (brainButton != null) return

        brainButton = TextView(context).apply {
            text = "🧠"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = bg(0xEE121722.toInt(), 20)
            setOnClickListener {
                if (panel == null) openPanel() else closePanel()
            }
        }

        val brainLp = WindowManager.LayoutParams(
            dp(54), dp(54),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(48)
        }
        wm.addView(brainButton, brainLp)
    }

    private fun openPanel() {
        if (panel != null) return

        val cfg = BrainPrefs.load(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = bg(0xF3151A24.toInt(), 22)
        }

        root.addView(TextView(context).apply {
            text = "BRAWLBRAIN • LIVE"
            textSize = 17f
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(context).apply {
            text = "Память • прогноз • дальность • предупреждения"
            textSize = 11f
            setTextColor(0xFF8795AF.toInt())
            setPadding(0, dp(2), 0, dp(8))
        })

        val brawler = Spinner(context)
        val brawlers = arrayOf("Buzz", "Tick")
        brawler.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            brawlers
        )
        brawler.setSelection(if (cfg.brawler == "Tick") 1 else 0)
        root.addView(label("БОЕЦ", brawler))

        val ghost = Switch(context).apply {
            text = "Память врагов"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = cfg.showGhosts
        }
        root.addView(ghost)

        val prediction = Switch(context).apply {
            text = "Точка упреждения"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = cfg.showPrediction
        }
        root.addView(prediction)

        val range = Switch(context).apply {
            text = "Кольцо дальности"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = cfg.showRangeRings
        }
        root.addView(range)

        val warnings = Switch(context).apply {
            text = "Критичные предупреждения"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = cfg.showWarnings
        }
        root.addView(warnings)

        val save = Button(context).apply {
            text = "ПРИМЕНИТЬ"
            setTextColor(Color.WHITE)
            background = bg(0xFF5B80FF.toInt(), 17)
            setOnClickListener {
                val selected = brawler.selectedItem.toString()
                val updated = cfg.copy(
                    brawler = selected,
                    attackRangeRaw = BrawlerCombatTable.profile(selected).attackRangeRaw,
                    showGhosts = ghost.isChecked,
                    showPrediction = prediction.isChecked,
                    showRangeRings = range.isChecked,
                    showWarnings = warnings.isChecked
                )

                BrainPrefs.save(context, updated)
                onChanged(updated)
                closePanel()
                Toast.makeText(context, "Настройки применены", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(save)

        root.addView(Button(context).apply {
            text = "НАСТРОЙКИ"
            setTextColor(0xFFBAC4D7.toInt())
            background = bg(0xFF202633.toInt(), 17)
            setOnClickListener {
                BrainPrefs.save(
                    context,
                    BrainPrefs.load(context).copy(
                        brawler = brawler.selectedItem.toString(),
                        attackRangeRaw = BrawlerCombatTable.profile(
                            brawler.selectedItem.toString()
                        ).attackRangeRaw
                    )
                )
                context.startActivity(
                    Intent(context, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        })

        root.addView(Button(context).apply {
            text = "ЗАКРЫТЬ"
            setTextColor(0xFFBAC4D7.toInt())
            background = bg(0xFF202633.toInt(), 17)
            setOnClickListener { closePanel() }
        })

        panel = root

        val lp = WindowManager.LayoutParams(
            dp(300), dp(360),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(112)
        }

        wm.addView(root, lp)
    }

    private fun closePanel() {
        panel?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        panel = null
    }

    fun hide() {
        closePanel()
        brainButton?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        brainButton = null
    }

    private fun label(title: String, spinner: Spinner): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 10f
                setTextColor(0xFF6F7B92.toInt())
            })
            addView(spinner)
        }
    }

    private fun bg(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }
}
