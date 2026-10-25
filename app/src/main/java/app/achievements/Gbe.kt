package app.achievements

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Sets up the gbe_fork Steam emulator for a game and (re)builds its achievement list.
// The emulator DLLs ship inside the app. The progress file (achievements.json written by the emulator)
// is only ever read or backed up here, never overwritten.
object Gbe {
    class Hit(val dll: DocumentFile, val dir: DocumentFile)
    class Def(val api: String, val name: String, val desc: String, val hidden: Boolean, val icon: String?, val pct: Double?)
    class PageRow(val name: String, val desc: String, val pct: Double?, val img: String)

    private fun http(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AchievementsApp")
        c.setRequestProperty("Accept-Language", "en")
        return c
    }
    fun getBytes(url: String): ByteArray? = try {
        val c = http(url)
        if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) { null }
    fun getText(url: String): String? = getBytes(url)?.let { String(it) }

    fun search(q: String): List<Pair<String, String>> {
        val t = getText("https://store.steampowered.com/api/storesearch/?term=" + URLEncoder.encode(q, "UTF-8") + "&l=english&cc=us")
            ?: return emptyList()
        return try {
            val a = JSONObject(t).optJSONArray("items") ?: return emptyList()
            (0 until a.length()).map { val o = a.getJSONObject(it); o.optInt("id").toString() to o.optString("name") }.take(6)
        } catch (e: Exception) { emptyList() }
    }

    // ---- file helpers (all through the folder permission the user granted) ----
    private fun put(c: Context, dir: DocumentFile, name: String, bytes: ByteArray) = putStream(c, dir, name, bytes.inputStream())

    private fun putStream(c: Context, dir: DocumentFile, name: String, src: InputStream) {
        check(name != "achievements.json" || dir.name == "steam_settings") { "Refusing to write a progress file" }
        dir.findFile(name)?.delete()
        val f = dir.createFile("application/octet-stream", name) ?: error("Couldn't create $name")
        if (f.name != name) f.renameTo(name)
        val out = c.contentResolver.openOutputStream(f.uri) ?: error("Couldn't write $name")
        out.use { o -> src.use { it.copyTo(o) } }
    }

    private fun read(c: Context, f: DocumentFile): ByteArray? = try {
        c.contentResolver.openInputStream(f.uri)?.use { it.readBytes() }
    } catch (e: Exception) { null }

    fun findDlls(dir: DocumentFile, depth: Int = 4): List<Hit> {
        val out = ArrayList<Hit>()
        for (f in dir.listFiles()) {
            val n = f.name ?: continue
            if (f.isDirectory) { if (depth > 0) out += findDlls(f, depth - 1) }
            else if (n.equals("steam_api.dll", true) || n.equals("steam_api64.dll", true)) out += Hit(f, dir)
        }
        return out
    }

    // The game's folder inside the achievements folder: "<AppID>" or "<AppID> - Name".
    fun findGameDir(root: DocumentFile, appId: String): DocumentFile? =
        root.listFiles().firstOrNull { it.isDirectory && (it.name == appId || it.name?.startsWith("$appId ") == true || it.name?.startsWith("$appId-") == true) }

    private fun relPath(from: DocumentFile, to: DocumentFile): String? {
        val a = DocumentsContract.getDocumentId(from.uri).split(":", limit = 2)
        val b = DocumentsContract.getDocumentId(to.uri).split(":", limit = 2)
        if (a[0] != b[0]) return null
        val x = a.getOrElse(1) { "" }.split("/").filter { it.isNotEmpty() }
        val y = b.getOrElse(1) { "" }.split("/").filter { it.isNotEmpty() }
        var i = 0
        while (i < x.size && i < y.size && x[i] == y[i]) i++
        return (List(x.size - i) { ".." } + y.drop(i)).joinToString("/").ifEmpty { "." }
    }

    private fun interfaces(b: ByteArray): String? {
        val s = String(b, Charsets.ISO_8859_1)
        val m = Regex("STEAM[A-Z]+_INTERFACE_V(?:ERSION)?_?[0-9]{3}|Steam[A-Za-z]+[0-9]{3}").findAll(s).map { it.value }.toSortedSet()
        return if (m.isEmpty()) null else m.joinToString("\n")
    }

    // Safety net: copy the current progress file aside before touching anything.
    private fun backupProgress(c: Context, ad: DocumentFile) {
        val pf = ad.findFile("achievements.json") ?: ad.findFile(ad.name?.takeWhile { it.isDigit() } ?: "x")?.findFile("achievements.json") ?: return
        val b = read(c, pf) ?: return
        if (b.isNotEmpty()) put(c, ad, "progress.bak.json", b)
    }

    // ---- Steam's public achievements page (new layout): every achievement is an <img> from community_assets
    // followed by its name, description and global percentage. Parsed by those images, not by class names.
    private fun dec(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }

    fun rows(html: String, appId: String): List<PageRow> {
        val tags = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).toList()
        val out = ArrayList<PageRow>()
        for ((i, m) in tags.withIndex()) {
            val tag = m.value
            val src = Regex("src=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)?.let { dec(it) } ?: continue
            if (!src.contains("/community_assets/images/apps/$appId/")) continue
            val alt = Regex("alt=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)?.let { dec(it).trim() } ?: ""
            val next = if (i + 1 < tags.size) tags[i + 1].range.first else html.length
            val seg = html.substring(m.range.last + 1, next)
            val chunks = Regex(">([^<>]+)<").findAll(">$seg<").map { dec(it.groupValues[1]).trim() }.filter { it.isNotEmpty() }.toList()
            val k = chunks.indexOfFirst { it.matches(Regex("\\d+(\\.\\d+)?%")) }
            val body = if (k >= 0) chunks.take(k) else chunks.take(2)
            val name = if (alt.isNotBlank()) alt else (body.firstOrNull() ?: continue)
            val desc = body.filter { it != name && it != alt }.joinToString(" ")
            out += PageRow(name, desc, if (k >= 0) chunks[k].removeSuffix("%").toDoubleOrNull() else null, src)
        }
        return out
    }

    private fun iconName(api: String) = api.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".jpg"

    private class Built(val defs: List<Def>, val urls: Map<String, String>)

    // Replacement for generate_emu_config: achievement IDs come from Steam's percentage data (all of them,
    // hidden ones too); names, descriptions and icons come from the public page and are matched by percentage.
    private fun build(appId: String, key: String, log: (String) -> Unit): Built {
        if (key.isNotBlank()) {
            log("Reading the Steam schema")
            try {
                val t = getText("https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$key&appid=$appId&l=english") ?: ""
                val a = JSONObject(t).getJSONObject("game").getJSONObject("availableGameStats").getJSONArray("achievements")
                val urls = HashMap<String, String>()
                val defs = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    val id = o.getString("name")
                    o.optString("icon").takeIf { u -> u.isNotBlank() }?.let { u -> urls[iconName(id)] = u }
                    Def(id, o.optString("displayName").ifBlank { id }, o.optString("description"), o.optInt("hidden") == 1, iconName(id), null)
                }
                return Built(defs, urls)
            } catch (e: Exception) { log("Schema lookup failed, using the public pages instead") }
        }
        log("Reading Steam's public data")
        val pj = getText("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v0002/?gameid=$appId&format=json")
            ?: error("Couldn't reach Steam. Check your internet.")
        val arr = try { JSONObject(pj).getJSONObject("achievementpercentages").getJSONArray("achievements") }
        catch (e: Exception) { error("Steam has no achievements listed for AppID $appId.") }
        val api = (0 until arr.length()).map { val o = arr.getJSONObject(it); o.getString("name") to o.optDouble("percent") }.sortedByDescending { it.second }
        val html = getText("https://steamcommunity.com/achievements/$appId?l=english")
        val page = if (html == null) emptyList() else rows(html, appId).sortedByDescending { it.pct ?: -1.0 }
        log("Steam lists ${api.size} achievements, its page shows ${page.size}")
        if (page.isEmpty()) log("Couldn't read names and icons from Steam's page. IDs only for now.")
        val match = arrayOfNulls<PageRow>(api.size)
        var j = 0
        for (r in page) {
            val rp = r.pct ?: continue
            while (j < api.size && api[j].second - rp > 0.06) j++
            if (j < api.size && Math.abs(api[j].second - rp) <= 0.06) { match[j] = r; j++ }
        }
        val urls = HashMap<String, String>()
        val defs = api.mapIndexed { i, (id, pct) ->
            val r = match[i]
            if (r != null) urls[iconName(id)] = r.img
            Def(id, r?.name ?: id, r?.desc ?: "", page.isNotEmpty() && r == null, if (r != null) iconName(id) else null, pct)
        }
        return Built(defs, urls)
    }

    private fun defsAndIcons(appId: String, key: String, log: (String) -> Unit): Pair<List<Def>, Map<String, ByteArray>> {
        val b = build(appId, key, log)
        if (b.defs.isEmpty()) error("No achievements found for AppID $appId.")
        log("Downloading ${b.urls.size} icons")
        val icons = HashMap<String, ByteArray>()
        for ((n, u) in b.urls) getBytes(u)?.let { icons[n] = it }
        return b.defs to icons
    }

    private fun writeDefs(c: Context, st: DocumentFile, defs: List<Def>, icons: Map<String, ByteArray>) {
        val arr = JSONArray()
        for (d in defs) {
            val ic = if (d.icon != null && icons.containsKey(d.icon)) "images/" + d.icon else ""
            val o = JSONObject().put("name", d.api).put("displayName", d.name).put("description", d.desc)
                .put("hidden", if (d.hidden) "1" else "0").put("icon", ic).put("icongray", ic)
            d.pct?.let { o.put("unlock_percentage", Math.round(it * 10.0) / 10.0) }
            arr.put(o)
        }
        put(c, st, "achievements.json", arr.toString(2).toByteArray())
        if (icons.isNotEmpty()) {
            val img = st.findFile("images") ?: st.createDirectory("images") ?: return
            for ((n, b) in icons) put(c, img, n, b)
        }
    }

    // ---- public actions ----
    fun install(c: Context, p: Prefs, game: DocumentFile, appId: String, title: String, type: String, overlay: Boolean, log: (String) -> Unit) {
        val root = Scanner.root(c, p) ?: error("Choose the achievements folder in Settings first.")
        val hits = findDlls(game)
        if (hits.isEmpty()) error("No steam_api.dll or steam_api64.dll found inside this game folder.")
        val ad = findGameDir(root, appId) ?: root.createDirectory(appId) ?: error("Couldn't create the game's folder in the achievements folder.")
        backupProgress(c, ad)
        val (defs, icons) = defsAndIcons(appId, p.steamKey, log)
        // Progress goes inside the game's folder in the achievements folder. A plain "<AppID>" folder is
        // saved to from its parent so the file lands at <AppID>/achievements.json.
        val saveTarget = if (ad.name == appId) root else ad
        for (h in hits) {
            val dllName = h.dll.name ?: continue
            val is64 = dllName.equals("steam_api64.dll", true)
            val asset = "gbe/$type/" + if (is64) "x64/steam_api64.dll" else "x86/steam_api.dll"
            val rel = relPath(h.dir, saveTarget) ?: error("The game folder and the achievements folder must be on the same storage.")
            val bak = h.dir.findFile("$dllName.bak")
            val original: ByteArray?
            if (bak == null) {
                original = read(c, h.dll)
                if (!h.dll.renameTo("$dllName.bak")) error("Couldn't back up the original $dllName, so I left it alone.")
            } else {
                original = read(c, bak)
                h.dir.findFile(dllName)?.delete()
            }
            log("Copying the $type emulator into ${h.dir.name}")
            putStream(c, h.dir, dllName, c.assets.open(asset))
            val st = h.dir.findFile("steam_settings") ?: h.dir.createDirectory("steam_settings") ?: error("Couldn't create steam_settings.")
            put(c, st, "steam_appid.txt", appId.toByteArray())
            writeDefs(c, st, defs, icons)
            put(c, st, "configs.user.ini", ("[user::general]\naccount_name=" + p.name.ifBlank { "Player" } + "\n\n[user::saves]\nlocal_save_path=" + rel + "\n").toByteArray())
            put(c, st, "configs.overlay.ini", ("[overlay::general]\nenable_experimental_overlay=" + (if (overlay) 1 else 0) + "\n").toByteArray())
            if (overlay) {
                val snd = st.findFile("sounds") ?: st.createDirectory("sounds")
                if (snd != null) putStream(c, snd, "overlay_achievement_notification.wav", c.assets.open("sfx/unlock.wav"))
            }
            if (st.findFile("steam_interfaces.txt") == null) original?.let { o -> interfaces(o)?.let { put(c, st, "steam_interfaces.txt", it.toByteArray()) } }
            log("Set up $dllName")
        }
        val ast = ad.findFile("steam_settings") ?: ad.createDirectory("steam_settings") ?: error("Couldn't create steam_settings.")
        writeDefs(c, ast, defs, icons)
        put(c, ad, "title.txt", title.toByteArray())
        p.write("gdir_$appId", game.uri.toString())
        log("Finished. Launch the game to test.")
    }

    fun rescrape(c: Context, p: Prefs, dir: DocumentFile, appId: String, log: (String) -> Unit) {
        backupProgress(c, dir)
        val (defs, icons) = defsAndIcons(appId, p.steamKey, log)
        writeDefs(c, dir.findFile("steam_settings") ?: dir.createDirectory("steam_settings") ?: error("Couldn't create steam_settings."), defs, icons)
        val g = p.read("gdir_$appId")
        if (g.isNotBlank()) DocumentFile.fromTreeUri(c, Uri.parse(g))?.let { gd ->
            for (h in findDlls(gd)) h.dir.findFile("steam_settings")?.let { writeDefs(c, it, defs, icons) }
        }
        log("Updated ${defs.size} achievements. Your unlocked achievements were not touched.")
    }
}
