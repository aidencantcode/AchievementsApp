package app.achievements

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        val body = try { FontFamily(Typeface.createFromAsset(assets, "fonts/ARIALBD.TTF")) } catch (e: Exception) { FontFamily.SansSerif }
        headFont = try { FontFamily(Typeface.createFromAsset(assets, "fonts/Gilroy-ExtraBold.otf")) } catch (e: Exception) { body }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = cBg, surface = cBg, onSurface = cTx, primary = cAc)) {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = body, color = cTx)) {
                    Backdrop { App() }
                }
            }
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            val k = e.keyCode
            if ((k == KeyEvent.KEYCODE_BUTTON_L1 || k == KeyEvent.KEYCODE_BUTTON_R1 || k == KeyEvent.KEYCODE_BUTTON_B ||
                    k == KeyEvent.KEYCODE_BUTTON_X || k == KeyEvent.KEYCODE_BUTTON_Y) && KeyBus.h?.invoke(k) == true) return true
        }
        return super.dispatchKeyEvent(e)
    }
}

// ---------------------------------------------------------------- Chrome (top bar, bottom bar, splash)

private fun clock(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

private fun battery(c: Context): Pair<Int, Boolean> {
    val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100 to false
    val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
    val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return (l * 100 / s) to (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
}

@Composable
fun TopBar(title: String) {
    val c = LocalContext.current
    var time by remember { mutableStateOf(clock()) }
    var batt by remember { mutableStateOf(battery(c)) }
    LaunchedEffect(Unit) { while (true) { time = clock(); batt = battery(c); delay(20000) } }
    Column {
        Row(Modifier.fillMaxWidth().background(cPanel).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage("file:///android_asset/ui/logomini.jpg", null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(if (batt.second) "${batt.first}% +" else "${batt.first}%", fontSize = 13.sp, color = cMu)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(26.dp, 12.dp).border(1.5.dp, cTx).padding(2.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(batt.first / 100f).background(if (batt.second) cGreen else if (batt.first <= 20) Color(0xFFDE3619) else cTx))
            }
            Spacer(Modifier.width(16.dp))
            Text(time, fontSize = 15.sp, fontFamily = headFont)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(cAc))
    }
}

@Composable
private fun KeyHint(t: String) {
    Box(Modifier.padding(end = 8.dp).background(Color(0xFFE6EAEE)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, color = Color(0xFF11161C))
    }
}

@Composable
fun BottomBar(hints: List<Pair<String, String>>) {
    Row(
        Modifier.fillMaxWidth().background(cPanel).padding(horizontal = 20.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        hints.forEach { (k, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyHint(k)
                Text(l.uppercase(), fontSize = 12.sp, color = cMu, fontFamily = headFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        }
    }
}

@Composable
fun Splash() {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val inf = rememberInfiniteTransition(label = "ld")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "t")
    Box(Modifier.fillMaxSize().background(cBg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = a.value; scaleX = 0.9f + 0.1f * a.value; scaleY = 0.9f + 0.1f * a.value }
        ) {
            AsyncImage("file:///android_asset/ui/loadinglogo.png", null, contentScale = ContentScale.Fit, modifier = Modifier.width(220.dp))
            Spacer(Modifier.height(10.dp))
            Text("ACHIEVEMENTS", fontSize = 20.sp, fontFamily = headFont, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.size(170.dp, 3.dp).background(cHi).clipToBounds()) {
                Box(Modifier.offset(x = (t * 220 - 50).dp).size(50.dp, 3.dp).background(cAc))
            }
        }
    }
}

// ---------------------------------------------------------------- App

@Composable
fun App() {
    val c = LocalContext.current
    val p = remember { Prefs(c) }
    var games by remember { mutableStateOf(emptyList<Game>()) }
    var setup by remember { mutableStateOf(p.root.isBlank() || p.name.isBlank()) }
    var sel by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var splash by remember { mutableStateOf(true) }
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
        delay(1500); splash = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    BackHandler(sel != null || adding || (setup && p.root.isNotBlank())) {
        Sfx.play("back")
        if (sel != null) sel = null else if (adding) adding = false else setup = false
    }

    val g = games.firstOrNull { it.dir.name == sel }
    val screen = when { setup -> "settings"; adding -> "add"; g != null -> "game:" + sel; else -> "home" }
    val title = when { setup -> "Settings"; adding -> "Add a game"; g != null -> g.title; else -> "Achievements" }
    val hints = when {
        setup || adding -> listOf("A" to "Select", "B" to "Back")
        g != null -> listOf("A" to "Select", "B" to "Back", "L1 R1" to "Tabs", "X" to "Options")
        else -> listOf("A" to "Select", "X" to "Add game", "Y" to "Settings")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title)
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 14 }) togetherWith fadeOut(tween(120)) },
                    label = "screen"
                ) { s ->
                    when {
                        s == "settings" -> SettingsScreen(p, games) { setup = false; sel = null; refresh() }
                        s == "add" -> AddGame(p, { adding = false }) { adding = false; refresh() }
                        s.startsWith("game:") && g != null -> Detail(g, p, { sel = null }) { refresh() }
                        else -> Home(p, games, loaded, { sel = it }, { setup = true }, { adding = true })
                    }
                }
            }
            BottomBar(hints)
        }
        AnimatedVisibility(splash, exit = fadeOut(tween(500))) { Splash() }
    }
}

// ---------------------------------------------------------------- Shared controls

@Composable
private fun Toggle(title: String, desc: String, on: Boolean, set: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 26.dp else 3.dp, tween(150), label = "x")
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = headFont)
            Text(desc, fontSize = 13.sp, color = cMu)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(54.dp, 28.dp).bp(0.dp, if (on) "off" else "on") { set(!on) }.background(if (on) cAc else cHi)) {
            Box(Modifier.offset(x = x, y = 3.dp).size(22.dp).background(Color.White))
        }
    }
}

@Composable
private fun SqSlider(label: String, v: Int, lo: Int, hi: Int, unit: String, set: (Int) -> Unit) {
    var w by remember { mutableStateOf(1) }
    val f = (v - lo).toFloat() / (hi - lo)
    Column(Modifier.padding(vertical = 6.dp)) {
        Row { Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("$v$unit", fontSize = 14.sp, color = cGold, fontFamily = headFont) }
        Box(
            Modifier.fillMaxWidth().height(30.dp).onSizeChanged { w = it.width.coerceAtLeast(1) }
                .pointerInput(lo, hi) { detectTapGestures { o -> set((lo + (o.x / w * (hi - lo)).roundToInt()).coerceIn(lo, hi)) } }
                .pointerInput(lo, hi) { detectHorizontalDragGestures { ch, _ -> ch.consume(); set((lo + (ch.position.x / w * (hi - lo)).roundToInt()).coerceIn(lo, hi)) } }
        ) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(6.dp).background(cHi))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(f).height(6.dp).background(cAc))
            Box(Modifier.align(Alignment.CenterStart).offset { IntOffset((f * w).roundToInt() - 7.dp.roundToPx(), 0) }.size(14.dp, 24.dp).background(Color.White))
        }
    }
}

@Composable
private fun RailItem(t: String, on: Boolean, click: () -> Unit) {
    Box(Modifier.fillMaxWidth().bp(0.dp, "tab", click).background(if (on) cAc else Color.Transparent).padding(horizontal = 22.dp, vertical = 15.dp)) {
        Text(t, fontSize = 15.sp, fontFamily = headFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = if (on) Color.White else cMu)
    }
}

@Composable
private fun FolderBox(text: String, set: Boolean, click: () -> Unit) {
    Box(Modifier.fillMaxWidth().bp(0.dp, onClick = click).background(cSf).padding(16.dp)) {
        Text(text, fontSize = 15.sp, color = if (set) cTx else cMu)
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(p: Prefs, games: List<Game>, done: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val canDebug = remember { p.root.isNotBlank() && p.name.isNotBlank() }
    var cat by remember { mutableStateOf(0) }
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
            root = u.toString(); p.root = root
        }
    }
    val pickG = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            groot = u.toString(); p.gamesRoot = groot
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
    val cats = listOf("GENERAL", "ALERTS", "POP-UP", "SOUNDS")

    val body: @Composable ColumnScope.() -> Unit = {
        Text(cats[cat], fontSize = 26.sp, fontFamily = headFont, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        when (cat) {
            0 -> {
                Label("YOUR NAME")
                Field(name, "Name") { name = it; p.name = it.trim() }
                Spacer(Modifier.height(20.dp))
                Label("ACHIEVEMENTS FOLDER")
                FolderBox(folderName(root, "Choose folder"), root.isNotBlank()) { pick.launch(null) }
                Spacer(Modifier.height(20.dp))
                Label("GAMES FOLDER (FOR ONE-TAP SETUP)")
                FolderBox(folderName(groot, "Choose games folder"), groot.isNotBlank()) { pickG.launch(null) }
                Spacer(Modifier.height(20.dp))
                Label("STEAMGRIDDB KEY (OPTIONAL)")
                Field(key, "Used for cover art") { key = it; p.key = it.trim() }
                Spacer(Modifier.height(20.dp))
                Label("STEAM WEB API KEY (OPTIONAL)")
                Field(skey, "Not needed. Icons come from Steam's public page") { skey = it; p.steamKey = it.trim() }
            }
            1 -> {
                Toggle("Alert me when I earn one", "Keeps a small notification running in the background", alerts) { alerts = it; p.alerts = it }
                if (alerts && !ignoring) {
                    Spacer(Modifier.height(8.dp))
                    Text("Android may close the app in the background. Allow it to keep running.", fontSize = 13.sp, color = cMu)
                    Spacer(Modifier.height(10.dp))
                    Pill("ALLOW BACKGROUND RUNNING") { askBattery() }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    Spacer(Modifier.height(20.dp))
                    Pill("ALLOW NOTIFICATIONS") { perm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                Spacer(Modifier.height(20.dp))
                Text(if (canDraw) "Display over other apps: allowed. Pop-ups can show over games." else "Pop-ups over games need the \"display over other apps\" permission.", fontSize = 13.sp, color = if (canDraw) cGreen else cGold)
                if (!canDraw) { Spacer(Modifier.height(10.dp)); Pill("ALLOW DISPLAY OVER OTHER APPS") { askOverlay() } }
            }
            2 -> {
                if (!canDebug) Text("Finish the first setup (name and folder) to unlock the pop-up options.", fontSize = 14.sp, color = cMu)
                else {
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
                        Text("$secs s", fontSize = 16.sp, fontFamily = headFont, modifier = Modifier.padding(horizontal = 18.dp))
                        Pill("+") { if (secs < 15) { secs++; p.popupSecs = secs } }
                    }
                    Spacer(Modifier.height(12.dp))
                    SqSlider("Corner roundness", radius, 0, 40, " dp") { radius = it; p.popupRadius = it }
                    SqSlider("Blur behind", blur, 0, 60, " dp") { blur = it; p.popupBlur = it }
                    SqSlider("Glass opacity", opacity, 20, 95, "%") { opacity = it; p.popupOpacity = it }
                    SqSlider("Size", scale, 80, 140, "%") { scale = it; p.popupScale = it }
                    Spacer(Modifier.height(8.dp))
                    val blurOn = Build.VERSION.SDK_INT >= 31 && c.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
                    Text(
                        if (blurOn) "Blur behind: available on this device."
                        else "Blur behind: NOT available (Android ${Build.VERSION.RELEASE}). It needs Android 12 or newer with window blur enabled. The pop-up uses a tinted glass look instead.",
                        fontSize = 12.sp, color = if (blurOn) cGreen else cGold
                    )
                    Spacer(Modifier.height(18.dp))
                    Label("POP-UP FONT")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (hasFont) "Custom font" else "Default", fontSize = 14.sp, color = cMu)
                        Pill("IMPORT FONT") { pickFont.launch(arrayOf("*/*")) }
                        if (hasFont) Pill("RESET") { File(c.filesDir, "popup_font").delete(); hasFont = false }
                    }
                    Spacer(Modifier.height(22.dp))
                    Pill("TEST POP-UP", accent = true) {
                        if (!canDraw) askOverlay() else scope.launch(Dispatchers.IO) {
                            val g = games.firstOrNull { it.earned > 0 }
                            val a = g?.achs?.firstOrNull { it.earned }
                            val bmp = a?.icon?.let { try { c.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } } catch (e: Exception) { null } }
                            Popup.show(c.applicationContext, p, a?.name ?: "Slice 'n Dice", a?.desc ?: "Defeated Bazz", g?.title ?: "EX Zodiac", bmp)
                        }
                    }
                }
            }
            else -> {
                Toggle("Navigation sounds", "Move, select, back, tab and unlock sounds", sfx) { sfx = it; p.sfxOn = it; Sfx.on = it }
                if (sfx) {
                    Spacer(Modifier.height(10.dp))
                    listOf("move" to "Move", "select" to "Select", "back" to "Back", "unlock" to "Unlock", "tab" to "Tab").forEach { (id, label) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 15.sp, fontFamily = headFont, color = cMu, modifier = Modifier.weight(1f))
                            Pill("PLAY") { Sfx.play(id) }
                            Spacer(Modifier.width(6.dp))
                            Pill("IMPORT") { sfxTarget = id; pickSfx.launch(arrayOf("audio/*")) }
                            Spacer(Modifier.width(6.dp))
                            Pill("RESET") { File(c.filesDir, "sfx/$id").delete(); Sfx.reload(c) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(34.dp))
        Pill("DONE", accent = ready, modifier = Modifier.fillMaxWidth()) {
            if (ready) {
                val svc = Intent(c, WatchService::class.java)
                if (alerts) c.startForegroundService(svc) else c.stopService(svc)
                done()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 560.dp) Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(230.dp).fillMaxHeight().background(cPanel).padding(vertical = 14.dp)) {
                cats.forEachIndexed { i, l -> RailItem(l, cat == i) { cat = i } }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(28.dp), content = body)
        } else Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(cPanel).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEachIndexed { i, l -> Pill(l, cat == i, tab = true) { cat = i } }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), content = body)
        }
    }
}

// ---------------------------------------------------------------- Library

@Composable
private fun Stat(label: String, value: Int, suffix: String, modifier: Modifier) {
    val n by animateIntAsState(value, tween(900), label = "n")
    Column(modifier.background(cPanel).padding(14.dp)) {
        Text(label, fontSize = 11.sp, color = cMu, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text("$n$suffix", fontSize = 30.sp, color = cGold, fontFamily = headFont)
    }
}

@Composable
private fun Hero(g: Game, open: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "kb")
    val z by inf.animateFloat(1f, 1.1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "z")
    Box(Modifier.fillMaxWidth().height(190.dp).bp(0.dp, onClick = open).clipToBounds().background(cPanel)) {
        AsyncImage(g.hero ?: g.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = z; scaleY = z })
        Box(Modifier.fillMaxSize().background(Color(0x77000000)))
        Box(Modifier.align(Alignment.TopStart).background(cAc).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text("CONTINUE", fontSize = 12.sp, fontFamily = headFont, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(g.title, fontSize = 30.sp, fontFamily = headFont, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${g.earned} / ${g.achs.size} achievements", fontSize = 15.sp, color = cGold, fontFamily = headFont)
            Spacer(Modifier.height(8.dp))
            GradBar(g.earned, g.achs.size, 5.dp)
        }
    }
}

@Composable
fun Home(p: Prefs, games: List<Game>, loaded: Boolean, open: (String) -> Unit, settings: () -> Unit, add: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(p.read("sort").ifBlank { "recent" }) }
    DisposableEffect(Unit) {
        KeyBus.h = { k ->
            when (k) {
                KeyEvent.KEYCODE_BUTTON_X -> { Sfx.play("select"); add(); true }
                KeyEvent.KEYCODE_BUTTON_Y -> { Sfx.play("select"); settings(); true }
                else -> false
            }
        }
        onDispose { KeyBus.h = null }
    }
    val total = games.sumOf { it.achs.size }
    val got = games.sumOf { it.earned }
    val lastUnlock = { g: Game -> g.achs.maxOfOrNull { if (it.earned) it.time else 0L } ?: 0L }
    val feat = remember(games) { games.maxByOrNull(lastUnlock) }
    val recent = remember(games) {
        games.flatMap { g -> g.achs.filter { it.earned && it.time > 0 }.map { g to it } }.sortedByDescending { it.second.time }.take(10)
    }
    val shown = remember(games, q, sort) {
        val l = games.filter { q.isBlank() || it.title.contains(q, true) }
        when (sort) {
            "az" -> l.sortedBy { it.title.lowercase() }
            "progress" -> l.sortedByDescending { if (it.achs.isEmpty()) 0f else it.earned.toFloat() / it.achs.size }
            else -> l.sortedByDescending(lastUnlock)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (feat != null) item(span = { GridItemSpan(maxLineSpan) }) { Hero(feat) { open(feat.dir.name ?: "") } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("EARNED", got, "", Modifier.weight(1f))
                Stat("GAMES", games.size, "", Modifier.weight(1f))
                Stat("COMPLETE", if (total == 0) 0 else got * 100 / total, "%", Modifier.weight(1f))
            }
        }
        if (recent.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Label("RECENTLY EARNED")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    itemsIndexed(recent) { i, (g, a) ->
                        Box(Modifier.size(62.dp).appear("recent", i).bp(0.dp) { open(g.dir.name ?: "") }.rareGlow(a.percent != null && a.percent < 10.0).background(cSf)) {
                            AsyncImage(a.icon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Field(q, "Search games") { q = it }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("recent" to "RECENT", "az" to "A-Z", "progress" to "PROGRESS").forEach { (id, l) ->
                        Pill(l, sort == id, tab = true) { sort = id; p.write("sort", id) }
                    }
                    Spacer(Modifier.weight(1f))
                    Pill("ADD GAME") { add() }
                    Pill("SETTINGS") { settings() }
                }
            }
        }
        if (loaded && games.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("No games found. Use Add game to set one up, or point Settings at a folder with one folder per game.", fontSize = 14.sp, color = cMu, modifier = Modifier.padding(vertical = 24.dp))
        }
        gridItemsIndexed(shown, key = { _, g -> g.dir.uri.toString() }) { i, g ->
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).appear("tile", i).bp(0.dp) { open(g.dir.name ?: "") }.background(cSf)) {
                AsyncImage(g.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC0F1318)).padding(10.dp)) {
                    Text(g.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, maxLines = 1)
                    Text("${g.earned} / ${g.achs.size}", fontSize = 12.sp, color = cGold, fontFamily = headFont)
                    Spacer(Modifier.height(6.dp))
                    GradBar(g.earned, g.achs.size, 4.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Game page

@Composable
private fun AchRow(a: Ach, global: Boolean, fmt: DateFormat, gray: ColorFilter, idx: Int, tab: Int, click: () -> Unit) {
    val hide = a.hidden && !a.earned
    val rare = a.earned && a.percent != null && a.percent < 10.0
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).appear(tab, idx).bp(0.dp, onClick = click)
            .background(cSf).alpha(if (a.earned || global) 1f else 0.62f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(5.dp).height(92.dp).background(if (rare) cGold else if (a.earned) cAc else cHi))
        Spacer(Modifier.width(12.dp))
        if (global) Text(if (a.earned) "\u2713" else "", fontSize = 20.sp, color = cGreen, modifier = Modifier.width(24.dp))
        Box(Modifier.size(68.dp).rareGlow(rare)) {
            AsyncImage(
                if (hide) null else a.icon, null, contentScale = ContentScale.Crop, colorFilter = if (a.earned || global) null else gray,
                modifier = Modifier.fillMaxSize().background(cHi)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(if (hide) "Hidden achievement" else a.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, maxLines = 2)
            Text(if (hide) "Earn it to see what it is." else a.desc, fontSize = 15.sp, color = cMu, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
            if (a.percent != null) Text("%.1f%%".format(a.percent), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, color = if (rare) cGold else cTx)
            if (a.earned && a.time > 0) Text("Unlocked " + fmt.format(Date(a.time * 1000)), fontSize = 12.sp, color = cMu, modifier = Modifier.widthIn(max = 120.dp))
        }
    }
}

@Composable
fun Detail(g: Game, p: Prefs, back: () -> Unit, refresh: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    var tab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Ach?>(null) }
    var menu by remember { mutableStateOf(false) }
    var gtype by remember { mutableStateOf(p.read("gbe_" + g.appId).ifBlank { "regular" }) }
    var ov by remember { mutableStateOf(p.read("ov_" + g.appId) != "0") }
    var linked by remember { mutableStateOf(p.read("gdir_" + g.appId)) }
    var busy by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val list = remember(g, tab, filter, q) {
        val l = g.achs.filter { (filter == 0 || (filter == 1) == it.earned) && (q.isBlank() || it.name.contains(q, true) || it.desc.contains(q, true)) }
        if (tab == 0) l.sortedWith(compareByDescending<Ach> { it.earned }.thenByDescending { it.time }.thenByDescending { it.percent ?: -1.0 })
        else l.sortedByDescending { it.percent ?: -1.0 }
    }
    val pickLink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            p.write("gdir_" + g.appId, u.toString()); linked = u.toString()
            log.clear(); log.add("Game folder linked. Tap Apply to set it up.")
        }
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
                KeyEvent.KEYCODE_BUTTON_L1 -> { if (tab != 0) { Sfx.play("tab"); tab = 0 }; true }
                KeyEvent.KEYCODE_BUTTON_R1 -> { if (tab != 1) { Sfx.play("tab"); tab = 1 }; true }
                KeyEvent.KEYCODE_BUTTON_X -> { Sfx.play("open"); menu = true; true }
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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(140.dp).background(cPanel).clipToBounds()) {
                    AsyncImage(g.hero ?: g.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color(0x99000000)))
                    Pill("BACK", modifier = Modifier.align(Alignment.TopStart).padding(14.dp)) { close() }
                    Pill("OPTIONS", modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) { menu = true; Sfx.play("open") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().background(cPanel).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(g.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp, 120.dp).background(cHi))
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(g.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, maxLines = 2)
                        Spacer(Modifier.height(6.dp))
                        Text("${g.earned} / ${g.achs.size} achievements", fontSize = 18.sp, color = cGold, fontFamily = headFont)
                        Text("${if (g.achs.isEmpty()) 0 else g.earned * 100 / g.achs.size}% complete", fontSize = 14.sp, color = cMu)
                        Spacer(Modifier.height(10.dp))
                        GradBar(g.earned, g.achs.size, 6.dp)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Pill("MY ACHIEVEMENTS", tab == 0, tab = true) { tab = 0 }
                    Spacer(Modifier.width(6.dp))
                    Pill("GLOBAL ACHIEVEMENTS", tab == 1, tab = true) { tab = 1 }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Field(q, "Search achievements") { q = it }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL", "EARNED", "LOCKED").forEachIndexed { i, l -> Pill(l, filter == i, tab = true) { filter = i } }
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
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(cPanel).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).rareGlow(a.earned && a.percent != null && a.percent < 10.0)) {
                        AsyncImage(if (hide) null else a.icon, null, contentScale = ContentScale.Crop, colorFilter = if (a.earned) null else gray, modifier = Modifier.fillMaxSize().background(cHi))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(if (hide) "Hidden achievement" else a.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Text(if (hide) "Earn it to see what it is." else a.desc.ifBlank { "No description available." }, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                a.percent?.let { Text("%.1f%% of players have this achievement".format(it), fontSize = 13.sp, color = cMu) }
                Text(
                    if (a.earned && a.time > 0) "Unlocked " + fmt.format(Date(a.time * 1000)) else if (a.earned) "Unlocked" else "Not unlocked yet",
                    fontSize = 13.sp, color = cMu
                )
            }
        }
        if (menu) {
            Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!busy) menu = false })
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(cPanel).padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Game options", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = headFont)
                Spacer(Modifier.height(16.dp))
                Label("GAME FOLDER")
                Text(
                    linked.takeIf { it.isNotBlank() }?.let { "Linked: " + (DocumentFile.fromTreeUri(c, Uri.parse(it))?.name ?: "folder") } ?: "No game folder linked yet.",
                    fontSize = 13.sp, color = if (linked.isBlank()) cGold else cGreen
                )
                Spacer(Modifier.height(8.dp))
                Pill(if (linked.isBlank()) "LINK GAME FOLDER" else "CHANGE GAME FOLDER") { pickLink.launch(null) }
                Spacer(Modifier.height(18.dp))
                Label("EMULATOR BUILD")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("REGULAR", gtype == "regular") { gtype = "regular" }
                    Pill("EXPERIMENTAL", gtype == "experimental") { gtype = "experimental" }
                }
                Spacer(Modifier.height(6.dp))
                Text("Regular is the normal build. Experimental blocks the game's online connections (LAN only) and helps some older games. Try Regular first.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(16.dp))
                Label("IN-GAME OVERLAY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("ON", ov) { ov = true }
                    Pill("OFF", !ov) { ov = false }
                }
                Spacer(Modifier.height(6.dp))
                Text("Shows Steam-style pop-ups inside the game (Shift+Tab opens it). Works with either build, but may crash some games.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("APPLY", accent = true) {
                        if (linked.isBlank()) pickLink.launch(null)
                        else runJob { say ->
                            val dir = DocumentFile.fromTreeUri(c, Uri.parse(linked)) ?: error("Can't open the game folder.")
                            Gbe.install(c, p, dir, g.appId, g.title, gtype, ov, say)
                            p.write("gbe_" + g.appId, gtype); p.write("ov_" + g.appId, if (ov) "1" else "0")
                        }
                    }
                    Pill("RESCRAPE") { runJob { say -> Gbe.rescrape(c, p, g.dir, g.appId, say) } }
                }
                Spacer(Modifier.height(12.dp))
                Text("Rescrape needs no game folder. Your unlocked achievements are never changed by either action.", fontSize = 12.sp, color = cMu)
                if (busy) Text("Working...", fontSize = 13.sp, fontFamily = headFont, modifier = Modifier.padding(top = 10.dp))
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
    var ov by remember { mutableStateOf(true) }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        val ch = chosen
        val pk = picked
        when {
            ch != null -> {
                Text(ch.second, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = headFont)
                Text("AppID ${ch.first}", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(20.dp))
                Label("EMULATOR BUILD")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("REGULAR", type == "regular") { if (!busy) type = "regular" }
                    Pill("EXPERIMENTAL", type == "experimental") { if (!busy) type = "experimental" }
                }
                Spacer(Modifier.height(6.dp))
                Text("Regular is the normal build. Experimental blocks the game's online connections (LAN only) and helps some older games.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(16.dp))
                Label("IN-GAME OVERLAY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("ON", ov) { if (!busy) ov = true }
                    Pill("OFF", !ov) { if (!busy) ov = false }
                }
                Spacer(Modifier.height(6.dp))
                Text("Steam-style pop-ups inside the game. You can change both choices later from the game's options.", fontSize = 12.sp, color = cMu)
                Spacer(Modifier.height(24.dp))
                if (ok) Pill("DONE", accent = true) { done() }
                else if (!busy) Pill("SET UP", accent = true) {
                    busy = true; log.clear()
                    scope.launch(Dispatchers.IO) {
                        val say = { s: String -> scope.launch(Dispatchers.Main) { log.add(s) }; Unit }
                        try {
                            Gbe.install(c, p, pk!!, ch.first, ch.second, type, ov, say)
                            p.write("gbe_" + ch.first, type); p.write("ov_" + ch.first, if (ov) "1" else "0")
                            withContext(Dispatchers.Main) { ok = true }
                        } catch (e: Exception) { say("Failed: " + (e.message ?: e.toString())) }
                        withContext(Dispatchers.Main) { busy = false }
                    }
                } else Text("Working...", fontSize = 14.sp, fontFamily = headFont)
                log.forEach { Text(it, fontSize = 12.sp, color = cMu, modifier = Modifier.padding(top = 6.dp)) }
            }
            pk != null -> {
                Text(pk.name ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = headFont)
                Spacer(Modifier.height(6.dp))
                Text("Pick the matching Steam game.", fontSize = 13.sp, color = cMu)
                Spacer(Modifier.height(14.dp))
                val r = results
                if (r == null) Text("Searching...", fontSize = 14.sp, color = cMu)
                else r.forEach { hit ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).bp(0.dp) { chosen = hit }.background(cSf).padding(14.dp)) {
                        Text(hit.second, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = headFont)
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
                    f.forEachIndexed { i, d ->
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).appear("add", i).bp(0.dp) { picked = d }.background(cSf).padding(16.dp)) {
                            Text(d.name ?: "", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = headFont)
                        }
                    }
                }
            }
        }
    }
}
