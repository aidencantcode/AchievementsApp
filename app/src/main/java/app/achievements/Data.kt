package app.achievements

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }
    var root: String
        get() = sp.getString("root", "") ?: ""
        set(v) { sp.edit().putString("root", v).apply() }
    var key: String
        get() = sp.getString("key", "") ?: ""
        set(v) { sp.edit().putString("key", v).apply() }
    var alerts: Boolean
        get() = sp.getBoolean("alerts", true)
        set(v) { sp.edit().putBoolean("alerts", v).apply() }
}

data class Ach(
    val id: String, val name: String, val desc: String, val hidden: Boolean,
    val icon: Uri?, val earned: Boolean, val time: Long
)

data class Game(
    val dir: DocumentFile, val appId: String, val title: String, val achs: List<Ach>,
    val progress: DocumentFile?, val cover: Uri?, val hero: Uri?
) {
    val earned: Int get() = achs.count { it.earned }
}

object Scanner {
    fun root(c: Context, p: Prefs): DocumentFile? =
        if (p.root.isBlank()) null else DocumentFile.fromTreeUri(c, Uri.parse(p.root))

    fun list(c: Context, p: Prefs): List<Game> =
        (root(c, p)?.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { try { load(c, it) } catch (e: Exception) { null } }
            .sortedBy { it.title.lowercase() }

    fun text(c: Context, d: DocumentFile): String? = try {
        c.contentResolver.openInputStream(d.uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) { null }

    // id -> earned time. Empty map if the file doesn't exist yet, null if unreadable.
    fun progress(c: Context, d: DocumentFile?): Map<String, Long>? {
        if (d == null) return emptyMap()
        val t = text(c, d) ?: return null
        return try {
            val o = JSONObject(t)
            val m = HashMap<String, Long>()
            for (k in o.keys()) {
                val v = o.optJSONObject(k) ?: continue
                if (v.optBoolean("earned")) m[k] = v.optLong("earned_time")
            }
            m
        } catch (e: Exception) { null }
    }

    private fun str(o: JSONObject, k: String): String = when (val v = o.opt(k)) {
        is String -> v
        is JSONObject -> v.optString("english", v.keys().asSequence().firstOrNull()?.let { v.optString(it) } ?: "")
        else -> ""
    }

    private fun load(c: Context, dir: DocumentFile): Game? {
        val kids = dir.listFiles().associateBy { it.name }
        val st = kids["steam_settings"]?.listFiles()?.associateBy { it.name } ?: return null
        val defs = JSONArray(st["achievements.json"]?.let { text(c, it) } ?: return null)
        val imgs = st["images"]?.listFiles()?.associate { (it.name ?: "") to it.uri } ?: emptyMap()
        val progressDoc = kids["achievements.json"]
        val earned = progress(c, progressDoc) ?: emptyMap()
        val achs = (0 until defs.length()).map {
            val o = defs.getJSONObject(it)
            val id = o.optString("name")
            Ach(
                id, str(o, "displayName").ifBlank { id }, str(o, "description"),
                o.opt("hidden")?.toString() in listOf("1", "true"),
                imgs[o.optString("icon").substringAfterLast('/')],
                id in earned, earned[id] ?: 0L
            )
        }
        val folder = dir.name ?: ""
        val m = Regex("^(\\d+)\\s*[-_.]?\\s*(.*)$").find(folder)
        val appId = m?.groupValues?.get(1) ?: ""
        val title = m?.groupValues?.get(2)?.ifBlank { null } ?: folder
        return Game(dir, appId, title, achs, progressDoc, kids["cover.jpg"]?.uri, kids["hero.jpg"]?.uri)
    }
}

object Net {
    private fun get(url: String, key: String? = null): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 20000
        if (key != null) c.setRequestProperty("Authorization", "Bearer $key")
        if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) { null }

    private fun sgdb(path: String, key: String): ByteArray? {
        val j = get("https://www.steamgriddb.com/api/v2/$path", key) ?: return null
        val a = (try { JSONObject(String(j)).optJSONArray("data") } catch (e: Exception) { null }) ?: return null
        if (a.length() == 0) return null
        return get(a.getJSONObject(0).optString("url"))
    }

    private fun save(c: Context, dir: DocumentFile, name: String, bytes: ByteArray) {
        dir.findFile(name)?.delete()
        val f = dir.createFile("image/jpeg", name.removeSuffix(".jpg")) ?: return
        c.contentResolver.openOutputStream(f.uri)?.use { it.write(bytes) }
    }

    // SteamGridDB when a key is set, Steam's public CDN otherwise. Saved inside the game's folder.
    fun art(c: Context, g: Game, key: String) {
        if (g.appId.isBlank()) return
        val cdn = "https://cdn.cloudflare.steamstatic.com/steam/apps/${g.appId}/"
        if (g.cover == null) {
            val b = (if (key.isNotBlank()) sgdb("grids/steam/${g.appId}?dimensions=600x900", key) else null)
                ?: get(cdn + "library_600x900.jpg")
            b?.let { save(c, g.dir, "cover.jpg", it) }
        }
        if (g.hero == null) {
            val b = (if (key.isNotBlank()) sgdb("heroes/steam/${g.appId}", key) else null)
                ?: get(cdn + "library_hero.jpg")
            b?.let { save(c, g.dir, "hero.jpg", it) }
        }
    }
}
