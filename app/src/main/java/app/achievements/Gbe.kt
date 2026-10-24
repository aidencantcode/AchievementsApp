package app.achievements

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

// Sets up the gbe_fork Steam emulator for a game and (re)builds its achievement list.
// Rules: the progress file (achievements.json next to the game's AppID folder) is only ever read or backed up here.
object Gbe {
    class Hit(val dll: DocumentFile, val dir: DocumentFile)
    class Def(val api: String, val name: String, val desc: String, val hidden: Boolean, val icon: String?)
    private class Row(val name: String, val desc: String, val pct: Double?, val img: String?)

    private fun http(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "AchievementsApp")
        return c
    }
    private fun getBytes(url: String): ByteArray? = try {
        val c = http(url)
        if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) { null }
    private fun getText(url: String): String? = getBytes(url)?.let { String(it) }

    fun search(q: String): List<Pair<String, String>> {
        val t = getText("https://store.steampowered.com/api/storesearch/?term=" + URLEncoder.encode(q, "UTF-8") + "&l=english&cc=us")
            ?: return emptyList()
        return try {
            val a = JSONObject(t).optJSONArray("items") ?: return emptyList()
            (0 until a.length()).map { val o = a.getJSONObject(it); o.optInt("id").toString() to o.optString("name") }.take(6)
        } catch (e: Exception) { emptyList() }
    }

    // ---- emulator download (latest release, picked at runtime) ----
    fun ensureEmu(c: Context, type: String, log: (String) -> Unit): File {
        val out = File(c.filesDir, "gbe/$type")
        if (File(out, "x64/steam_api64.dll").exists() || File(out, "x32/steam_api.dll").exists()) return out
        log("Looking up the latest GBE release")
        val rel = JSONObject(getText("https://api.github.com/repos/Detanup01/gbe_fork/releases/latest") ?: error("Couldn't reach GitHub. Check your internet."))
        val arr = rel.getJSONArray("assets")
        val assets = (0 until arr.length()).map { arr.getJSONObject(it) }
        val exp = type == "experimental"
        val cands = assets.filter {
            val l = it.getString("name").lowercase()
            l.contains("win") && !l.contains("debug") && (l.endsWith(".7z") || l.endsWith(".zip"))
        }
        val pick = cands.firstOrNull { it.getString("name").lowercase().contains("exper") == exp } ?: cands.firstOrNull()
            ?: error("No Windows release file found. Release files: " + assets.joinToString { it.getString("name") })
        log("Downloading " + pick.getString("name"))
        val tmp = File(c.cacheDir, pick.getString("name"))
        http(pick.getString("browser_download_url")).inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        out.deleteRecursively(); out.mkdirs()
        val seen = ArrayList<String>()
        fun take(name: String, bytes: () -> ByteArray) {
            val l = name.lowercase().replace('\\', '/')
            if (seen.size < 15) seen.add(l)
            val base = l.substringAfterLast('/')
            if (base != "steam_api.dll" && base != "steam_api64.dll") return
            val isExp = l.contains("experimental")
            if (exp && l.contains("regular") && !isExp) return
            if (!exp && isExp) return
            val dest = File(out, if (base == "steam_api.dll") "x32/steam_api.dll" else "x64/steam_api64.dll")
            if (dest.exists()) return
            dest.parentFile?.mkdirs(); dest.writeBytes(bytes())
        }
        log("Unpacking")
        if (tmp.name.endsWith(".zip")) {
            ZipInputStream(tmp.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) { if (!e.isDirectory) take(e.name) { z.readBytes() }; e = z.nextEntry }
            }
        } else {
            SevenZFile.builder().setFile(tmp).get().use { sz ->
                var e = sz.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val entry = e
                        take(entry.name) {
                            val buf = ByteArray(entry.size.toInt()); var off = 0
                            while (off < buf.size) { val n = sz.read(buf, off, buf.size - off); if (n < 0) break; off += n }
                            buf
                        }
                    }
                    e = sz.nextEntry
                }
            }
        }
        tmp.delete()
        if (!File(out, "x64/steam_api64.dll").exists() && !File(out, "x32/steam_api.dll").exists())
            error("The release didn't contain the DLLs I expected. Files seen: " + seen.joinToString())
        return out
    }

    // ---- file helpers (all through the folder permission the user granted) ----
    private fun put(c: Context, dir: DocumentFile, name: String, bytes: ByteArray) {
        check(name != "achievements.json" || dir.name == "steam_settings") { "Refusing to write a progress file" }
        dir.findFile(name)?.delete()
        val f = dir.createFile("application/octet-stream", name) ?: error("Couldn't create $name")
        if (f.name != name) f.renameTo(name)
        c.contentResolver.openOutputStream(f.uri)?.use { it.write(bytes) } ?: error("Couldn't write $name")
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

    private fun relPath(from: DocumentFile, treeUri: String): String? {
        val a = DocumentsContract.getDocumentId(from.uri).split(":", limit = 2)
        val b = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)).split(":", limit = 2)
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
        val pf = ad.findFile("achievements.json") ?: return
        val b = read(c, pf) ?: return
        if (b.isNotEmpty()) put(c, ad, "progress.bak.json", b)
    }

    // ---- achievement list (replacement for generate_emu_config) ----
    private fun clean(s: String) = s.replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").trim()

    private fun rows(html: String): List<Row> {
        val parts = html.split("class=\"achieveRow")
        if (parts.size < 2) return emptyList()
        return parts.drop(1).mapNotNull { r ->
            val n = Regex("<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL).find(r)?.groupValues?.get(1)?.let { clean(it) } ?: return@mapNotNull null
            val d = Regex("<h5[^>]*>(.*?)</h5>", RegexOption.DOT_MATCHES_ALL).find(r)?.groupValues?.get(1)?.let { clean(it) } ?: ""
            val pct = Regex("achievePercent[^>]*>\\s*([0-9.]+)%").find(r)?.groupValues?.get(1)?.toDoubleOrNull()
            val img = Regex("<img[^>]+src=\"([^\"]+)\"").find(r)?.groupValues?.get(1)
            Row(n, d, pct, img)
        }
    }

    private fun iconName(api: String) = api.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".jpg"

    private fun buildDefs(appId: String, key: String, log: (String) -> Unit): List<Pair<Def, String?>> {
        if (key.isNotBlank()) {
            log("Reading the Steam schema")
            val t = getText("https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$key&appid=$appId&l=english")
            try {
                val a = JSONObject(t ?: "").getJSONObject("game").getJSONObject("availableGameStats").getJSONArray("achievements")
                return (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    Def(o.getString("name"), o.optString("displayName"), o.optString("description"), o.optInt("hidden") == 1, iconName(o.getString("name"))) to o.optString("icon").ifBlank { null }
                }
            } catch (e: Exception) { log("Schema lookup failed, using the public pages instead") }
        }
        log("Reading Steam's public pages")
        val pj = getText("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v0002/?gameid=$appId&format=json")
            ?: error("Couldn't reach Steam. Check your internet.")
        val arr = try { JSONObject(pj).getJSONObject("achievementpercentages").getJSONArray("achievements") } catch (e: Exception) { error("Steam has no achievements listed for AppID $appId.") }
        val api = (0 until arr.length()).map { val o = arr.getJSONObject(it); o.getString("name") to o.optDouble("percent") }.sortedByDescending { it.second }
        val page = rows(getText("https://steamcommunity.com/stats/$appId/achievements?l=english") ?: error("Couldn't load Steam's achievements page."))
            .sortedByDescending { it.pct ?: -1.0 }
        if (page.size != api.size) error("Steam's page lists ${page.size} achievements but its data lists ${api.size}, so the IDs can't be matched safely. Add a Steam Web API key or use generate_emu_config on a PC.")
        for (i in api.indices) {
            val q = page[i].pct ?: error("The page layout changed, so the IDs can't be matched safely.")
            if (Math.abs(api[i].second - q) > 0.6) error("Percentages don't line up for the achievement ${page[i].name}, so the IDs can't be matched safely. Add a Steam Web API key or use generate_emu_config on a PC.")
        }
        return api.indices.map { Def(api[it].first, page[it].name, page[it].desc, false, iconName(api[it].first)) to page[it].img }
    }

    private fun defsAndIcons(appId: String, key: String, log: (String) -> Unit): Pair<List<Def>, Map<String, ByteArray>> {
        val built = buildDefs(appId, key, log)
        if (built.isEmpty()) error("No achievements found for AppID $appId.")
        log("Downloading ${built.size} icons")
        val icons = HashMap<String, ByteArray>()
        for ((d, url) in built) if (url != null && d.icon != null) getBytes(url)?.let { icons[d.icon] = it }
        return built.map { it.first } to icons
    }

    private fun writeDefs(c: Context, st: DocumentFile, defs: List<Def>, icons: Map<String, ByteArray>) {
        val arr = JSONArray()
        for (d in defs) {
            val ic = if (d.icon != null && icons.containsKey(d.icon)) "images/" + d.icon else ""
            arr.put(JSONObject().put("name", d.api).put("defaultvalue", 0).put("displayName", d.name)
                .put("hidden", if (d.hidden) 1 else 0).put("description", d.desc).put("icon", ic).put("icon_gray", ic))
        }
        put(c, st, "achievements.json", arr.toString(2).toByteArray())
        if (icons.isNotEmpty()) {
            val img = st.findFile("images") ?: st.createDirectory("images") ?: return
            for ((n, b) in icons) put(c, img, n, b)
        }
    }

    // ---- public actions ----
    fun install(c: Context, p: Prefs, game: DocumentFile, appId: String, title: String, type: String, log: (String) -> Unit) {
        val root = Scanner.root(c, p) ?: error("Choose the achievements folder in Settings first.")
        val emu = ensureEmu(c, type, log)
        val hits = findDlls(game)
        if (hits.isEmpty()) error("No steam_api.dll or steam_api64.dll found inside this game folder.")
        val ad = root.findFile(appId) ?: root.createDirectory(appId) ?: error("Couldn't create the game's folder in the achievements folder.")
        backupProgress(c, ad)
        val (defs, icons) = defsAndIcons(appId, p.steamKey, log)
        for (h in hits) {
            val dllName = h.dll.name ?: continue
            val is64 = dllName.equals("steam_api64.dll", true)
            val src = File(emu, if (is64) "x64/steam_api64.dll" else "x32/steam_api.dll")
            if (!src.exists()) { log("Skipped $dllName (not in this release)"); continue }
            val rel = relPath(h.dir, p.root) ?: error("The game folder and the achievements folder must be on the same storage.")
            val bak = h.dir.findFile("$dllName.bak")
            val original: ByteArray?
            if (bak == null) {
                original = read(c, h.dll)
                if (!h.dll.renameTo("$dllName.bak")) error("Couldn't back up the original $dllName, so I left it alone.")
            } else {
                original = read(c, bak)
                h.dir.findFile(dllName)?.delete()
            }
            put(c, h.dir, dllName, src.readBytes())
            val st = h.dir.findFile("steam_settings") ?: h.dir.createDirectory("steam_settings") ?: error("Couldn't create steam_settings.")
            put(c, st, "steam_appid.txt", appId.toByteArray())
            writeDefs(c, st, defs, icons)
            put(c, st, "configs.user.ini", ("[user::general]\naccount_name=" + p.name.ifBlank { "Player" } + "\n\n[user::saves]\nlocal_save_path=" + rel + "\n").toByteArray())
            if (type == "experimental") put(c, st, "configs.overlay.ini", "[overlay::general]\nenable_experimental_overlay=1\n".toByteArray())
            else st.findFile("configs.overlay.ini")?.delete()
            if (st.findFile("steam_interfaces.txt") == null) original?.let { o -> interfaces(o)?.let { put(c, st, "steam_interfaces.txt", it.toByteArray()) } }
            log("Set up $dllName in ${h.dir.name}")
        }
        val ast = ad.findFile("steam_settings") ?: ad.createDirectory("steam_settings") ?: error("Couldn't create steam_settings.")
        writeDefs(c, ast, defs, icons)
        put(c, ad, "title.txt", title.toByteArray())
        p.write("gdir_$appId", game.uri.toString())
        log("Finished. Launch the game to test.")
    }

    fun rescrape(c: Context, p: Prefs, appId: String, log: (String) -> Unit) {
        val root = Scanner.root(c, p) ?: error("Choose the achievements folder in Settings first.")
        val ad = root.findFile(appId) ?: error("This game has no folder in the achievements folder.")
        backupProgress(c, ad)
        val (defs, icons) = defsAndIcons(appId, p.steamKey, log)
        writeDefs(c, ad.findFile("steam_settings") ?: ad.createDirectory("steam_settings") ?: error("Couldn't create steam_settings."), defs, icons)
        val g = p.read("gdir_$appId")
        if (g.isNotBlank()) DocumentFile.fromTreeUri(c, Uri.parse(g))?.let { gd ->
            for (h in findDlls(gd)) h.dir.findFile("steam_settings")?.let { writeDefs(c, it, defs, icons) }
        }
        log("Updated ${defs.size} achievements. Your unlocked achievements were not touched.")
    }
}
