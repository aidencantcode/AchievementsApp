package app.achievements

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

// Navigation sounds: move, select, back, unlock. Defaults are bundled; the user can import their own files per sound.
object Sfx {
    @Volatile var on = true
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()

    fun init(c: Context) {
        if (pool != null) return
        val ctx = c.applicationContext
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        on = Prefs(ctx).sfxOn
        reload(ctx)
    }

    fun reload(c: Context) {
        val ctx = c.applicationContext
        val p = pool ?: return
        ids.values.forEach { p.unload(it) }
        ids.clear()
        for (n in listOf("move", "select", "back", "unlock")) {
            val f = File(ctx.filesDir, "sfx/$n")
            try {
                ids[n] = if (f.exists()) p.load(f.path, 1) else p.load(ctx.assets.openFd("sfx/$n.wav"), 1)
            } catch (e: Exception) { }
        }
    }

    fun play(n: String) {
        if (!on) return
        ids[n]?.let { pool?.play(it, 1f, 1f, 1, 0, 1f) }
    }
}
