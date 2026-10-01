package com.pixi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * Dynamic Island / Dynamic Bar — pílula no topo que expande com eventos.
 * Estados: collapsed (pílula fina) | expanded (barra com texto/progresso)
 */
class IslandView(ctx: Context) : View(ctx) {
    enum class Mode { HIDDEN, IDLE, NOTIFY, DOWNLOAD, CHARGE, CALL, MUSIC }

    var mode = Mode.IDLE
        set(v) {
            field = v
            modeStart = SystemClock.uptimeMillis()
            invalidate()
        }
    var label: String = ""
    var progress: Int = -1 // 0..100 download
    var expanded: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private var modeStart = 0L

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(18, 18, 22)
        style = Paint.Style.FILL
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 180, 255)
        style = Paint.Style.FILL
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 200, 120)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 200, 200, 210)
        textAlign = Paint.Align.CENTER
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 120, 180, 255)
        style = Paint.Style.FILL
    }

    fun show(m: Mode, text: String = "", prog: Int = -1) {
        mode = m
        label = text
        progress = prog
        expanded = m != Mode.IDLE && m != Mode.HIDDEN
        visibility = if (m == Mode.HIDDEN) GONE else VISIBLE
        invalidate()
    }

    fun collapse() {
        expanded = false
        mode = Mode.IDLE
        label = ""
        progress = -1
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (mode == Mode.HIDDEN) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val now = SystemClock.uptimeMillis()
        val t = now / 1000f

        // largura da pílula
        val targetW = if (expanded) w * 0.92f else min(w * 0.42f, h * 2.8f)
        val pillH = if (expanded) h * 0.72f else h * 0.55f
        val cx = w / 2f
        val cy = h * 0.45f
        val left = cx - targetW / 2f
        val top = cy - pillH / 2f
        val right = cx + targetW / 2f
        val bottom = cy + pillH / 2f
        val radius = pillH / 2f

        // glow suave quando expandido
        if (expanded) {
            c.drawRoundRect(RectF(left - 4, top - 3, right + 4, bottom + 3), radius + 4, radius + 4, glow)
        }

        // pílula preta (Dynamic Island)
        c.drawRoundRect(RectF(left, top, right, bottom), radius, radius, pill)

        // indicador de modo (bolinha colorida à esquerda quando expandido)
        when (mode) {
            Mode.DOWNLOAD -> {
                accent.color = Color.rgb(100, 180, 255)
                if (expanded) {
                    c.drawCircle(left + radius * 0.9f, cy, radius * 0.28f, accent)
                    txt.textSize = h * 0.22f
                    c.drawText(if (label.isNotEmpty()) label else "Download", cx + radius * 0.2f, cy + h * 0.07f, txt)
                    // barra de progresso
                    val bl = left + radius * 1.6f
                    val br = right - radius * 0.6f
                    val by = bottom - pillH * 0.22f
                    track.strokeWidth = h * 0.08f
                    fill.strokeWidth = h * 0.08f
                    c.drawLine(bl, by, br, by, track)
                    if (progress in 0..100) {
                        c.drawLine(bl, by, bl + (br - bl) * progress / 100f, by, fill)
                    } else {
                        val s0 = bl + (br - bl) * ((t * 1.2f) % 1f) * 0.6f
                        c.drawLine(s0, by, s0 + (br - bl) * 0.25f, by, fill)
                    }
                } else {
                    // collapsed: pontinho + onda
                    c.drawCircle(cx - 8, cy, 4f, accent)
                    c.drawCircle(cx + 8, cy, 4f + 1.5f * sin(t * 6f).toFloat(), accent)
                }
            }
            Mode.CHARGE -> {
                accent.color = Color.rgb(100, 220, 120)
                if (expanded) {
                    txt.textSize = h * 0.28f
                    c.drawText("⚡", left + radius * 0.85f, cy + h * 0.10f, txt)
                    txt.textSize = h * 0.22f
                    c.drawText(if (label.isNotEmpty()) label else "Carregando", cx + radius * 0.3f, cy + h * 0.07f, txt)
                } else {
                    txt.textSize = h * 0.28f
                    c.drawText("⚡", cx, cy + h * 0.10f, txt)
                }
            }
            Mode.NOTIFY, Mode.CALL -> {
                accent.color = Color.rgb(255, 160, 90)
                if (expanded) {
                    c.drawCircle(left + radius * 0.9f, cy, radius * 0.28f, accent)
                    txt.textSize = h * 0.20f
                    val t1 = if (label.isNotEmpty()) label else "Notificação"
                    c.drawText(t1.take(28), cx + radius * 0.15f, cy + h * 0.07f, txt)
                } else {
                    // pulse
                    val pulse = 3f + 2f * sin(t * 8f).toFloat()
                    c.drawCircle(cx, cy, pulse, accent)
                }
            }
            Mode.MUSIC -> {
                accent.color = Color.rgb(200, 140, 255)
                if (expanded) {
                    txt.textSize = h * 0.22f
                    c.drawText("♪ " + (if (label.isNotEmpty()) label else "Música"), cx, cy + h * 0.07f, txt)
                } else {
                    txt.textSize = h * 0.24f
                    c.drawText("♪", cx, cy + h * 0.08f, txt)
                }
            }
            Mode.IDLE -> {
                // pílula vazia mínima (só a barra preta)
            }
            else -> {}
        }

        if (expanded || mode != Mode.IDLE) postInvalidateOnAnimation()
    }
}
