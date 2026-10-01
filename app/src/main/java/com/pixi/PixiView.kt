package com.pixi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * PixiView — companion 2D flutuante.
 * Moods: IDLE, ALERT, HAPPY, ANNOYED, DIZZY, DRAG, DOWNLOAD, SLEEP, CHARGE
 * Sprites em assets/sprites/{mood}.png (faixa horizontal 64x64 frames).
 * Sem sprite → desenho procedural soft-cartoon.
 */
class PixiView(ctx: Context) : View(ctx) {
    enum class Mood { IDLE, ALERT, HAPPY, ANNOYED, DIZZY, DRAG, DOWNLOAD, SLEEP, CHARGE }

    var mood = Mood.IDLE
        set(v) {
            if (field != v) {
                field = v
                moodStart = SystemClock.uptimeMillis()
            }
        }
    var progress = -1 // 0..100 download, -1 indeterminado
    private var moodStart = 0L

    private val accent = if (Build.VERSION.SDK_INT >= 31)
        ctx.getColor(android.R.color.system_accent1_300) else Color.rgb(120, 180, 255)
    private val accentStrong = if (Build.VERSION.SDK_INT >= 31)
        ctx.getColor(android.R.color.system_accent1_600) else Color.rgb(70, 110, 220)

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pupil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 35, 50) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 50, 70); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 128, 128, 128); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentStrong; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = accentStrong
    }
    private val cheek = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 160, 170)
    }
    private val spritePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }

    private val sheets = HashMap<Mood, Bitmap>()
    private val missing = HashSet<Mood>()
    private val oneShot = setOf(Mood.HAPPY, Mood.ANNOYED, Mood.DIZZY)

    private fun sheet(m: Mood): Bitmap? {
        sheets[m]?.let { return it }
        if (m in missing) return null
        val b = try {
            context.assets.open("sprites/${m.name.lowercase()}.png").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) { null }
        if (b == null) missing.add(m) else sheets[m] = b
        return b
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.uptimeMillis()
        val dt = (now - moodStart) / 1000f
        val t = now / 1000f

        var dx = 0f
        var dy = 0f
        var sy = 1f
        when (mood) {
            Mood.ALERT -> {
                dx = sin(t * 18f) * w * 0.06f
                dy = abs(sin(t * 12f)) * h * 0.03f
            }
            Mood.HAPPY -> {
                dy = -abs(sin(min(dt * 6f, 3.14f))) * h * 0.22f
                sy = 1f + 0.08f * abs(sin(min(dt * 6f, 3.14f)))
            }
            Mood.DRAG -> sy = 0.88f
            Mood.DIZZY -> {
                dx = sin(t * 10f) * w * 0.08f
                dy = kotlin.math.cos(t * 8f) * h * 0.04f
            }
            Mood.SLEEP -> dy = 2f + abs(sin(t * 1.2f)) * 2f
            Mood.CHARGE -> {
                dy = -abs(sin(t * 4f)) * h * 0.08f
                sy = 1f + 0.05f * abs(sin(t * 4f))
            }
            Mood.DOWNLOAD -> dy = sin(t * 3f) * h * 0.02f
            else -> sy = 1f + 0.03f * sin(t * 2.2f)
        }

        val cx = w / 2f + dx
        val bottom = h * 0.88f + dy
        var topY: Float

        val own = sheet(mood)
        val sp = own ?: sheet(Mood.IDLE)
        if (sp != null) {
            val fh = sp.height
            val frames = max(1, sp.width / fh)
            val i = if (own != null && mood in oneShot) {
                min(frames - 1, (dt * 10).toInt())
            } else {
                ((now / 110) % frames).toInt()
            }
            val side = min(w, h) * 0.88f
            val dest = RectF(cx - side / 2f, bottom - side * sy, cx + side / 2f, bottom)
            c.drawBitmap(sp, Rect(i * fh, 0, (i + 1) * fh, fh), dest, spritePaint)
            topY = dest.top
        } else {
            body.color = when (mood) {
                Mood.ALERT -> Color.rgb(255, 170, 100)
                Mood.ANNOYED -> Color.rgb(220, 150, 190)
                Mood.DIZZY -> Color.rgb(160, 230, 160)
                Mood.CHARGE -> Color.rgb(255, 220, 100)
                Mood.SLEEP -> Color.rgb(100, 140, 200)
                Mood.HAPPY -> Color.rgb(140, 220, 180)
                else -> accent
            }
            val s = 1f + 0.03f * sin(t * 2f)
            val bw = w * 0.72f * s * (if (mood == Mood.DRAG) 1.12f else 1f)
            val bh = h * 0.58f * (2f - s) * sy * (if (mood == Mood.DRAG) 0.9f else 1f)
            val rect = RectF(cx - bw / 2f, bottom - bh, cx + bw / 2f, bottom)
            c.drawRoundRect(rect, bw * 0.38f, bw * 0.38f, body)
            stroke.strokeWidth = 2.5f * (w / 80f)
            c.drawRoundRect(rect, bw * 0.38f, bw * 0.38f, stroke)
            topY = rect.top

            if (mood in setOf(Mood.IDLE, Mood.HAPPY, Mood.CHARGE, Mood.DOWNLOAD)) {
                val cr = bw * 0.12f
                c.drawCircle(cx - bw * 0.28f, rect.top + bh * 0.62f, cr, cheek)
                c.drawCircle(cx + bw * 0.28f, rect.top + bh * 0.62f, cr, cheek)
            }

            val blink = (now % 4200) < 140
            val er = bw * if (mood == Mood.ALERT || mood == Mood.DRAG) 0.15f else 0.11f
            val ey = rect.top + bh * 0.40f
            stroke.strokeWidth = er * 0.35f
            for (sign in intArrayOf(-1, 1)) {
                val ex = cx + sign * bw * 0.22f
                when {
                    mood == Mood.HAPPY || mood == Mood.CHARGE ->
                        c.drawArc(ex - er, ey - er, ex + er, ey + er, 200f, 140f, false, stroke)
                    mood == Mood.ANNOYED ->
                        c.drawLine(ex - er, ey - sign * er * 0.45f, ex + er, ey + sign * er * 0.45f, stroke)
                    mood == Mood.DIZZY -> {
                        c.drawLine(ex - er, ey - er, ex + er, ey + er, stroke)
                        c.drawLine(ex - er, ey + er, ex + er, ey - er, stroke)
                    }
                    mood == Mood.SLEEP || blink ->
                        c.drawLine(ex - er, ey, ex + er, ey, stroke)
                    else -> {
                        val look = when (mood) {
                            Mood.DOWNLOAD -> er * 0.4f
                            Mood.DRAG -> -er * 0.15f
                            else -> 0f
                        }
                        c.drawCircle(ex, ey, er, eye)
                        c.drawCircle(ex, ey + look, er * 0.48f, pupil)
                    }
                }
            }
        }

        if (mood == Mood.SLEEP) {
            txt.textSize = h * 0.18f
            val zx = cx + w * 0.28f
            val zy = topY + h * 0.10f - abs(sin(t * 1.5f)) * h * 0.06f
            c.drawText("z", zx, zy, txt)
            txt.textSize = h * 0.12f
            c.drawText("z", zx + w * 0.08f, zy - h * 0.08f, txt)
        }
        if (mood == Mood.CHARGE) {
            txt.textSize = h * 0.22f
            c.drawText("⚡", cx + w * 0.28f, topY + h * 0.14f, txt)
        }
        if (mood == Mood.DOWNLOAD) {
            val bl = w * 0.12f
            val br = w * 0.88f
            val by = h * 0.96f
            track.strokeWidth = h * 0.055f
            fill.strokeWidth = h * 0.055f
            c.drawLine(bl, by, br, by, track)
            if (progress in 0..100) {
                c.drawLine(bl, by, bl + (br - bl) * progress / 100f, by, fill)
            } else {
                val s0 = bl + (br - bl) * ((t * 1.3f) % 1f) * 0.65f
                c.drawLine(s0, by, s0 + (br - bl) * 0.28f, by, fill)
            }
        }

        postInvalidateOnAnimation()
    }
}
