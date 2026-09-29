package com.brawlbrain.rt

import android.content.Context
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
    private var button: TextView? = null
    private var panel: LinearLayout? = null

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    fun show() {
        if (button != null) return
        button = TextView(context).apply {
            text = "🧠"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = bg(0xEE121722.toInt(), 20)
            setOnClickListener { toggle() }
        }
        val lp = WindowManager.LayoutParams(
            dp(54), dp(54),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(48)
        }
        wm.addView(button, lp)
    }

    private fun toggle() {
        if (panel == null) openPanel() else closePanel()
    }

    private fun openPanel() {
        val cfg = BrainPrefs.load(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = bg(0xF2141822.toInt(), 20)
        }

        root.addView(TextView(context).apply {
            text = "BRAWLBRAIN • LIVE"
            textSize = 17f
            setTextColor(Color.WHITE)
        })

        val brawler = Spinner(context)
        val brawlers = arrayOf("Buzz", "Tick")
        brawler.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, brawlers)
        brawler.setSelection(if (cfg.brawler == "Tick") 1 else 0)
        root.addView(label("Боец", brawler))

        val advice = Switch(context).apply {
            text = "Тактика"
            setTextColor(Color.WHITE)
            isChecked = cfg.showAdvice
        }
        val target = Switch(context).apply {
            text = "Приоритетная цель"
            setTextColor(Color.WHITE)
            isChecked = cfg.showTargetLine
        }
        val enemies = Switch(context).apply {
            text = "Метки врагов"
            setTextColor(Color.WHITE)
            isChecked = cfg.showEnemies
        }
        root.addView(advice)
        root.addView(target)
        root.addView(enemies)

        root.addView(Button(context).apply {
            text = "ПРИМЕНИТЬ"
            setTextColor(Color.WHITE)
            background = bg(0xFF5B80FF.toInt(), 16)
            setOnClickListener {
                val updated = cfg.copy(
                    brawler = brawler.selectedItem.toString(),
                    showAdvice = advice.isChecked,
                    showTargetLine = target.isChecked,
                    showEnemies = enemies.isChecked
                )
                BrainPrefs.save(context, updated)
                onChanged(updated)
                closePanel()
            }
        })

        root.addView(Button(context).apply {
            text = "ЗАКРЫТЬ"
            setTextColor(0xFFB9C3D6.toInt())
            background = bg(0xFF202633.toInt(), 16)
            setOnClickListener { closePanel() }
        })

        panel = root
        val lp = WindowManager.LayoutParams(
            dp(300), dp(390),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(114)
        }
        wm.addView(root, lp)
    }

    private fun closePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        panel = null
    }

    fun hide() {
        closePanel()
        button?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        button = null
    }

    private fun label(title: String, spinner: Spinner): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 11f
                setTextColor(0xFF727E95.toInt())
            })
            addView(spinner)
        }
    }

    private fun bg(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }
}