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
    var steamKey: String
        get() = sp.getString("skey", "") ?: ""
        set(v) { sp.edit().putString("skey", v).apply() }
    var gamesRoot: String
        get() = sp.getString("groot", "") ?: ""
        set(v) { sp.edit().putString("groot", v).apply() }
    fun read(k: String): String = sp.getString(k, "") ?: ""
    fun write(k: String, v: String) { sp.edit().putString(k, v).apply() }
    var alerts: Boolean
        get() = sp.getBoolean("alerts", true)
        set(v) { sp.edit().putBoolean("alerts", v).apply() }
    var popupSide: String
        get() = sp.getString("pside", "top") ?: "top"
        set(v) { sp.edit().putString("pside", v).apply() }
    var popupSecs: Int
        get() = sp.getInt("psecs", 4)
        set(v) { sp.edit().putInt("psecs", v).apply() }
}

data class Ach(
    val id: String, val name: String, val desc: String, val hidden: Boolean,
    val icon: Uri?, val earned: Boolean, val time: Long, val percent: Double?
)

data class Game(
    val dir: DocumentFile, val appId: String, val title: String, val achs: List<Ach>,
    val progress: DocumentFile?, val cover: Uri?, val hero: Uri?,
    val metaAt: Long, val metaSchema: Boolean
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
        val icons = kids["icons"]?.listFiles()?.associate { (it.name ?: "") to it.uri } ?: emptyMap()
        val meta = try { kids["meta.json"]?.let { text(c, it) }?.let { JSONObject(it) } } catch (e: Exception) { null }
        val metaAch = meta?.optJSONObject("ach")
        val appId0 = Regex("^(\\d+)").find(dir.name ?: "")?.groupValues?.get(1) ?: ""
        val progressDoc = kids["achievements.json"]
            ?: kids[appId0]?.takeIf { it.isDirectory }?.findFile("achievements.json")
        val earned = progress(c, progressDoc) ?: emptyMap()
        val achs = (0 until defs.length()).map {
            val o = defs.getJSONObject(it)
            val id = o.optString("name")
            val mo = metaAch?.optJSONObject(id)
            val icon = imgs[o.optString("icon").substringAfterLast('/')]
                ?: mo?.optString("i")?.takeIf { s -> s.isNotBlank() }?.let { s -> icons[s] }
            Ach(
                id,
                str(o, "displayName").ifBlank { mo?.optString("n")?.takeIf { s -> s.isNotBlank() } ?: id },
                str(o, "description").ifBlank { mo?.optString("d") ?: "" },
                o.opt("hidden")?.toString() in listOf("1", "true"),
                icon, id in earned, earned[id] ?: 0L,
                mo?.optDouble("p", -1.0)?.takeIf { d -> d >= 0 }
            )
        }
        val folder = dir.name ?: ""
        val m = Regex("^(\\d+)\\s*[-_.]?\\s*(.*)$").find(folder)
        val appId = m?.groupValues?.get(1) ?: ""
        val title = kids["title.txt"]?.let { text(c, it) }?.trim()?.takeIf { it.isNotBlank() }
            ?: m?.groupValues?.get(2)?.ifBlank { null } ?: folder
        return Game(
            dir, appId, title, achs, progressDoc, kids["cover.jpg"]?.uri, kids["hero.jpg"]?.uri,
            meta?.optLong("at") ?: 0L, meta?.optBoolean("s") ?: false
        )
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
        val json = name.endsWith(".json")
        val f = dir.createFile(if (json) "application/json" else "image/jpeg", if (json) name else name.removeSuffix(".jpg")) ?: return
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

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun clean(s: String) = s.replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").trim()

    // Keyless fallback: Steam's public global-stats page lists every achievement with icon, name,
    // description and percentage. Entries are matched to the definitions file by display name.
    private fun page(c: Context, g: Game, ach: JSONObject): Boolean {
        val html = get("https://steamcommunity.com/stats/${g.appId}/achievements?l=english")?.let { String(it) } ?: return false
        val rows = html.split("class=\"achieveRow")
        if (rows.size < 2) return false
        val byName = g.achs.associateBy { norm(it.name) }
        val icons = g.dir.findFile("icons") ?: g.dir.createDirectory("icons")
        var hits = 0
        for (r in rows.drop(1)) {
            val n = Regex("<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL).find(r)?.groupValues?.get(1)?.let { clean(it) } ?: continue
            val a = byName[norm(n)] ?: continue
            val desc = Regex("<h5[^>]*>(.*?)</h5>", RegexOption.DOT_MATCHES_ALL).find(r)?.groupValues?.get(1)?.let { clean(it) } ?: ""
            val pct = Regex("achievePercent[^>]*>\\s*([0-9.]+)%").find(r)?.groupValues?.get(1)?.toDoubleOrNull()
            val img = Regex("<img[^>]+src=\"([^\"]+)\"").find(r)?.groupValues?.get(1)
            val s = ach.optJSONObject(a.id) ?: JSONObject().also { ach.put(a.id, it) }
            if (desc.isNotBlank()) s.put("d", desc)
            if (pct != null && !s.has("p")) s.put("p", pct)
            if (img != null && icons != null) {
                val fn = "icon_" + a.id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".jpg"
                if (icons.findFile(fn) == null) get(img)?.let { bytes -> save(c, icons, fn, bytes) }
                s.put("i", fn)
            }
            hits++
        }
        return hits > 0
    }

    fun needsMeta(g: Game, steamKey: String): Boolean =
        g.appId.isNotBlank() && (g.metaAt == 0L || System.currentTimeMillis() - g.metaAt > 7 * 86400000L ||
            (steamKey.isNotBlank() && !g.metaSchema))

    // Global unlock percentages (no key needed). With a Steam Web API key: names, descriptions and icons too.
    // Everything is saved to meta.json and icons/ inside the game's folder.
    fun meta(c: Context, g: Game, steamKey: String) {
        val ach = JSONObject()
        fun slot(id: String): JSONObject = ach.optJSONObject(id) ?: JSONObject().also { ach.put(id, it) }
        var gotPct = false
        get("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v0002/?gameid=${g.appId}&format=json")?.let { b ->
            try {
                val a = JSONObject(String(b)).getJSONObject("achievementpercentages").getJSONArray("achievements")
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    slot(o.getString("name")).put("p", o.optDouble("percent"))
                }
                gotPct = true
            } catch (e: Exception) { }
        }
        var schema = false
        if (steamKey.isNotBlank()) {
            get("https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$steamKey&appid=${g.appId}&l=english")?.let { b ->
                try {
                    val a = JSONObject(String(b)).getJSONObject("game").getJSONObject("availableGameStats").getJSONArray("achievements")
                    val icons = g.dir.findFile("icons") ?: g.dir.createDirectory("icons")
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        val id = o.getString("name")
                        val s = slot(id)
                        s.put("n", o.optString("displayName"))
                        s.put("d", o.optString("description"))
                        val url = o.optString("icon")
                        if (url.isNotBlank() && icons != null) {
                            val fn = "icon_" + id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".jpg"
                            if (icons.findFile(fn) == null) get(url)?.let { bytes -> save(c, icons, fn, bytes) }
                            s.put("i", fn)
                        }
                    }
                    schema = true
                } catch (e: Exception) { }
            }
        }
        var pageOk = false
        if (!schema) pageOk = page(c, g, ach)
        if (!gotPct && !schema && !pageOk) return
        if (!schema && g.metaSchema) return // keep the richer file we already have
        val root = JSONObject().put("at", System.currentTimeMillis()).put("s", schema).put("ach", ach)
        save(c, g.dir, "meta.json", root.toString().toByteArray())
    }
}
