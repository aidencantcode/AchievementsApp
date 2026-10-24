package app.achievements

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import kotlinx.coroutines.*

class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val prefs by lazy { Prefs(this) }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("watch", "Background watching", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel("unlock", "Achievements", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, "watch")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Watching for achievements").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (job == null) job = scope.launch { watch() }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private suspend fun watch() {
        val p = Prefs(this)
        val known = HashMap<String, Set<String>>()
        val stamp = HashMap<String, Long>()
        var games = emptyList<Game>()
        var last = 0L
        while (currentCoroutineContext().isActive) {
            try {
                if (System.currentTimeMillis() - last > 30000) {
                    games = Scanner.list(this, p); last = System.currentTimeMillis()
                }
                for (g in games) {
                    val k = g.dir.name ?: continue
                    val pf = g.progress
                    if (pf == null) { known.putIfAbsent(k, emptySet()); continue }
                    val t = pf.lastModified()
                    if (stamp[k] == t) continue
                    val e = Scanner.progress(this, pf) ?: continue
                    stamp[k] = t
                    val old = known[k]
                    known[k] = e.keys
                    if (old != null) for (a in g.achs) if (a.id in e.keys && a.id !in old) unlocked(g, a)
                }
            } catch (e: Exception) { }
            delay(2000)
        }
    }

    private fun unlocked(g: Game, a: Ach) {
        val bmp = a.icon?.let {
            try { contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } } catch (e: Exception) { null }
        }
        if (Settings.canDrawOverlays(this)) {
            Popup.show(applicationContext, prefs, a.name, a.desc, g.title, bmp)
            return
        }
        val b = Notification.Builder(this, "unlock")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Achievement unlocked")
            .setContentText(a.name)
            .setSubText(g.title)
            .setStyle(Notification.BigTextStyle().bigText(a.name + "\n" + a.desc))
            .setTimeoutAfter(7000)
            .setAutoCancel(true)
        if (bmp != null) b.setLargeIcon(bmp)
        getSystemService(NotificationManager::class.java)
            .notify((System.nanoTime() % 100000).toInt() + 10, b.build())
    }
}
