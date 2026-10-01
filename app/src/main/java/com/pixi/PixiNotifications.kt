package com.pixi

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Detecta downloads (notificações com progresso) e avisa o Pixi. */
class PixiNotifications : NotificationListenerService() {
    private val tracked = HashSet<String>()
    private val known = setOf(
        "com.android.providers.downloads", "com.android.providers.downloads.ui",
        "com.android.chrome", "org.mozilla.firefox", "com.brave.browser",
        "com.google.android.apps.nbu.files", "com.android.vending"
    )
    private val dlWords = listOf("download", "baixa", "transfer", "descarreg")
    private val doneWords = listOf("complet", "conclu", "finaliz", "baixad", "downloaded")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val x = sbn.notification.extras ?: return
        val max = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val cur = x.getInt(Notification.EXTRA_PROGRESS, 0)
        val indet = x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val txt = ((x.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString() + " " +
            (x.getCharSequence(Notification.EXTRA_TEXT) ?: "")).lowercase()
        val fromKnown = sbn.packageName in known
        val mentionsDl = dlWords.any { txt.contains(it) }
        when {
            (max > 0 || indet) && (fromKnown || mentionsDl) -> {
                tracked.add(sbn.key)
                PixiBus.post("download", if (indet || max <= 0) -1 else cur * 100 / max)
            }
            fromKnown && mentionsDl && doneWords.any { txt.contains(it) } -> PixiBus.post("done")
            !sbn.isOngoing -> PixiBus.post("notify")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (tracked.remove(sbn.key)) PixiBus.post("done")
    }
}
