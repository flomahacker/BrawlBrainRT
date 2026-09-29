package com.brawlbrain.rt

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class InGameControl(
    private val context: Context,
    private val wm: WindowManager,
    private val onChanged: (BrainConfig) -> Unit
) {
    private var brainButton: TextView? = null
    private var dodgeButton: TextView? = null
    private var panel: LinearLayout? = null

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    fun show() {
        if (brainButton != null || dodgeButton != null) return

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(48)
        }
        wm.addView(brainButton, brainLp)

        // Independent one-tap switch for use during an active match.
        dodgeButton = TextView(context).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(7), 0, dp(7), 0)
            setOnClickListener { toggleDodgeInstant() }
        }

        val dodgeLp = WindowManager.LayoutParams(
            dp(82), dp(42),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(108)
        }
        wm.addView(dodgeButton, dodgeLp)
        refreshDodgeButton(BrainPrefs.load(context).autoDodge)
    }

    private fun toggleDodgeInstant() {
        val current = BrainPrefs.load(context)
        val enable = !current.autoDodge

        if (enable && DodgeAccessibilityService.instance == null) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {}
            Toast.makeText(
                context,
                "Открой BrawlBrain Dodge в Спец. возможностях",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val updated = current.copy(
            autoDodge = enable,
            dodgeStrengthPercent = current.dodgeStrengthPercent.coerceIn(35, 100),
            dodgeReactionMs = current.dodgeReactionMs.coerceIn(45L, 250L),
            dodgeCooldownMs = current.dodgeCooldownMs.coerceIn(60L, 260L)
        )

        BrainPrefs.save(context, updated)
        onChanged(updated)
        refreshDodgeButton(enable)

        Toast.makeText(
            context,
            if (enable) "⚡ DODGE ON" else "DODGE OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun refreshDodgeButton(enabled: Boolean) {
        dodgeButton?.apply {
            text = if (enabled) "⚡ DODGE" else "DODGE"
            background = if (enabled) {
                bg(0xEE1D6B52.toInt(), 16)
            } else {
                bg(0xEE202633.toInt(), 16)
            }
        }
    }

    private fun openPanel() {
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
            text = "Только движение • атака не используется"
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

        val dodge = Switch(context).apply {
            text = "DODGE ONLY"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = cfg.autoDodge
        }
        root.addView(dodge)

        val strengthText = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF7F8CA5.toInt())
        }
        val strength = SeekBar(context).apply {
            max = 65
            progress = (cfg.dodgeStrengthPercent - 35).coerceIn(0, 65)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    strengthText.text = "Сила манса: \${35 + p}%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
        strengthText.text = "Сила манса: \${cfg.dodgeStrengthPercent}%"
        root.addView(strengthText)
        root.addView(strength)

        val target = Switch(context).apply {
            text = "Приоритетная цель"
            setTextColor(Color.WHITE)
            isChecked = cfg.showTargetLine
        }
        root.addView(target)

        val enemies = Switch(context).apply {
            text = "Метки врагов"
            setTextColor(Color.WHITE)
            isChecked = cfg.showEnemies
        }
        root.addView(enemies)

        root.addView(Button(context).apply {
            text = "ПРИМЕНИТЬ"
            setTextColor(Color.WHITE)
            background = bg(0xFF5B80FF.toInt(), 17)
            setOnClickListener {
                val updated = cfg.copy(
                    brawler = brawler.selectedItem.toString(),
                    autoDodge = dodge.isChecked,
                    dodgeStrengthPercent = 35 + strength.progress,
                    showTargetLine = target.isChecked,
                    showEnemies = enemies.isChecked
                )

                BrainPrefs.save(context, updated)
                onChanged(updated)

                if (updated.autoDodge && DodgeAccessibilityService.instance == null) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Throwable) {}
                    Toast.makeText(context, "Включи BrawlBrain Dodge в Спец. возможностях", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, if (updated.autoDodge) "Dodge Only включён" else "Dodge Only выключен", Toast.LENGTH_SHORT).show()
                }
                closePanel()
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
            dp(310), dp(500),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(160)
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

        dodgeButton?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        dodgeButton = null
    }

    private fun label(text: String, spinner: Spinner): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                this.text = text
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