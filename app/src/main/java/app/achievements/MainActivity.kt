package app.achievements

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val cBg = Color(0xFF1F2125)
private val cSf = Color(0xFF282B30)
private val cHi = Color(0xFF32363C)
private val cTx = Color(0xFFE8EAED)
private val cMu = Color(0xFF8E949C)
private val cAc = Color(0xFF9DB7D5)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val tf = try { Typeface.createFromAsset(assets, "fonts/Inter.ttf") } catch (e: Exception) { null }
        val fam = if (tf != null) FontFamily(tf) else FontFamily.SansSerif
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = cBg, surface = cBg, onSurface = cTx, primary = cAc)) {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = fam, color = cTx)) {
                    Box(Modifier.fillMaxSize().background(cBg)) { App() }
                }
            }
        }
    }
}

@Composable
fun App() {
    val c = LocalContext.current
    val p = remember { Prefs(c) }
    var games by remember { mutableStateOf(emptyList<Game>()) }
    var setup by remember { mutableStateOf(p.root.isBlank() || p.name.isBlank()) }
    var sel by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (p.root.isBlank()) return
        scope.launch {
            games = withContext(Dispatchers.IO) { Scanner.list(c, p) }
            loaded = true
            val miss = games.filter { it.cover == null || it.hero == null || Net.needsMeta(it, p.steamKey) }
            if (miss.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    miss.forEach {
                        Net.art(c, it, p.key)
                        if (Net.needsMeta(it, p.steamKey)) Net.meta(c, it, p.steamKey)
                    }
                }
                games = withContext(Dispatchers.IO) { Scanner.list(c, p) }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (p.alerts && p.root.isNotBlank()) c.startForegroundService(Intent(c, WatchService::class.java))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    BackHandler(sel != null || (setup && p.root.isNotBlank())) { if (sel != null) sel = null else setup = false }

    val g = games.firstOrNull { it.dir.name == sel }
    when {
        setup -> Form(p, games) { setup = false; sel = null; refresh() }
        adding -> AddGame(p, { adding = false }) { adding = false; refresh() }
        g != null -> Detail(g, p, { sel = null }) { refresh() }
        else -> Home(p, games, loaded, { sel = it }, { setup = true }, { adding = true })
    }
}

@Composable
fun Form(p: Prefs, games: List<Game>, done: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val canDebug = remember { p.root.isNotBlank() && p.name.isNotBlank() }
    var name by remember { mutableStateOf(p.name) }
    var root by remember { mutableStateOf(p.root) }
    var key by remember { mutableStateOf(p.key) }
    var skey by remember { mutableStateOf(p.steamKey) }
    var alerts by remember { mutableStateOf(p.alerts) }
    var side by remember { mutableStateOf(p.popupSide) }
    var secs by remember { mutableStateOf(p.popupSecs) }
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(c)) }
    var ignoring by remember { mutableStateOf(c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canDraw = Settings.canDrawOverlays(c)
        ignoring = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    }
    var groot by remember { mutableStateOf(p.gamesRoot) }
    val pickG = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            groot = u.toString()
        }
    }
    val gfolder = if (groot.isBlank()) "Choose games folder" else (DocumentFile.fromTreeUri(c, Uri.parse(groot))?.name ?: "Folder selected")
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            c.contentResolver.takePersistableUriPermission(
                u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            root = u.toString()
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val folder = if (root.isBlank()) "Choose folder" else (DocumentFile.fromTreeUri(c, Uri.parse(root))?.name ?: "Folder selected")
    val ready = name.isNotBlank() && root.isNotBlank()
    fun askBattery() {
        c.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName)))
    }
    fun askOverlay() {
        c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 48.dp)) {
        Text("Achievements", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Everything you've earned, in one place.", fontSize = 15.sp, color = cMu)
        Spacer(Modifier.height(40.dp))
        Label("Your name")
        Field(name, "Name") { name = it }
        Spacer(Modifier.height(24.dp))
        Label("Achievements folder")
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cSf)
                .clickable { pick.launch(null) }.padding(16.dp)
        ) { Text(folder, fontSize = 15.sp, color = if (root.isBlank()) cMu else cTx) }
        Spacer(Modifier.height(24.dp))
        Label("Games folder (optional, for one-tap setup)")
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cSf)
                .clickable { pickG.launch(null) }.padding(16.dp)
        ) { Text(gfolder, fontSize = 15.sp, color = if (groot.isBlank()) cMu else cTx) }
        Spacer(Modifier.height(24.dp))
        Label("SteamGridDB key (optional)")
        Field(key, "Used for cover art") { key = it }
        Spacer(Modifier.height(24.dp))
        Label("Steam Web API key (optional)")
        Field(skey, "Not needed, icons come from Steam's public page") { skey = it }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alert me when I earn one", fontSize = 15.sp)
                Text("Keeps a small notification running", fontSize = 13.sp, color = cMu)
            }
            Switch(
                checked = alerts, onCheckedChange = { alerts = it },
                colors = SwitchDefaults.colors(checkedTrackColor = cAc, checkedThumbColor = cBg, uncheckedTrackColor = cHi, uncheckedThumbColor = cMu, uncheckedBorderColor = cHi)
            )
        }
        if (alerts && !ignoring) {
            Spacer(Modifier.height(18.dp))
            Text("Android may close the app in the background. Allow it to keep running.", fontSize = 13.sp, color = cMu)
            Spacer(Modifier.height(10.dp))
            Chip("Allow background running", false) { askBattery() }
        }
        if (canDebug) {
            Spacer(Modifier.height(36.dp))
            Text("Debug", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            Label("Pop-up slides in from")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("top", "bottom", "left", "right").forEach { s ->
                    Chip(s.replaceFirstChar { it.uppercase() }, side == s) { side = s; p.popupSide = s }
                }
            }
            Spacer(Modifier.height(20.dp))
            Label("Stays visible for")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("-", false) { if (secs > 1) { secs--; p.popupSecs = secs } }
                Text("$secs s", fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp))
                Chip("+", false) { if (secs < 15) { secs++; p.popupSecs = secs } }
            }
            Spacer(Modifier.height(20.dp))
            if (!canDraw) {
                Text("Pop-ups over games need the \"display over other apps\" permission.", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(10.dp))
                Chip("Allow display over other apps", false) { askOverlay() }
                Spacer(Modifier.height(20.dp))
            }
            Chip("Test pop-up", false) {
                if (!canDraw) askOverlay() else scope.launch(Dispatchers.IO) {
                    val g = games.firstOrNull { it.earned > 0 }
                    val a = g?.achs?.firstOrNull { it.earned }
                    val bmp = a?.icon?.let {
                        try { c.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } } catch (e: Exception) { null }
                    }
                    Popup.show(c.applicationContext, p, a?.name ?: "Slice 'n Dice", a?.desc ?: "Defeated Bazz", g?.title ?: "EX Zodiac", bmp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (ready) cAc else cHi)
                .clickable(enabled = ready) {
                    p.name = name.trim(); p.root = root; p.gamesRoot = groot; p.key = key.trim(); p.steamKey = skey.trim(); p.alerts = alerts
                    val svc = Intent(c, WatchService::class.java)
                    if (alerts) {
                        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        c.startForegroundService(svc)
                        if (!ignoring) askBattery()
                    } else c.stopService(svc)
                    done()
                }.padding(16.dp),
            contentAlignment = Alignment.Center
        ) { Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (ready) cBg else cMu) }
    }
}

@Composable
private fun Chip(t: String, on: Boolean, click: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) cAc else cSf)
            .clickable { click() }.padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(t, fontSize = 14.sp, color = if (on) cBg else cTx) }
}

@Composable
private fun Label(t: String) {
    Text(t, fontSize = 13.sp, color = cMu, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Field(v: String, hint: String, set: (String) -> Unit) {
    TextField(
        value = v, onValueChange = set, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = cMu) }, shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cSf, unfocusedContainerColor = cSf,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = cTx, unfocusedTextColor = cTx, cursorColor = cAc
        )
    )
}

@Composable
private fun Bar(a: Int, b: Int) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(cHi)) {
        Box(Modifier.fillMaxWidth(if (b == 0) 0f else a.toFloat() / b).fillMaxHeight().background(cAc))
    }
}

@Composable
fun Home(p: Prefs, games: List<Game>, loaded: Boolean, open: (String) -> Unit, settings: () -> Unit, add: () -> Unit) {
    val total = games.sumOf { it.achs.size }
    val got = games.sumOf { it.earned }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Hi, ${p.name}", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("$got of $total achievements across ${games.size} games", fontSize = 14.sp, color = cMu)
                }
                Text("Add game", fontSize = 14.sp, color = cAc, modifier = Modifier.clickable { add() }.padding(8.dp))
                Text("Settings", fontSize = 14.sp, color = cAc, modifier = Modifier.clickable { settings() }.padding(8.dp))
            }
        }
        if (loaded && games.isEmpty()) item {
            Text(
                "No games found. Each game needs its own folder containing a steam_settings folder and an achievements.json file.",
                fontSize = 14.sp, color = cMu, modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        items(games, key = { it.dir.uri.toString() }) { g ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cSf)
                    .clickable { open(g.dir.name ?: "") }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    g.cover, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp, 84.dp).clip(RoundedCornerShape(8.dp)).background(cHi)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text("${g.earned} of ${g.achs.size}", fontSize = 13.sp, color = cMu)
                    Spacer(Modifier.height(10.dp))
                    Bar(g.earned, g.achs.size)
                }
            }
        }
    }
}

@Composable
fun Detail(g: Game, p: Prefs, back: () -> Unit, refresh: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val list = remember(g) { g.achs.sortedWith(compareByDescending<Ach> { it.earned }.thenByDescending { it.time }) }
    val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    var open by remember { mutableStateOf<Ach?>(null) }
    var menu by remember { mutableStateOf(false) }
    var gtype by remember { mutableStateOf(p.read("gbe_" + g.appId).ifBlank { "regular" }) }
    var busy by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    BackHandler(open != null || menu) { if (open != null) open = null else menu = false }

    fun runJob(job: (log: (String) -> Unit) -> Unit) {
        if (busy) return
        busy = true; log.clear()
        scope.launch(Dispatchers.IO) {
            val say = { s: String -> scope.launch(Dispatchers.Main) { log.add(s) }; Unit }
            try { job(say) } catch (e: Exception) { say("Failed: " + (e.message ?: e.toString())) }
            withContext(Dispatchers.Main) { busy = false; refresh() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp).background(cSf)) {
                    AsyncImage(g.hero, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, cBg))))
                    Text("Back", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clickable { back() }.padding(8.dp))
                    Text("\u2630", fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clickable { menu = true }.padding(8.dp))
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(g.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Text("${g.earned} of ${g.achs.size} earned", fontSize = 14.sp, color = cMu)
                    }
                }
            }
            item { Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { Bar(g.earned, g.achs.size) } }
            items(list, key = { it.id }) { a ->
                val hide = a.hidden && !a.earned
                Row(
                    Modifier.fillMaxWidth().clickable { open = a }.padding(horizontal = 20.dp, vertical = 10.dp)
                        .alpha(if (a.earned) 1f else 0.55f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        if (hide) null else a.icon, null, contentScale = ContentScale.Crop,
                        colorFilter = if (a.earned) null else gray,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(cHi)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (hide) "Hidden achievement" else a.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (hide) "Earn it to see what it is." else a.desc, fontSize = 13.sp, color = cMu, maxLines = 2)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        a.percent?.let { Text("%.1f%%".format(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        if (a.earned && a.time > 0) Text(fmt.format(Date(a.time * 1000)), fontSize = 11.sp, color = cMu)
                    }
                }
            }
        }
        open?.let { a ->
            val hide = a.hidden && !a.earned
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { open = null })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(cSf)
                    .clickable { }.padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        if (hide) null else a.icon, null, contentScale = ContentScale.Crop,
                        colorFilter = if (a.earned) null else gray,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(cHi)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(if (hide) "Hidden achievement" else a.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Text(if (hide) "Earn it to see what it is." else a.desc.ifBlank { "No description available." }, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                a.percent?.let { Text("%.1f%% of players have this".format(it), fontSize = 13.sp, color = cMu) }
                Text(
                    if (a.earned && a.time > 0) "Earned on " + fmt.format(Date(a.time * 1000)) else if (a.earned) "Earned" else "Not earned yet",
                    fontSize = 13.sp, color = cMu
                )
            }
        }
        if (menu) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { if (!busy) menu = false })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(cSf)
                    .clickable { }.padding(24.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Game options", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                Label("Emulator build")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Regular", gtype == "regular") { gtype = "regular" }
                    Chip("Experimental", gtype == "experimental") { gtype = "experimental" }
                }
                Spacer(Modifier.height(8.dp))
                Text("Experimental adds the in-game overlay and may crash some games.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Apply build", false) {
                        val gd = p.read("gdir_" + g.appId)
                        if (gd.isBlank()) { log.clear(); log.add("No game folder is linked yet. Use Add game first.") }
                        else runJob { say ->
                            val dir = DocumentFile.fromTreeUri(c, Uri.parse(gd)) ?: error("Can't open the game folder.")
                            Gbe.install(c, p, dir, g.appId, g.title, gtype, say)
                            p.write("gbe_" + g.appId, gtype)
                        }
                    }
                    Chip("Rescrape achievements", false) { runJob { say -> Gbe.rescrape(c, p, g.appId, say) } }
                }
                Spacer(Modifier.height(14.dp))
                Text("Your unlocked achievements are never changed by either action.", fontSize = 12.sp, color = cMu)
                if (busy) Text("Working...", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                log.forEach { Text(it, fontSize = 12.sp, color = cMu, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

@Composable
fun AddGame(p: Prefs, back: () -> Unit, done: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<DocumentFile>?>(null) }
    var picked by remember { mutableStateOf<DocumentFile?>(null) }
    var results by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var chosen by remember { mutableStateOf<Pair<String, String>?>(null) }
    var manual by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("regular") }
    var busy by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    BackHandler { if (!busy) { if (chosen != null) chosen = null else if (picked != null) picked = null else back() } }
    LaunchedEffect(Unit) {
        folders = withContext(Dispatchers.IO) {
            if (p.gamesRoot.isBlank()) emptyList()
            else (DocumentFile.fromTreeUri(c, Uri.parse(p.gamesRoot))?.listFiles() ?: emptyArray())
                .filter { it.isDirectory }.sortedBy { it.name?.lowercase() }
        }
    }
    LaunchedEffect(picked) {
        val f = picked ?: return@LaunchedEffect
        results = null
        val q = (f.name ?: "").replace(Regex("[-_.]+"), " ").trim()
        results = withContext(Dispatchers.IO) { Gbe.search(q) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Back", fontSize = 14.sp, modifier = Modifier.clickable { if (!busy) back() }.padding(vertical = 12.dp, horizontal = 4.dp))
        Text("Add a game", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        val ch = chosen
        val pk = picked
        when {
            ch != null -> {
                Text(ch.second, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("AppID ${ch.first}", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(20.dp))
                Label("Emulator build")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Regular", type == "regular") { if (!busy) type = "regular" }
                    Chip("Experimental", type == "experimental") { if (!busy) type = "experimental" }
                }
                Spacer(Modifier.height(8.dp))
                Text("Regular is the safe choice. Experimental adds the in-game overlay and may crash some games. You can switch later from the game's menu.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(24.dp))
                if (ok) Chip("Done", false) { done() }
                else if (!busy) Chip("Set up", true) {
                    busy = true; log.clear()
                    scope.launch(Dispatchers.IO) {
                        val say = { s: String -> scope.launch(Dispatchers.Main) { log.add(s) }; Unit }
                        try {
                            Gbe.install(c, p, pk!!, ch.first, ch.second, type, say)
                            p.write("gbe_" + ch.first, type)
                            withContext(Dispatchers.Main) { ok = true }
                        } catch (e: Exception) { say("Failed: " + (e.message ?: e.toString())) }
                        withContext(Dispatchers.Main) { busy = false }
                    }
                } else Text("Working...", fontSize = 14.sp)
                log.forEach { Text(it, fontSize = 12.sp, color = cMu, modifier = Modifier.padding(top = 6.dp)) }
            }
            pk != null -> {
                Text(pk.name ?: "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Pick the matching Steam game.", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(14.dp))
                val r = results
                if (r == null) Text("Searching...", fontSize = 14.sp, color = cMu)
                else r.forEach { it0 ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(cSf)
                        .clickable { chosen = it0 }.padding(14.dp)) {
                        Column { Text(it0.second, fontSize = 15.sp); Text("AppID ${it0.first}", fontSize = 12.sp, color = cMu) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Label("Not listed? Enter the AppID")
                Field(manual, "e.g. 1249480") { manual = it.filter { ch2 -> ch2.isDigit() } }
                Spacer(Modifier.height(10.dp))
                if (manual.isNotBlank()) Chip("Use this AppID", false) { chosen = manual to (pk.name ?: manual) }
            }
            else -> {
                val f = folders
                if (p.gamesRoot.isBlank()) Text("Choose a games folder in Settings first.", fontSize = 14.sp, color = cMu)
                else if (f == null) Text("Loading...", fontSize = 14.sp, color = cMu)
                else if (f.isEmpty()) Text("No game folders found.", fontSize = 14.sp, color = cMu)
                else {
                    Text("Pick a game folder.", fontSize = 13.sp, color = cMu)
                    Spacer(Modifier.height(10.dp))
                    f.forEach { d ->
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(cSf)
                            .clickable { picked = d }.padding(16.dp)) { Text(d.name ?: "", fontSize = 15.sp) }
                    }
                }
            }
        }
    }
}
