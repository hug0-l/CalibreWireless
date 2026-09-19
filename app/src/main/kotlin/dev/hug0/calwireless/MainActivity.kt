package dev.hug0.calwireless

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.hug0.calwireless.ui.BorderDim
import dev.hug0.calwireless.ui.Canvas
import dev.hug0.calwireless.ui.DevicePanelTheme
import dev.hug0.calwireless.ui.Ink
import dev.hug0.calwireless.ui.InkDim
import dev.hug0.calwireless.ui.InkFaint
import dev.hug0.calwireless.ui.LogBg
import dev.hug0.calwireless.ui.Mono
import dev.hug0.calwireless.ui.Panel
import dev.hug0.calwireless.ui.Panel2
import dev.hug0.calwireless.ui.StatusLed
import dev.hug0.calwireless.ui.inkFor
import dev.hug0.calwireless.ui.ledFor
import dev.hug0.calwireless.DeviceConfig
import dev.hug0.calwireless.DeviceBookInfo
import dev.hug0.calwireless.saf.SafInboxStore
import android.content.ActivityNotFoundException
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

private data class Settings(
    val auto: Boolean = true,
    val host: String = "",
    val port: Int = 8135,
    val password: String = "",
    val name: String = "CalibreWireless",
    val tree: String = "",
    val formats: String = DeviceConfig.DEFAULT_FORMATS.joinToString(","),
    val packet: Int = 65536,
    val anim: Boolean = true,
) {
    fun ready() = tree.isNotEmpty() && (auto || host.isNotEmpty())
    fun folderName(): String =
        if (tree.isEmpty()) "" else Uri.parse(tree).lastPathSegment?.substringAfter(':').orEmpty()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.parseColor("#161518")
        window.navigationBarColor = AndroidColor.parseColor("#161518")
        setContent { DevicePanelTheme { MainScreen(this) } }
    }
}

private fun sans(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Ink, tracking: Float = 0f) =
    TextStyle(fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = tracking.sp)

private fun mono(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Ink) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = weight, color = color)

@Composable
private fun Chip(label: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(1.dp, BorderDim, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = sans(10, FontWeight.Medium, InkFaint, 1.2f))
        Spacer(Modifier.height(3.dp))
        Text(value, style = sans(13, FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun DeviceSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        animationSpec = tween(160),
        label = "thumb",
    )
    Box(
        Modifier.width((if (pressed) 54 else 56).dp).height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) Color(0xFF5F8266) else Color(0xFF2A292F))
            .border(1.dp, BorderDim, RoundedCornerShape(16.dp))
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = thumbOffset).size(26.dp).clip(CircleShape)
                .background(if (checked) Canvas else InkDim),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(context: Context) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Settings()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        context.dataStore.data.map { p ->
            Settings(
                auto = p[KeyAuto] ?: true,
                host = p[KeyHost] ?: "",
                port = p[KeyPort] ?: 8135,
                password = p[KeyPassword] ?: "",
                name = p[KeyName] ?: "CalibreWireless",
                tree = p[KeyTree] ?: "",
                formats = p[KeyFormats] ?: DeviceConfig.DEFAULT_FORMATS.joinToString(","),
                packet = p[KeyPacket] ?: 65536,
                anim = p[KeyAnim] ?: animatorOn(context),
            )
        }.collect { ns -> s = ns; loaded = true }
    }

    val status by DeviceState.status.collectAsState()
    val running by DeviceState.running.collectAsState()
    val library by DeviceState.library.collectAsState()
    val uuid by DeviceState.deviceUuid.collectAsState()
    val logs by DeviceState.logs.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    val savePrefs: (Settings) -> Unit = { ns ->
        scope.launch {
            context.dataStore.edit {
                it[KeyAuto] = ns.auto; it[KeyHost] = ns.host; it[KeyPort] = ns.port
                it[KeyPassword] = ns.password; it[KeyName] = ns.name; it[KeyTree] = ns.tree
                it[KeyFormats] = ns.formats; it[KeyPacket] = ns.packet; it[KeyAnim] = ns.anim
            }
        }
    }
    val startService: (Settings) -> Unit = { ns ->
        context.startForegroundService(Intent(context, WirelessService::class.java).apply {
            putExtra(WirelessService.EXTRA_AUTO, ns.auto)
            putExtra(WirelessService.EXTRA_HOST, ns.host)
            putExtra(WirelessService.EXTRA_PORT, ns.port)
            putExtra(WirelessService.EXTRA_PASSWORD, ns.password)
            putExtra(WirelessService.EXTRA_NAME, ns.name)
            putExtra(WirelessService.EXTRA_TREE, ns.tree)
            putExtra(WirelessService.EXTRA_FORMATS, ns.formats)
            putExtra(WirelessService.EXTRA_PACKET, ns.packet)
        })
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (loaded && s.ready()) startService(s)
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                DeviceState.log(R.string.log_saf_fail, e.message, LogKind.ERR)
            }
            val ns = s.copy(tree = uri.toString())
            savePrefs(ns)
            DeviceState.log(R.string.log_folder_set, ns.folderName(), LogKind.OK)
        }
    }

    val (ledColor, ledPulse) = ledFor(status)

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.header), style = sans(10, FontWeight.Medium, InkFaint, 3f))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(stringResource(R.string.tab_status))
            }
            SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(stringResource(R.string.tab_device))
            }
        }
        if (tab == 1) {
            DeviceTab(context, s)
        } else {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Panel).border(1.dp, BorderDim, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLed(ledColor, ledPulse && s.anim)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(statusText(context, status), style = sans(22, FontWeight.SemiBold))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.meta_line, library ?: "—", uuid?.take(8) ?: "—"),
                        style = mono(11, color = InkFaint),
                    )
                }
                DeviceSwitch(checked = running, onChange = { on ->
                    if (!loaded) return@DeviceSwitch
                    if (on) {
                        if (!s.ready()) {
                            DeviceState.log(R.string.log_need_setup, kind = LogKind.WARN)
                        } else if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startService(s)
                        }
                    } else {
                        context.startService(Intent(context, WirelessService::class.java).apply { action = WirelessService.ACTION_STOP })
                    }
                })
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Chip(
                        stringResource(R.string.lbl_inbox),
                        s.folderName().ifEmpty { stringResource(R.string.unconfigured) },
                    ) { treeLauncher.launch(null) }
                }
                Box(Modifier.weight(1f)) {
                    Chip(
                        stringResource(R.string.lbl_conn),
                        if (s.auto) stringResource(R.string.conn_auto) else "${s.host}:${s.port}",
                    ) { showSheet = true }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))
                .background(LogBg).border(1.dp, BorderDim, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.log_title), style = sans(10, FontWeight.Medium, InkFaint, 2f), modifier = Modifier.weight(1f))
                Text("%03d".format(logs.size), style = mono(10, color = InkFaint))
            }
            Spacer(Modifier.height(8.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }
            if (logs.isEmpty()) {
                Text(
                    stringResource(R.string.log_empty),
                    style = mono(11, color = InkFaint),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(logs) { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(line.time, style = mono(10, color = InkFaint))
                        val color = inkFor(line.kind)
                        Text(
                            text = if (line.res == 0) line.raw.orEmpty()
                            else if (line.arg != null) stringResource(line.res, line.arg)
                            else stringResource(line.res),
                            style = mono(11, color = color),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        }
    }

    if (showSheet) {
        var t by remember { mutableStateOf(s) }
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Panel2,
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = t.auto, onCheckedChange = { t = t.copy(auto = it) })
                    Text(stringResource(R.string.set_auto), style = sans(13, color = InkDim))
                }
                if (!t.auto) {
                    OutlinedTextField(t.host, { t = t.copy(host = it) }, label = { Text(stringResource(R.string.set_host)) }, singleLine = true)
                    OutlinedTextField(
                        t.port.toString(),
                        { t = t.copy(port = it.filter(Char::isDigit).toIntOrNull() ?: 0) },
                        label = { Text(stringResource(R.string.set_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    t.password, { t = t.copy(password = it) },
                    label = { Text(stringResource(R.string.set_password)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(t.name, { t = t.copy(name = it) }, label = { Text(stringResource(R.string.set_name)) }, singleLine = true)
                OutlinedTextField(
                    t.formats, { t = t.copy(formats = it) },
                    label = { Text(stringResource(R.string.set_formats)) },
                    textStyle = mono(12),
                )
                OutlinedTextField(
                    t.packet.toString(),
                    { t = t.copy(packet = it.filter(Char::isDigit).toIntOrNull() ?: 0) },
                    label = { Text(stringResource(R.string.set_packet)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = t.anim, onCheckedChange = { t = t.copy(anim = it) })
                    Text(stringResource(R.string.set_anim), style = sans(13, color = InkDim))
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showSheet = false }) { Text(stringResource(R.string.set_cancel)) }
                    Button(onClick = {
                        savePrefs(t)
                        s = t
                        showSheet = false
                        if (DeviceState.running.value) startService(t)
                    }) { Text(stringResource(R.string.set_save)) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private val KeyAuto = booleanPreferencesKey("auto")
private val KeyHost = stringPreferencesKey("host")
private val KeyPort = intPreferencesKey("port")
private val KeyPassword = stringPreferencesKey("password")
private val KeyName = stringPreferencesKey("name")
@Composable
private fun Cover(lpath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(lpath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(lpath) {
        withContext(Dispatchers.IO) {
            bmp = try {
                android.graphics.BitmapFactory.decodeFile(FileCoverSink(context).fileFor(lpath).absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(Panel2), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("書", style = sans(13, color = InkFaint))
    }
}

private fun humanSize(n: Long): String = when {
    n >= 1L shl 30 -> "%.1f GB".format(n / 1073741824f)
    n >= 1L shl 20 -> "%.1f MB".format(n / 1048576f)
    n >= 1024 -> "%.0f KB".format(n / 1024f)
    else -> "$n B"
}

@Composable
private fun DeviceTab(context: Context, s: Settings) {
    val scope = rememberCoroutineScope()
    val running by DeviceState.running.collectAsState()
    val live by DeviceState.books.collectAsState()
    var fileBooks by remember { mutableStateOf<List<DeviceBookInfo>>(emptyList()) }
    var cap by remember { mutableStateOf(0L to 0L) }
    var reload by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<DeviceBookInfo?>(null) }
    val tree = if (s.tree.isEmpty()) null else Uri.parse(s.tree)
    val books = if (running) live else fileBooks

    LaunchedEffect(running, reload, s.tree) {
        withContext(Dispatchers.IO) {
            if (tree != null) {
                val st = SafInboxStore(context, tree)
                cap = st.usableBytes() to st.totalBytes()
                if (!running) {
                    val db = DeviceBooks(st)
                    db.load()
                    fileBooks = db.infos()
                }
            } else {
                cap = 0L to 0L
                fileBooks = emptyList()
            }
        }
    }

    val sortLabel = when (sort) {
        0 -> stringResource(R.string.dev_sort_title)
        1 -> stringResource(R.string.dev_sort_author)
        2 -> stringResource(R.string.dev_sort_series)
        else -> stringResource(R.string.dev_sort_size)
    }
    val shown = books
        .filter {
            query.isEmpty() || it.title.contains(query, true) ||
                it.authors.contains(query, true) || it.lpath.contains(query, true)
        }
        .sortedWith(
            when (sort) {
                1 -> compareBy { it.authors }
                2 -> compareBy { it.series ?: it.title }
                3 -> compareByDescending { it.size }
                else -> compareBy { it.title }
            },
        )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tree == null) {
            Text(stringResource(R.string.dev_no_tree), style = sans(13, color = InkDim), modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dev_storage, humanSize(cap.second - cap.first), humanSize(cap.second)),
                style = mono(10, color = InkFaint), modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { DeviceState.resyncFun?.invoke() }, enabled = running) {
                Text(stringResource(R.string.dev_sync), style = sans(12))
            }
        }
        if (!running) Text(stringResource(R.string.dev_sync_hint), style = sans(10, color = InkFaint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it },
                label = { Text(stringResource(R.string.dev_search)) },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Box {
                OutlinedButton(onClick = { sortMenu = true }) { Text(sortLabel, style = sans(12)) }
                DropdownMenu(sortMenu, { sortMenu = false }, containerColor = Panel2) {
                    listOf(
                        stringResource(R.string.dev_sort_title) to 0,
                        stringResource(R.string.dev_sort_author) to 1,
                        stringResource(R.string.dev_sort_series) to 2,
                        stringResource(R.string.dev_sort_size) to 3,
                    ).forEach { (label, idx) ->
                        DropdownMenuItem(text = { Text(label, style = sans(13)) }, onClick = { sort = idx; sortMenu = false })
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.dev_empty), style = mono(11, color = InkFaint),
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp), textAlign = TextAlign.Center,
                    )
                }
            }
            items(shown) { b ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Panel).border(1.dp, BorderDim, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Cover(lpath = b.lpath, modifier = Modifier.size(width = 44.dp, height = 66.dp))
                        Column(Modifier.weight(1f)) {
                    Text(b.title, style = sans(15, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(b.authors.takeIf { it.isNotEmpty() }, b.series).joinToString(" · "),
                        style = sans(12, color = InkDim), maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.lpath.substringAfterLast('/') + "  " + humanSize(b.size), style = mono(10, color = InkFaint), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { tree?.let { openBook(context, it, b) } }) { Text(stringResource(R.string.dev_open), style = sans(12)) }
                        TextButton(onClick = { confirm = b }) { Text(stringResource(R.string.dev_delete), style = sans(12, color = inkFor(LogKind.ERR))) }
                    }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { b ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.dev_delete_q, b.title)) },
            text = { Text(stringResource(R.string.dev_delete_note), style = sans(13, color = InkDim)) },
            containerColor = Panel2,
            confirmButton = {
                Button(onClick = {
                    confirm = null
                    scope.launch(Dispatchers.IO) {
                        val ok = DeviceState.deleteFun?.invoke(b.lpath) ?: run {
                            if (tree != null) {
                                val st = SafInboxStore(context, tree)
                                val db = DeviceBooks(st)
                                db.load()
                                val hit = db.remove(b.lpath) != null
                                st.delete(b.lpath)
                                db.save()
                                hit
                            } else false
                        }
                        if (ok) DeviceState.log(R.string.ev_deleted, b.lpath, LogKind.OK)
                        reload++
                    }
                }) { Text(stringResource(R.string.dev_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.set_cancel)) } },
        )
    }
}

private fun openBook(context: Context, tree: Uri, b: DeviceBookInfo) {
    val uri = SafInboxStore(context, tree).uriFor(b.lpath)
    if (uri == null) {
        DeviceState.log(R.string.dev_open_fail, b.lpath, LogKind.ERR)
        return
    }
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(b.lpath.substringAfterLast('.').lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, b.title))
    } catch (e: ActivityNotFoundException) {
        DeviceState.log(R.string.dev_open_fail, b.lpath, LogKind.ERR)
    }
}

private val KeyTree = stringPreferencesKey("tree")
private val KeyFormats = stringPreferencesKey("formats")
private val KeyPacket = intPreferencesKey("packet")
private val KeyAnim = booleanPreferencesKey("anim")

fun animatorOn(context: Context): Boolean = try {
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
} catch (e: Exception) {
    true
}
