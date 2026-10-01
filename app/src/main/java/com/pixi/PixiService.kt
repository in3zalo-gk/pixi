package com.pixi

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * PixiService
 * - Dynamic Island (barra/pílula no topo) via IslandView
 * - Pixi (bichinho quadrado) livre na tela, pode grudar na ilha
 */
class PixiService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences

    // overlay do Pixi (personagem)
    private lateinit var pixiLp: WindowManager.LayoutParams
    private var pixiRoot: LinearLayout? = null
    private lateinit var pixi: PixiView
    private lateinit var panel: LinearLayout
    private lateinit var msg: TextView

    // overlay da Dynamic Island (barra)
    private lateinit var islandLp: WindowManager.LayoutParams
    private lateinit var island: IslandView

    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())
    private val answers = LinkedBlockingQueue<String>()
    private val clicks = ArrayDeque<Long>()

    private var lastInteract = 0L
    private var dragging = false
    private var oneShotRunning = false
    private var inIsland = false

    private val sleepTask = Runnable {
        if (prefs.getBoolean("sleep", true) &&
            pixi.mood == PixiView.Mood.IDLE &&
            !dragging &&
            SystemClock.uptimeMillis() - lastInteract > sleepMs()
        ) {
            pixi.mood = PixiView.Mood.SLEEP
        }
    }

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> PixiBus.post("charge")
                Intent.ACTION_BATTERY_LOW -> PixiBus.post("alert")
                Intent.ACTION_BATTERY_OKAY -> if (pixi.mood == PixiView.Mood.ALERT) PixiBus.post("idle")
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "size" -> applySize()
            "sleep" -> scheduleSleep()
            "island" -> updateIslandVisibility()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun sleepMs() = prefs.getInt("sleep_sec", 45) * 1000L

    private fun screen(): IntArray =
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            intArrayOf(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(m)
            intArrayOf(m.widthPixels, m.heightPixels)
        }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("pixi", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("pixi", "Pixi", NotificationManager.IMPORTANCE_MIN)
        )
        val n: Notification = Notification.Builder(this, "pixi")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pixi + Dynamic Island")
            .setContentText("Companion e barra ativos")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // ---- Dynamic Island (barra no topo) ----
        island = IslandView(this)
        islandLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(56),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(6)
        }
        island.setOnClickListener {
            if (island.expanded) {
                island.collapse()
            } else {
                island.expanded = true
                island.invalidate()
            }
            syncPixiWithIsland()
        }
        if (prefs.getBoolean("island", true)) {
            wm.addView(island, islandLp)
            island.show(IslandView.Mode.IDLE)
        }

        // ---- Pixi (personagem) ----
        pixi = PixiView(this)
        pixi.setOnTouchListener(dragListener())

        msg = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            maxWidth = dp(240)
        }
        fun btn(label: String, answer: String) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener {
                answers.offer(answer)
                hidePanel()
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(btn("Permitir", "allow"))
            addView(btn("Negar", "deny"))
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(230, 30, 30, 45))
                cornerRadius = dp(16).toFloat()
            }
            visibility = View.GONE
            addView(msg)
            addView(row)
        }

        pixiRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(pixi)
            addView(panel)
        }

        pixiLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(24))
            y = prefs.getInt("y", dp(64))
        }

        applySize()
        wm.addView(pixiRoot, pixiLp)

        PixiBus.sink = { type, value -> main.post { onEvent(type, value) } }
        registerReceiver(power, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        })
        startServer()
        lastInteract = SystemClock.uptimeMillis()
        scheduleSleep()

        try {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 1..15) PixiBus.post("alert")
        } catch (_: Exception) {}
    }

    private fun updateIslandVisibility() {
        val show = prefs.getBoolean("island", true)
        try {
            if (show) {
                if (island.parent == null) {
                    wm.addView(island, islandLp)
                    island.show(IslandView.Mode.IDLE)
                }
            } else {
                if (island.parent != null) wm.removeView(island)
            }
        } catch (_: Exception) {}
    }

    private fun scheduleSleep() {
        main.removeCallbacks(sleepTask)
        if (prefs.getBoolean("sleep", true)) {
            main.postDelayed(sleepTask, sleepMs())
        }
    }

    private fun wake() {
        lastInteract = SystemClock.uptimeMillis()
        if (pixi.mood == PixiView.Mood.SLEEP) {
            setMood(PixiView.Mood.IDLE)
        }
        scheduleSleep()
    }

    private fun setMood(m: PixiView.Mood, progress: Int = -1) {
        pixi.progress = progress
        pixi.mood = m
        oneShotRunning = m in setOf(PixiView.Mood.HAPPY, PixiView.Mood.ANNOYED, PixiView.Mood.DIZZY)
        if (oneShotRunning) {
            main.postDelayed({
                if (pixi.mood == m) {
                    pixi.mood = PixiView.Mood.IDLE
                    oneShotRunning = false
                }
            }, 900)
        }
        if (m != PixiView.Mood.SLEEP) scheduleSleep()
    }

    private fun onEvent(type: String, value: Int) {
        wake()
        when (type.lowercase()) {
            "alert", "notify" -> {
                setMood(PixiView.Mood.ALERT)
                island.show(IslandView.Mode.NOTIFY, "Notificação")
                syncPixiWithIsland()
                main.postDelayed({
                    if (island.mode == IslandView.Mode.NOTIFY) { island.collapse(); syncPixiWithIsland() }
                    if (pixi.mood == PixiView.Mood.ALERT) setMood(PixiView.Mood.IDLE)
                }, 4500)
            }
            "done", "happy" -> {
                setMood(PixiView.Mood.HAPPY)
                island.show(IslandView.Mode.NOTIFY, "Concluído ✓")
                syncPixiWithIsland()
                main.postDelayed({ island.collapse(); syncPixiWithIsland() }, 2500)
            }
            "idle" -> {
                setMood(PixiView.Mood.IDLE)
                island.collapse()
                syncPixiWithIsland()
            }
            "sleep" -> setMood(PixiView.Mood.SLEEP)
            "charge" -> {
                setMood(PixiView.Mood.CHARGE)
                island.show(IslandView.Mode.CHARGE, "Carregando")
                syncPixiWithIsland()
                main.postDelayed({
                    if (island.mode == IslandView.Mode.CHARGE) { island.collapse(); syncPixiWithIsland() }
                    if (pixi.mood == PixiView.Mood.CHARGE) setMood(PixiView.Mood.IDLE)
                }, 8000)
            }
            "download" -> {
                setMood(PixiView.Mood.DOWNLOAD, value)
                val pct = if (value in 0..100) "$value%" else "…"
                island.show(IslandView.Mode.DOWNLOAD, "Baixando $pct", value)
                syncPixiWithIsland()
            }
            "annoyed" -> setMood(PixiView.Mood.ANNOYED)
            "dizzy" -> setMood(PixiView.Mood.DIZZY)
            "drag" -> setMood(PixiView.Mood.DRAG)
            else -> {}
        }
    }

    private fun dragListener() = View.OnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                wake()
                dragging = true
                // ao puxar de dentro da ilha, volta a ser livre e visível
                if (inIsland) {
                    inIsland = false
                    pixi.visibility = View.VISIBLE
                    pixiRoot?.visibility = View.VISIBLE
                }
                setMood(PixiView.Mood.DRAG)
                v.tag = floatArrayOf(e.rawX, e.rawY, pixiLp.x.toFloat(), pixiLp.y.toFloat())
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val t = v.tag as? FloatArray ?: return@OnTouchListener false
                val dx = e.rawX - t[0]
                val dy = e.rawY - t[1]
                pixiLp.x = (t[2] + dx).toInt()
                pixiLp.y = (t[3] + dy).toInt()
                clampPixi()
                wm.updateViewLayout(pixiRoot, pixiLp)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                val t = v.tag as? FloatArray
                val moved = if (t != null) {
                    hypot((e.rawX - t[0]).toDouble(), (e.rawY - t[1]).toDouble()) >
                        ViewConfiguration.get(this).scaledTouchSlop
                } else false
                if (!moved) {
                    onPoke()
                } else {
                    if (prefs.getBoolean("snap_island", true)) {
                        maybeSnapIsland()
                    }
                    setMood(PixiView.Mood.IDLE)
                    savePos()
                }
                true
            }
            else -> false
        }
    }

    private fun onPoke() {
        val now = SystemClock.uptimeMillis()
        clicks.addLast(now)
        while (clicks.isNotEmpty() && now - clicks.first() > 800) clicks.removeFirst()
        when (clicks.size) {
            1 -> setMood(PixiView.Mood.HAPPY)
            2 -> setMood(PixiView.Mood.DIZZY)
            else -> {
                setMood(PixiView.Mood.ANNOYED)
                clicks.clear()
            }
        }
    }

    /** Soltar perto do topo central → gruda na Dynamic Island */
    private fun maybeSnapIsland() {
        val (sw, _) = screen()
        val size = prefs.getInt("size", 80)
        val px = dp(size)
        val islandY = dp(8)
        val islandX = (sw - px) / 2
        if (pixiLp.y < dp(72) && kotlin.math.abs(pixiLp.x - islandX) < sw * 0.30f) {
            val anim = ValueAnimator.ofFloat(0f, 1f).setDuration(220)
            val sx = pixiLp.x
            val sy = pixiLp.y
            anim.addUpdateListener {
                val f = it.animatedValue as Float
                pixiLp.x = (sx + (islandX - sx) * f).toInt()
                pixiLp.y = (sy + (islandY - sy) * f).toInt()
                try { wm.updateViewLayout(pixiRoot, pixiLp) } catch (_: Exception) {}
            }
            anim.start()
            pixiLp.x = islandX
            pixiLp.y = islandY
            inIsland = true
            // Dentro do notch: some no estado colapsado; só aparece quando a ilha expande
            pixi.visibility = View.GONE
            island.show(IslandView.Mode.IDLE)
            island.expanded = false
        }
    }

    /** Pixi no notch só aparece com a ilha expandida */
    private fun syncPixiWithIsland() {
        if (!inIsland) {
            pixi.visibility = View.VISIBLE
            return
        }
        pixi.visibility = if (island.expanded) View.VISIBLE else View.GONE
    }

    private fun clampPixi() {
        val (sw, sh) = screen()
        val size = prefs.getInt("size", 80)
        val px = dp(size)
        pixiLp.x = max(-px / 3, min(pixiLp.x, sw - px * 2 / 3))
        pixiLp.y = max(0, min(pixiLp.y, sh - px - dp(24)))
    }

    private fun savePos() {
        prefs.edit().putInt("x", pixiLp.x).putInt("y", pixiLp.y).apply()
    }

    private fun applySize() {
        val size = prefs.getInt("size", 80).coerceIn(48, 140)
        val px = dp(size)
        pixi.layoutParams = LinearLayout.LayoutParams(px, px)
        pixiRoot?.post { clampPixi() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pixiRoot?.post { clampPixi() }
    }

    private fun hidePanel() {
        panel.visibility = View.GONE
        setMood(PixiView.Mood.IDLE)
        pixiRoot?.post { clampPixi() }
    }

    private fun startServer() = thread(isDaemon = true) {
        try {
            val ss = ServerSocket(8765, 20, InetAddress.getByName("127.0.0.1"))
            server = ss
            while (!ss.isClosed) {
                val s = ss.accept()
                thread(isDaemon = true) { handle(s) }
            }
        } catch (_: Exception) {
        }
    }

    private fun handle(s: Socket) {
        try {
            val line = s.getInputStream().bufferedReader().readLine() ?: ""
            var body = "ok"
            if (line.contains("/ask")) {
                val text = Regex("msg=([^& ]*)").find(line)?.groupValues?.get(1)
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Pixi precisa de você"
                answers.clear()
                main.post {
                    msg.text = text
                    panel.visibility = View.VISIBLE
                    setMood(PixiView.Mood.ALERT)
                    island.show(IslandView.Mode.NOTIFY, text.take(24))
                    pixiRoot?.post { clampPixi() }
                }
                body = answers.poll(60, TimeUnit.SECONDS) ?: "timeout"
                if (body == "timeout") main.post { hidePanel() }
            } else {
                val type = Regex("type=(\\w+)").find(line)?.groupValues?.get(1)
                val value = Regex("value=(\\d+)").find(line)?.groupValues?.get(1)?.toInt() ?: -1
                if (type != null) PixiBus.post(type, value)
            }
            s.getOutputStream().write(
                "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray()
            )
        } catch (_: Exception) {
        } finally {
            s.close()
        }
    }

    override fun onDestroy() {
        PixiBus.sink = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        try { unregisterReceiver(power) } catch (_: Exception) {}
        main.removeCallbacks(sleepTask)
        server?.close()
        try { if (island.parent != null) wm.removeView(island) } catch (_: Exception) {}
        pixiRoot?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}
