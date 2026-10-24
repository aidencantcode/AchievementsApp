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
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

object KeyBus { var h: ((Int) -> Boolean)? = null }

fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
    val s = InputDevice.getDevice(id)?.sources ?: 0
    (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Sfx.init(this)
        val tf = try { Typeface.createFromAsset(assets, "fonts/Inter.ttf") } catch (e: Exception) { null }
        val fam = if (tf != null) FontFamily(tf) else FontFamily.SansSerif
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = cBg, surface = cBg, onSurface = cTx, primary = cAc)) {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = fam, color = cTx)) {
                    Backdrop { App() }
                }
            }
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            val k = e.keyCode
            if ((k == KeyEvent.KEYCODE_BUTTON_L1 || k == KeyEvent.KEYCODE_BUTTON_R1 || k == KeyEvent.KEYCODE_BUTTON_B) && KeyBus.h?.invoke(k) == true) return true
        }
        return super.dispatchKeyEvent(e)
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
    BackHandler(sel != null || (setup && p.root.isNotBlank())) { Sfx.play("back"); if (sel != null) sel = null else setup = false }

    val g = games.firstOrNull { it.dir.name == sel }
    when {
        setup -> Form(p, games) { setup = false; sel = null; refresh() }
        adding -> AddGame(p, { adding = false }) { adding = false; refresh() }
        g != null -> Detail(g, p, { sel = null }) { refresh() }
        else -> Home(p, games, loaded, { sel = it }, { setup = true }, { adding = true })
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun Form(p: Prefs, games: List<Game>, done: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val canDebug = remember { p.root.isNotBlank() && p.name.isNotBlank() }
    var name by remember { mutableStateOf(p.name) }
    var root by remember { mutableStateOf(p.root) }
    var groot by remember { mutableStateOf(p.gamesRoot) }
    var key by remember { mutableStateOf(p.key) }
    var skey by remember { mutableStateOf(p.steamKey) }
    var alerts by remember { mutableStateOf(p.alerts) }
    var sfx by remember { mutableStateOf(p.sfxOn) }
    var side by remember { mutableStateOf(p.popupSide) }
    var secs by remember { mutableStateOf(p.popupSecs) }
    var style by remember { mutableStateOf(p.popupStyle) }
    var radius by remember { mutableStateOf(p.popupRadius) }
    var blur by remember { mutableStateOf(p.popupBlur) }
    var opacity by remember { mutableStateOf(p.popupOpacity) }
    var scale by remember { mutableStateOf(p.popupScale) }
    var hasFont by remember { mutableStateOf(File(c.filesDir, "popup_font").exists()) }
    var sfxTarget by remember { mutableStateOf("") }
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(c)) }
    var ignoring by remember { mutableStateOf(c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canDraw = Settings.canDrawOverlays(c)
        ignoring = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            root = u.toString()
        }
    }
    val pickG = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            groot = u.toString()
        }
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) scope.launch(Dispatchers.IO) {
            try {
                c.contentResolver.openInputStream(u)?.use { i -> File(c.filesDir, "popup_font").outputStream().use { o -> i.copyTo(o) } }
                withContext(Dispatchers.Main) { hasFont = true }
            } catch (e: Exception) { }
        }
    }
    val pickSfx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        val t = sfxTarget
        if (u != null && t.isNotBlank()) scope.launch(Dispatchers.IO) {
            try {
                val dir = File(c.filesDir, "sfx"); dir.mkdirs()
                c.contentResolver.openInputStream(u)?.use { i -> File(dir, t).outputStream().use { o -> i.copyTo(o) } }
                Sfx.reload(c)
                withContext(Dispatchers.Main) { Sfx.play(t) }
            } catch (e: Exception) { }
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun folderName(s: String, none: String) = if (s.isBlank()) none else (DocumentFile.fromTreeUri(c, Uri.parse(s))?.name ?: "Folder selected")
    fun askBattery() { c.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName))) }
    fun askOverlay() { c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName))) }
    val ready = name.isNotBlank() && root.isNotBlank()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 40.dp)) {
        Text("Achievements", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Everything you've earned, in one place.", fontSize = 15.sp, color = cMu)
        Spacer(Modifier.height(32.dp))
        Label("YOUR NAME")
        Field(name, "Name") { name = it }
        Spacer(Modifier.height(22.dp))
        Label("ACHIEVEMENTS FOLDER")
        Box(Modifier.fillMaxWidth().bp(12.dp) { pick.launch(null) }.clip(RoundedCornerShape(12.dp)).background(cSf).padding(16.dp)) {
            Text(folderName(root, "Choose folder"), fontSize = 15.sp, color = if (root.isBlank()) cMu else cTx)
        }
        Spacer(Modifier.height(22.dp))
        Label("GAMES FOLDER (OPTIONAL, FOR ONE-TAP SETUP)")
        Box(Modifier.fillMaxWidth().bp(12.dp) { pickG.launch(null) }.clip(RoundedCornerShape(12.dp)).background(cSf).padding(16.dp)) {
            Text(folderName(groot, "Choose games folder"), fontSize = 15.sp, color = if (groot.isBlank()) cMu else cTx)
        }
        Spacer(Modifier.height(22.dp))
        Label("STEAMGRIDDB KEY (OPTIONAL)")
        Field(key, "Used for cover art") { key = it }
        Spacer(Modifier.height(22.dp))
        Label("STEAM WEB API KEY (OPTIONAL)")
        Field(skey, "Not needed, icons come from Steam's public page") { skey = it }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alert me when I earn one", fontSize = 15.sp)
                Text("Keeps a small notification running", fontSize = 13.sp, color = cMu)
            }
            Switch(alerts, { alerts = it }, colors = SwitchDefaults.colors(checkedTrackColor = cAc, checkedThumbColor = Color.White, uncheckedTrackColor = cHi, uncheckedThumbColor = cMu, uncheckedBorderColor = cHi))
        }
        if (alerts && !ignoring) {
            Spacer(Modifier.height(14.dp))
            Text("Android may close the app in the background. Allow it to keep running.", fontSize = 13.sp, color = cMu)
            Spacer(Modifier.height(10.dp))
            Pill("ALLOW BACKGROUND RUNNING") { askBattery() }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Navigation sounds", fontSize = 15.sp)
                Text("Move, select, back and unlock sounds", fontSize = 13.sp, color = cMu)
            }
            Switch(sfx, { sfx = it; p.sfxOn = it; Sfx.on = it }, colors = SwitchDefaults.colors(checkedTrackColor = cAc, checkedThumbColor = Color.White, uncheckedTrackColor = cHi, uncheckedThumbColor = cMu, uncheckedBorderColor = cHi))
        }
        if (sfx) {
            Spacer(Modifier.height(10.dp))
            listOf("move" to "Move", "select" to "Select", "back" to "Back", "unlock" to "Unlock").forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, color = cMu, modifier = Modifier.weight(1f))
                    Pill("PLAY") { Sfx.play(id) }
                    Spacer(Modifier.width(6.dp))
                    Pill("IMPORT") { sfxTarget = id; pickSfx.launch(arrayOf("audio/*")) }
                    Spacer(Modifier.width(6.dp))
                    Pill("RESET") { File(c.filesDir, "sfx/$id").delete(); Sfx.reload(c) }
                }
            }
        }
        if (canDebug) {
            Spacer(Modifier.height(36.dp))
            Text("Pop-up and debug", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Label("STYLE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("solid" to "SOLID", "glass" to "GLASS", "liquid" to "LIQUID GLASS").forEach { (id, l) -> Pill(l, style == id) { style = id; p.popupStyle = id } }
            }
            Spacer(Modifier.height(18.dp))
            Label("SLIDES IN FROM")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("top", "bottom", "left", "right").forEach { s -> Pill(s.uppercase(), side == s) { side = s; p.popupSide = s } }
            }
            Spacer(Modifier.height(18.dp))
            Label("STAYS VISIBLE FOR")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("-") { if (secs > 1) { secs--; p.popupSecs = secs } }
                Text("$secs s", fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp))
                Pill("+") { if (secs < 15) { secs++; p.popupSecs = secs } }
            }
            Spacer(Modifier.height(14.dp))
            Sl("Roundness", radius, 0, 40, " dp") { radius = it; p.popupRadius = it }
            Sl("Blur behind", blur, 0, 60, " dp") { blur = it; p.popupBlur = it }
            Sl("Glass opacity", opacity, 20, 95, "%") { opacity = it; p.popupOpacity = it }
            Sl("Size", scale, 80, 140, "%") { scale = it; p.popupScale = it }
            Spacer(Modifier.height(8.dp))
            Text("Blur behind softens the game under the pop-up. It needs Android 12 or newer with window blur turned on; otherwise the glass gets more opaque.", fontSize = 12.sp, color = cMu)
            Spacer(Modifier.height(18.dp))
            Label("POP-UP FONT")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (hasFont) "Custom font" else "Default", fontSize = 14.sp, color = cMu)
                Pill("IMPORT FONT") { pickFont.launch(arrayOf("*/*")) }
                if (hasFont) Pill("RESET") { File(c.filesDir, "popup_font").delete(); hasFont = false }
            }
            Spacer(Modifier.height(20.dp))
            if (!canDraw) {
                Text("Pop-ups over games need the \"display over other apps\" permission.", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(10.dp))
                Pill("ALLOW DISPLAY OVER OTHER APPS") { askOverlay() }
                Spacer(Modifier.height(16.dp))
            }
            Pill("TEST POP-UP", accent = true) {
                if (!canDraw) askOverlay() else scope.launch(Dispatchers.IO) {
                    val g = games.firstOrNull { it.earned > 0 }
                    val a = g?.achs?.firstOrNull { it.earned }
                    val bmp = a?.icon?.let { try { c.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } } catch (e: Exception) { null } }
                    Popup.show(c.applicationContext, p, a?.name ?: "Slice 'n Dice", a?.desc ?: "Defeated Bazz", g?.title ?: "EX Zodiac", bmp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        Pill("CONTINUE", accent = ready, modifier = Modifier.fillMaxWidth()) {
            if (ready) {
                p.name = name.trim(); p.root = root; p.gamesRoot = groot; p.key = key.trim(); p.steamKey = skey.trim(); p.alerts = alerts
                val svc = Intent(c, WatchService::class.java)
                if (alerts) {
                    if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    c.startForegroundService(svc)
                    if (!ignoring) askBattery()
                } else c.stopService(svc)
                done()
            }
        }
    }
}

@Composable
private fun Sl(label: String, v: Int, lo: Int, hi: Int, unit: String, set: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row { Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("$v$unit", fontSize = 14.sp, color = cMu) }
        Slider(
            value = v.toFloat(), onValueChange = { set(it.roundToInt()) }, valueRange = lo.toFloat()..hi.toFloat(),
            colors = SliderDefaults.colors(thumbColor = cAc, activeTrackColor = cAc, inactiveTrackColor = cHi)
        )
    }
}

// ---------------------------------------------------------------- Library

@Composable
fun Home(p: Prefs, games: List<Game>, loaded: Boolean, open: (String) -> Unit, settings: () -> Unit, add: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(p.read("sort").ifBlank { "recent" }) }
    val total = games.sumOf { it.achs.size }
    val got = games.sumOf { it.earned }
    val recent = remember(games) {
        games.flatMap { g -> g.achs.filter { it.earned && it.time > 0 }.map { g to it } }.sortedByDescending { it.second.time }.take(10)
    }
    val shown = remember(games, q, sort) {
        val l = games.filter { q.isBlank() || it.title.contains(q, true) }
        when (sort) {
            "az" -> l.sortedBy { it.title.lowercase() }
            "progress" -> l.sortedByDescending { if (it.achs.isEmpty()) 0f else it.earned.toFloat() / it.achs.size }
            else -> l.sortedByDescending { g -> g.achs.maxOfOrNull { if (it.earned) it.time else 0L } ?: 0L }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hi, ${p.name}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Your achievements", fontSize = 14.sp, color = cMu)
                    }
                    Pill("ADD GAME") { add() }
                    Spacer(Modifier.width(8.dp))
                    Pill("SETTINGS") { settings() }
                }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.fillMaxWidth().glass(16.dp).padding(16.dp)) {
                    Row {
                        Text("$got OF $total ACHIEVEMENTS EARNED", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
                        Text("(${if (total == 0) 0 else got * 100 / total}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cMu)
                    }
                    Spacer(Modifier.height(10.dp))
                    GradBar(got, total, 8.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("across ${games.size} games", fontSize = 12.sp, color = cMu)
                }
            }
        }
        if (recent.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(Modifier.height(4.dp))
                Label("RECENTLY EARNED")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    itemsIndexed(recent) { i, (g, a) ->
                        Box(Modifier.size(60.dp).appear("recent", i).bp(10.dp) { open(g.dir.name ?: "") }.rareGlow(a.percent != null && a.percent < 10.0).clip(RoundedCornerShape(10.dp)).background(cSf)) {
                            AsyncImage(a.icon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(Modifier.height(4.dp))
                Field(q, "Search games") { q = it }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("recent" to "RECENT", "az" to "A-Z", "progress" to "PROGRESS").forEach { (id, l) ->
                        Pill(l, sort == id) { sort = id; p.write("sort", id) }
                    }
                }
            }
        }
        if (loaded && games.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("No games found. Use Add game to set one up, or point Settings at a folder with one folder per game.", fontSize = 14.sp, color = cMu, modifier = Modifier.padding(vertical = 24.dp))
        }
        gridItemsIndexed(shown, key = { _, g -> g.dir.uri.toString() }) { i, g ->
            Box(
                Modifier.fillMaxWidth().aspectRatio(2f / 3f).appear("tile", i).bp(14.dp) { open(g.dir.name ?: "") }
                    .clip(RoundedCornerShape(14.dp)).background(cSf)
            ) {
                AsyncImage(g.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE05080C)))).padding(10.dp)
                ) {
                    Text(g.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${g.earned} / ${g.achs.size}", fontSize = 11.sp, color = cMu)
                    Spacer(Modifier.height(6.dp))
                    GradBar(g.earned, g.achs.size, 4.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Game page

@Composable
private fun KeyHint(t: String) {
    Box(Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE6EAEE)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF11161C))
    }
}

@Composable
private fun AchRow(a: Ach, global: Boolean, fmt: DateFormat, gray: ColorFilter, idx: Int, tab: Int, click: () -> Unit) {
    val hide = a.hidden && !a.earned
    val rare = a.earned && a.percent != null && a.percent < 10.0
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).appear(tab, idx).bp(10.dp, click)
            .clip(RoundedCornerShape(10.dp)).background(cSf).padding(12.dp).alpha(if (a.earned || global) 1f else 0.62f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (global) Text(if (a.earned) "\u2713" else "", fontSize = 18.sp, color = cGreen, modifier = Modifier.width(26.dp))
        Box(Modifier.size(56.dp).rareGlow(rare)) {
            AsyncImage(
                if (hide) null else a.icon, null, contentScale = ContentScale.Crop, colorFilter = if (a.earned || global) null else gray,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(cHi)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (hide) "Hidden achievement" else a.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(if (hide) "Earn it to see what it is." else a.desc, fontSize = 13.sp, color = cMu, maxLines = 2)
            if (!global && a.percent != null) Text("%.1f%% of players have this achievement".format(a.percent), fontSize = 12.sp, color = cMu.copy(alpha = 0.8f))
        }
        Spacer(Modifier.width(10.dp))
        if (global) Text(a.percent?.let { "%.1f%%".format(it) } ?: "-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (rare) cGold else cMu)
        else if (a.earned && a.time > 0) Text("Unlocked " + fmt.format(Date(a.time * 1000)), fontSize = 12.sp, color = cMu, modifier = Modifier.widthIn(max = 110.dp))
    }
}

@Composable
fun Detail(g: Game, p: Prefs, back: () -> Unit, refresh: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    val pad = remember { hasGamepad() }
    var tab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Ach?>(null) }
    var menu by remember { mutableStateOf(false) }
    var gtype by remember { mutableStateOf(p.read("gbe_" + g.appId).ifBlank { "regular" }) }
    var busy by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val list = remember(g, tab, filter, q) {
        val l = g.achs.filter { (filter == 0 || (filter == 1) == it.earned) && (q.isBlank() || it.name.contains(q, true) || it.desc.contains(q, true)) }
        if (tab == 0) l.sortedWith(compareByDescending<Ach> { it.earned }.thenByDescending { it.time }.thenByDescending { it.percent ?: -1.0 })
        else l.sortedByDescending { it.percent ?: -1.0 }
    }
    fun close(): Boolean {
        Sfx.play("back")
        if (open != null) open = null else if (menu) { if (!busy) menu = false } else back()
        return true
    }
    BackHandler(open != null || menu) { close() }
    DisposableEffect(Unit) {
        KeyBus.h = { k ->
            when (k) {
                KeyEvent.KEYCODE_BUTTON_L1 -> { if (tab != 0) { Sfx.play("select"); tab = 0 }; true }
                KeyEvent.KEYCODE_BUTTON_R1 -> { if (tab != 1) { Sfx.play("select"); tab = 1 }; true }
                KeyEvent.KEYCODE_BUTTON_B -> close()
                else -> false
            }
        }
        onDispose { KeyBus.h = null }
    }
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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(250.dp)) {
                    AsyncImage(g.hero ?: g.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(14.dp).alpha(0.7f))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), cBg))))
                    Pill("BACK", modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) { close() }
                    Pill("MENU", modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { menu = true }
                    Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(g.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(cHi))
                        Spacer(Modifier.width(14.dp))
                        Text(g.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(14.dp).padding(16.dp)) {
                    Row {
                        Text("${g.earned} OF ${g.achs.size} ACHIEVEMENTS EARNED", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
                        Text("(${if (g.achs.isEmpty()) 0 else g.earned * 100 / g.achs.size}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cMu)
                    }
                    Spacer(Modifier.height(10.dp))
                    GradBar(g.earned, g.achs.size, 8.dp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (pad) KeyHint("L1")
                    Pill("MY ACHIEVEMENTS", tab == 0) { tab = 0 }
                    Spacer(Modifier.width(6.dp))
                    Pill("GLOBAL ACHIEVEMENTS", tab == 1) { tab = 1 }
                    if (pad) KeyHint("R1")
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Field(q, "Search achievements") { q = it }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL", "EARNED", "LOCKED").forEachIndexed { i, l -> Pill(l, filter == i) { filter = i } }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            itemsIndexed(list, key = { _, a -> "$tab-${a.id}" }) { i, a ->
                AchRow(a, tab == 1, fmt, gray, i, tab) { open = a }
            }
        }
        open?.let { a ->
            val hide = a.hidden && !a.earned
            Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = null })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color(0xF0192029)).padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(68.dp).rareGlow(a.earned && a.percent != null && a.percent < 10.0)) {
                        AsyncImage(if (hide) null else a.icon, null, contentScale = ContentScale.Crop, colorFilter = if (a.earned) null else gray, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(cHi))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(if (hide) "Hidden achievement" else a.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Text(if (hide) "Earn it to see what it is." else a.desc.ifBlank { "No description available." }, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                a.percent?.let { Text("%.1f%% of players have this achievement".format(it), fontSize = 13.sp, color = cMu) }
                Text(
                    if (a.earned && a.time > 0) "Unlocked " + fmt.format(Date(a.time * 1000)) else if (a.earned) "Unlocked" else "Not unlocked yet",
                    fontSize = 13.sp, color = cMu
                )
            }
        }
        if (menu) {
            Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!busy) menu = false })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color(0xF0192029)).padding(24.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Game options", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Label("EMULATOR BUILD")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("REGULAR", gtype == "regular") { gtype = "regular" }
                    Pill("EXPERIMENTAL", gtype == "experimental") { gtype = "experimental" }
                }
                Spacer(Modifier.height(8.dp))
                Text("Experimental adds the in-game overlay and may crash some games.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("APPLY BUILD", accent = true) {
                        val gd = p.read("gdir_" + g.appId)
                        if (gd.isBlank()) { log.clear(); log.add("No game folder is linked yet. Use Add game first.") }
                        else runJob { say ->
                            val dir = DocumentFile.fromTreeUri(c, Uri.parse(gd)) ?: error("Can't open the game folder.")
                            Gbe.install(c, p, dir, g.appId, g.title, gtype, say)
                            p.write("gbe_" + g.appId, gtype)
                        }
                    }
                    Pill("RESCRAPE") { runJob { say -> Gbe.rescrape(c, p, g.appId, say) } }
                }
                Spacer(Modifier.height(14.dp))
                Text("Your unlocked achievements are never changed by either action.", fontSize = 12.sp, color = cMu)
                if (busy) Text("Working...", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                log.forEach { Text(it, fontSize = 12.sp, color = cMu, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Add game

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
    BackHandler { if (!busy) { Sfx.play("back"); if (chosen != null) chosen = null else if (picked != null) picked = null else back() } }
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
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("BACK") { if (!busy) back() }
            Spacer(Modifier.width(14.dp))
            Text("Add a game", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        val ch = chosen
        val pk = picked
        when {
            ch != null -> {
                Text(ch.second, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("AppID ${ch.first}", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(20.dp))
                Label("EMULATOR BUILD")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("REGULAR", type == "regular") { if (!busy) type = "regular" }
                    Pill("EXPERIMENTAL", type == "experimental") { if (!busy) type = "experimental" }
                }
                Spacer(Modifier.height(8.dp))
                Text("Regular is the safe choice. Experimental adds the in-game overlay and may crash some games. You can switch later from the game's menu.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(24.dp))
                if (ok) Pill("DONE", accent = true) { done() }
                else if (!busy) Pill("SET UP", accent = true) {
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
                Text(pk.name ?: "", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Pick the matching Steam game.", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(14.dp))
                val r = results
                if (r == null) Text("Searching...", fontSize = 14.sp, color = cMu)
                else r.forEach { hit ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).bp(12.dp) { chosen = hit }.clip(RoundedCornerShape(12.dp)).background(cSf).padding(14.dp)) {
                        Text(hit.second, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("AppID ${hit.first}", fontSize = 12.sp, color = cMu)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Label("NOT LISTED? ENTER THE APPID")
                Field(manual, "e.g. 1249480") { manual = it.filter { ch2 -> ch2.isDigit() } }
                Spacer(Modifier.height(10.dp))
                if (manual.isNotBlank()) Pill("USE THIS APPID") { chosen = manual to (pk.name ?: manual) }
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
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).bp(12.dp) { picked = d }.clip(RoundedCornerShape(12.dp)).background(cSf).padding(16.dp)) {
                            Text(d.name ?: "", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
