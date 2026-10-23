package app.achievements

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (p.root.isBlank()) return
        scope.launch {
            games = withContext(Dispatchers.IO) { Scanner.list(c, p) }
            loaded = true
            val miss = games.filter { it.cover == null || it.hero == null }
            if (miss.isNotEmpty()) {
                withContext(Dispatchers.IO) { miss.forEach { Net.art(c, it, p.key) } }
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
        setup -> Form(p) { setup = false; sel = null; refresh() }
        g != null -> Detail(g) { sel = null }
        else -> Home(p, games, loaded, { sel = it }, { setup = true })
    }
}

@Composable
fun Form(p: Prefs, done: () -> Unit) {
    val c = LocalContext.current
    var name by remember { mutableStateOf(p.name) }
    var root by remember { mutableStateOf(p.root) }
    var key by remember { mutableStateOf(p.key) }
    var alerts by remember { mutableStateOf(p.alerts) }
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
        Label("SteamGridDB key (optional)")
        Field(key, "Used for cover art") { key = it }
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
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (ready) cAc else cHi)
                .clickable(enabled = ready) {
                    p.name = name.trim(); p.root = root; p.key = key.trim(); p.alerts = alerts
                    val svc = Intent(c, WatchService::class.java)
                    if (alerts) {
                        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        c.startForegroundService(svc)
                    } else c.stopService(svc)
                    done()
                }.padding(16.dp),
            contentAlignment = Alignment.Center
        ) { Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (ready) cBg else cMu) }
    }
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
fun Home(p: Prefs, games: List<Game>, loaded: Boolean, open: (String) -> Unit, settings: () -> Unit) {
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
fun Detail(g: Game, back: () -> Unit) {
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val list = remember(g) { g.achs.sortedWith(compareByDescending<Ach> { it.earned }.thenByDescending { it.time }) }
    val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(200.dp).background(cSf)) {
                AsyncImage(g.hero, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, cBg))))
                Text("Back", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clickable { back() }.padding(8.dp))
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
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).alpha(if (a.earned) 1f else 0.55f),
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
                    Text(if (hide) "Earn it to see what it is." else a.desc, fontSize = 13.sp, color = cMu)
                }
                if (a.earned && a.time > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(fmt.format(Date(a.time * 1000)), fontSize = 12.sp, color = cMu)
                }
            }
        }
    }
}
