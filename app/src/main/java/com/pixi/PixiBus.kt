package com.pixi

import android.os.Handler
import android.os.Looper

/** Ponte entre fontes de eventos (HTTP, notificações, testes) e o Pixi. */
object PixiBus {
    // tipos: alert, done, idle, download (value = 0..100 ou -1), sleep, charge, notify
    var sink: ((String, Int) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    fun post(type: String, value: Int = -1) { main.post { sink?.invoke(type, value) } }
}
