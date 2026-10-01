package com.pixi

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.util.Linkify
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setPadding(0, dp(8), 0, dp(6))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        autoLinkMask = Linkify.WEB_URLS
    }

    private fun sw(title: String, key: String, def: Boolean) = Switch(this).apply {
        text = title
        textSize = 16f
        setPadding(0, dp(10), 0, dp(10))
        isChecked = prefs.getBoolean(key, def)
        setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun simulateDownload() = thread {
        for (p in 0..100 step 5) {
            PixiBus.post("download", p)
            Thread.sleep(120)
        }
        PixiBus.post("done")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pixi", MODE_PRIVATE)
        status = text("", 15f)

        val sizeLabel = text("Tamanho: ${prefs.getInt("size", 80)} dp", 16f)
        val seek = SeekBar(this).apply {
            max = 92
            progress = prefs.getInt("size", 80) - 48
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    sizeLabel.text = "Tamanho: ${p + 48} dp"
                    if (fromUser) prefs.edit().putInt("size", p + 48).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }

        val sleepLabel = text("Dormir após: ${prefs.getInt("sleep_sec", 45)} s", 16f)
        val sleepSeek = SeekBar(this).apply {
            max = 170
            progress = prefs.getInt("sleep_sec", 45) - 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    val sec = p + 10
                    sleepLabel.text = "Dormir após: $sec s"
                    if (fromUser) prefs.edit().putInt("sleep_sec", sec).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }

        fun row(vararg buttons: Button) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEach { addView(it) }
        }
        fun b(label: String, action: () -> Unit) = btn(label, action)

        val tests = row(
            b("Alerta") { PixiBus.post("alert") },
            b("Feliz") { PixiBus.post("done") },
            b("Download") { simulateDownload() }
        )
        val tests2 = row(
            b("Dormir") { PixiBus.post("sleep") },
            b("Carregar") { PixiBus.post("charge") },
            b("Normal") { PixiBus.post("idle") }
        )
        val tests3 = row(
            b("Irritado") { PixiBus.post("annoyed") },
            b("Tonto") { PixiBus.post("dizzy") }
        )

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
            addView(text("Pixi", 34f, true))
            addView(status)
            addView(btn("Permitir sobreposição (aparecer na tela)") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            addView(btn("Acesso a notificações (animação de download)") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })

            addView(text("Ajustes", 20f, true))
            addView(sizeLabel)
            addView(seek)
            addView(sleepLabel)
            addView(sleepSeek)
            addView(sw("Dynamic Island (barra no topo)", "island", true))
            addView(sw("Snap Pixi para a ilha", "snap_island", true))
            addView(sw("Dormir quando ficar parado", "sleep", true))
            addView(sw("Reagir a notificações", "notif", true))

            addView(text("Testar animações", 20f, true))
            addView(tests)
            addView(tests2)
            addView(tests3)

            addView(text(
                "• Dynamic Island: pílula preta no topo (expande em download/alerta/carga).\n" +
                "• Pixi: bichinho quadrado — arraste para qualquer lugar.\n" +
                "• Solte perto do topo central para grudar na ilha.\n" +
                "• Toque: feliz · 2 toques: tonto · 3 toques: irritado.\n" +
                "• Sem mexer → dorme. Mexeu → acorda.\n" +
                "• Download → barra na Island + Pixi em modo download.\n" +
                "• Carregador → ⚡ na Island e no Pixi.", 14f
            ))

            addView(text("API local (Termux / scripts)", 18f, true))
            addView(text(
                "curl \"http://127.0.0.1:8765/event?type=download&value=40\"\n" +
                "curl \"http://127.0.0.1:8765/event?type=alert\"\n" +
                "curl \"http://127.0.0.1:8765/event?type=done\"\n" +
                "curl \"http://127.0.0.1:8765/ask?msg=Posso+rodar+isso\"\n" +
                "Tipos: alert, done, idle, sleep, charge, download, annoyed, dizzy", 13f
            ))

            addView(text("Créditos", 20f, true))
            addView(text(
                "Inspirado no Coucou, criado por Louis Raillé (licença MIT):\n" +
                "https://github.com/louis-cfm/coucou\n\n" +
                "O personagem Mochi, o nome, o ícone e os sons do Coucou pertencem ao autor e NÃO são usados aqui.\n" +
                "Pixi é um personagem original com sprites cartoon soft gerados para este projeto.\n" +
                "Ideias de interações de Dynamic Island inspiradas em projetos open-source como SmartIsland (agupta07505).\n\n" +
                "Sprites: assets/sprites/ (idle, happy, alert, annoyed, dizzy, drag, download, sleep, charge).", 14f
            ))
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        val overlay = Settings.canDrawOverlays(this)
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        status.text = (if (overlay) "✔ Sobreposição permitida — Pixi ativo" else "✖ Falta permitir a sobreposição") + "\n" +
            (if (listener) "✔ Acesso a notificações ligado" else "✖ Sem acesso a notificações (sem animação de download)")
        if (overlay) startForegroundService(Intent(this, PixiService::class.java))
    }
}
