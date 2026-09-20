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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.rememberCoroutineScope
import dev.hug0.calwireless.ui.LocalTextScale
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
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
import dev.hug0.calwireless.ui.paletteFor
import dev.hug0.calwireless.ui.darkPalette
import dev.hug0.calwireless.ui.LocalPanelColors
import dev.hug0.calwireless.ui.LocalTextScale
import dev.hug0.calwireless.ui.pal
import dev.hug0.calwireless.ui.ruleW
import dev.hug0.calwireless.ui.CardBorder
import dev.hug0.calwireless.ui.SegSelBg
import dev.hug0.calwireless.ui.SegSelFg
import dev.hug0.calwireless.ui.SegUnselBg
import dev.hug0.calwireless.DeviceConfig
import dev.hug0.calwireless.DeviceBookInfo
import dev.hug0.calwireless.EpubCover
import dev.hug0.calwireless.saf.SafInboxStore
import android.content.ActivityNotFoundException
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.localized())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen(this) }
    }
}

@Composable
@ReadOnlyComposable
private fun sans(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Ink, tracking: Float = 0f) =
    TextStyle(fontSize = (size * LocalTextScale.current).sp, fontWeight = weight, color = color, letterSpacing = tracking.sp)

@Composable
@ReadOnlyComposable
private fun mono(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Ink) =
    TextStyle(fontFamily = Mono, fontSize = (size * LocalTextScale.current).sp, fontWeight = weight, color = color)

@Composable
private fun Chip(label: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(ruleW(), CardBorder, RoundedCornerShape(10.dp))
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
            .background(if (checked) pal().switchOn else pal().switchOff)
            .border(ruleW(), CardBorder, RoundedCornerShape(16.dp))
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
                .background(if (checked) pal().switchThumbOn else pal().switchThumbOff),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryPicker(context: Context, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var path by remember { mutableStateOf(android.os.Environment.getExternalStorageDirectory().absolutePath) }
    var dirs by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(path) {
        loading = true
        dirs = withContext(Dispatchers.IO) {
            try {
                java.io.File(path).listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                    ?.map { it.absolutePath }?.sorted() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = path != "/storage" && path != "/", onClick = { path = path.substringBeforeLast('/') }) {
                    Text("←", style = sans(18, color = InkDim))
                }
                Text(path, style = mono(11, color = InkFaint), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.pick_folder), style = sans(11, FontWeight.Medium, InkFaint, 2f))
            val noPerm = Build.VERSION.SDK_INT >= 30 && try {
                !android.os.Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
            if (noPerm) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(pal().panel2).border(ruleW(), CardBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.perm_needed), style = sans(12, color = inkFor(LogKind.WARN)), modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)),
                            )
                        } catch (e: Exception) {
                            DeviceState.log(R.string.hint_adb_grant, kind = LogKind.WARN)
                        }
                    }) { Text(stringResource(R.string.btn_grant_allfiles), style = sans(12, color = InkDim)) }
                }
            }
            LazyColumn(Modifier.weight(1f, false).heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (loading) {
                    item { Text("…", style = sans(14, color = InkFaint)) }
                } else if (dirs.isEmpty()) {
                    item { Text(stringResource(R.string.no_subdirs), style = sans(13, color = InkFaint)) }
                }
                items(dirs) { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Panel2).border(ruleW(), CardBorder, RoundedCornerShape(10.dp))
                            .clickable { path = d }.padding(horizontal = 14.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("▸", style = sans(14, color = InkDim))
                        Text(d.substringAfterLast('/'), style = sans(15, color = Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(
                onClick = { onPick(path) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = pal().switchOn, contentColor = pal().switchThumbOn),
            ) {
                Text(stringResource(R.string.use_this, path.substringAfterLast('/')), style = sans(14, FontWeight.SemiBold))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainScreen(context: Context) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Settings()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        context.dataStore.data.map { settingsFrom(it, context) }.collect { ns -> s = ns; loaded = true }
    }

    val status by DeviceState.status.collectAsState()
    val running by DeviceState.running.collectAsState()
    val library by DeviceState.library.collectAsState()
    val uuid by DeviceState.deviceUuid.collectAsState()
    val logs by DeviceState.logs.collectAsState()
    val bookCount by DeviceState.booksCount.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var showDirPicker by remember { mutableStateOf(false) }

    val savePrefs: (Settings) -> Unit = { ns -> scope.launch(Dispatchers.IO) { saveSettings(context, ns) } }
    val startService: (Settings) -> Unit = { ns -> context.startForegroundService(serviceIntent(context, ns)) }
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
    val palette = paletteFor(s.themeMode, isSystemInDarkTheme())
    val scale = listOf(1f, 1.22f, 1.5f)[s.textScale.coerceIn(0, 2)]
    val view = LocalView.current
    LaunchedEffect(palette) {
        (view.context as? ComponentActivity)?.window?.let { w ->
            val dark = palette === darkPalette()
            w.statusBarColor = android.graphics.Color.parseColor(if (dark) "#161518" else "#FFFFFF")
            w.navigationBarColor = w.statusBarColor
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPanelColors provides palette,
        LocalTextScale provides scale,
    ) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.header), style = sans(10, FontWeight.Medium, InkFaint, 3f))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val segColors = SegmentedButtonDefaults.colors(
                activeContainerColor = Panel2,
                activeContentColor = Ink,
                inactiveContainerColor = SegUnselBg,
                inactiveContentColor = InkFaint,
            )
            SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2), colors = segColors) {
                Text(stringResource(R.string.tab_status), style = sans(12, FontWeight.Medium))
            }
            SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2), colors = segColors) {
                Text(
                    if (bookCount > 0) "${stringResource(R.string.tab_device)} ${bookCount}" else stringResource(R.string.tab_device),
                    style = sans(12, FontWeight.Medium),
                )
            }
        }
        Crossfade(targetState = tab, modifier = Modifier.weight(1f), label = "tab") { cur ->
        if (cur == 1) {
            DeviceTab(context, s)
        } else Column(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Panel).border(ruleW(), CardBorder, RoundedCornerShape(14.dp))
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
                    ) {
                        try { treeLauncher.launch(null) } catch (e: ActivityNotFoundException) {
                            showDirPicker = true
                        }
                    }
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
                .background(LogBg).border(ruleW(), CardBorder, RoundedCornerShape(12.dp))
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
    }

    if (showDirPicker) {
        DirectoryPicker(context, onDismiss = { showDirPicker = false }, onPick = { pth ->
            showDirPicker = false
            val ns = s.copy(folderPath = pth)
            savePrefs(ns)
            s = ns
            DeviceState.log(R.string.log_folder_set, ns.folderName(), LogKind.OK)
        })
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
                SectionHeader(stringResource(R.string.sec_conn))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelSwitch(checked = t.auto, onCheckedChange = { t = t.copy(auto = it) })
                    Text(stringResource(R.string.set_auto), style = sans(13, color = InkDim))
                }
                if (!t.auto) {
                    var discovering by remember { mutableStateOf(false) }
                    var discovered by remember { mutableStateOf<List<CalibreServer>?>(null) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            t.host, { t = t.copy(host = it) },
                            label = { Text(stringResource(R.string.set_host)) },
                            singleLine = true, modifier = Modifier.weight(1f),
                            colors = fieldColors(),
                        )
                        OutlinedTextField(
                            t.port.toString(),
                            { t = t.copy(port = it.filter(Char::isDigit).toIntOrNull() ?: 0) },
                            label = { Text(stringResource(R.string.set_port_short)) },
                            singleLine = true, modifier = Modifier.width(110.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(),
                        )
                    }
                    TextButton(onClick = {
                        discovering = true
                        scope.launch(Dispatchers.IO) {
                            discovered = Discover.hello(1500)?.let { listOf(it) } ?: emptyList()
                            discovering = false
                        }
                    }) {
                        Text(stringResource(if (discovering) R.string.disc_scanning else R.string.disc_scan), style = sans(12, color = InkDim))
                    }
                    discovered?.let { list ->
                        if (list.isEmpty()) {
                            Text(stringResource(R.string.disc_none), style = sans(11, color = inkFor(LogKind.WARN)))
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { srv ->
                                    val on = t.host == srv.address && t.port == srv.tcpPort
                                    OutlinedButton(
                                        onClick = { t = t.copy(host = srv.address, port = srv.tcpPort) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            "${srv.name} · ${srv.address}:${srv.tcpPort}",
                                            style = mono(11, color = if (on) pal().sage else InkDim),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    t.password, { t = t.copy(password = it) },
                    label = { Text(stringResource(R.string.set_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    t.name, { t = t.copy(name = it) },
                    label = { Text(stringResource(R.string.set_name)) },
                    singleLine = true, colors = fieldColors(),
                )
                OutlinedTextField(
                    t.folderPath, { t = t.copy(folderPath = it.trim()) },
                    label = { Text(stringResource(R.string.set_path)) },
                    singleLine = true, colors = fieldColors(),
                )
                if (t.folderPath.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                    val granted = try { android.os.Environment.isExternalStorageManager() } catch (e: Exception) { true }
                    if (!granted) {
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)),
                                )
                            } catch (e: Exception) {
                                DeviceState.log(R.string.hint_adb_grant, kind = LogKind.WARN)
                            }
                        }) { Text(stringResource(R.string.btn_grant_allfiles), style = sans(12, color = InkDim)) }
                    }
                }
                SectionHeader(stringResource(R.string.sec_appearance))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        stringResource(R.string.theme_system) to 0,
                        stringResource(R.string.theme_light) to 1,
                        stringResource(R.string.theme_dark) to 2,
                        stringResource(R.string.theme_eink) to 3,
                    ).forEach { (label, v) ->
                        val on = t.themeMode == v
                        OutlinedButton(
                            onClick = { savePrefs(t.copy(themeMode = v)); s = t.copy(themeMode = v) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text(label, style = sans(12, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) Ink else InkFaint)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        stringResource(R.string.ts_normal) to 0,
                        stringResource(R.string.ts_large) to 1,
                        stringResource(R.string.ts_xl) to 2,
                    ).forEach { (label, v) ->
                        val on = t.textScale == v
                        OutlinedButton(
                            onClick = { savePrefs(t.copy(textScale = v)); s = t.copy(textScale = v) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text(label, style = sans(12, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) Ink else InkFaint)) }
                    }
                }
                SectionHeader(stringResource(R.string.sec_lang))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "system" to stringResource(R.string.lang_system),
                        "zh" to stringResource(R.string.lang_zh),
                        "en" to stringResource(R.string.lang_en),
                    ).forEach { (code, label) ->
                        val on = t.lang == code
                        OutlinedButton(onClick = {
                            if (!on) {
                                savePrefs(t.copy(lang = code))
                                s = t.copy(lang = code)
                                (context as? ComponentActivity)?.recreate()
                            }
                        }) {
                            Text(label, style = sans(12, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) Ink else InkFaint))
                        }
                    }
                }
                SectionHeader(stringResource(R.string.sec_sync))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelSwitch(checked = t.harvest, onCheckedChange = { t = t.copy(harvest = it) })
                    Text(stringResource(R.string.set_harvest), style = sans(13, color = InkDim))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelSwitch(checked = t.autoStart, onCheckedChange = { t = t.copy(autoStart = it) })
                    Text(stringResource(R.string.set_autostart), style = sans(13, color = InkDim))
                }
                val boolOpts by DeviceState.cols.collectAsState()
                val dateOpts by DeviceState.dateCols.collectAsState()
                ColPicker(
                    label = stringResource(R.string.set_read_col),
                    value = t.readCol, options = boolOpts,
                    onSelect = { t = t.copy(readCol = it) },
                )
                ColPicker(
                    label = stringResource(R.string.set_date_col),
                    value = t.dateCol, options = dateOpts,
                    onSelect = { t = t.copy(dateCol = it) },
                )
                SectionHeader(stringResource(R.string.sec_adv))
                var adv by remember { mutableStateOf(false) }
                TextButton(onClick = { adv = !adv }) {
                    Text(
                        (if (adv) "▾ " else "▸ ") + stringResource(R.string.sec_adv_toggle),
                        style = sans(12, color = InkDim),
                    )
                }
                if (adv) {
                    Text(stringResource(R.string.set_formats), style = sans(11, color = InkFaint))
                    val fmtSet = t.formats.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DeviceConfig.DEFAULT_FORMATS.forEach { f ->
                            val on = f in fmtSet
                            OutlinedButton(
                                onClick = {
                                    val next = if (on) fmtSet - f else fmtSet + f
                                    t = t.copy(formats = DeviceConfig.DEFAULT_FORMATS.filter { it in next }.joinToString(","))
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(f, style = mono(11, color = if (on) pal().sage else InkFaint))
                            }
                        }
                    }
                    Text(stringResource(R.string.hint_fmt_first), style = mono(10, color = InkFaint))
                    OutlinedTextField(
                        t.packet.toString(),
                        { t = t.copy(packet = it.filter(Char::isDigit).toIntOrNull() ?: 0) },
                        label = { Text(stringResource(R.string.set_packet)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PanelSwitch(checked = t.anim, onCheckedChange = { t = t.copy(anim = it) })
                        Text(stringResource(R.string.set_anim), style = sans(13, color = InkDim))
                    }
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
}

@Composable
private fun Cover(lpath: String, store: InboxStore?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(lpath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(lpath) {
        withContext(Dispatchers.IO) {
            val sink = FileCoverSink(context)
            bmp = try {
                val f = sink.fileFor(lpath)
                if (!f.exists() && store != null) {
                    EpubCover.extract(store, lpath)?.let { sink.putRaw(lpath, it) }
                }
                android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
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

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    disabledTextColor = InkFaint,
    focusedContainerColor = pal().canvas,
    unfocusedContainerColor = pal().canvas,
    cursorColor = pal().sage,
    focusedBorderColor = pal().switchOn,
    unfocusedBorderColor = BorderDim,
    focusedLabelColor = InkDim,
    unfocusedLabelColor = InkFaint,
)

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = sans(10, FontWeight.Medium, InkFaint, 2f), modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun PanelSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked, onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = pal().switchThumbOn,
            checkedTrackColor = pal().sage,
            uncheckedThumbColor = pal().switchThumbOff,
            uncheckedTrackColor = pal().switchOff,
            uncheckedBorderColor = CardBorder,
        ),
    )
}

private fun humanSize(n: Long): String = when {
    n >= 1L shl 30 -> "%.1f GB".format(n / 1073741824f)
    n >= 1L shl 20 -> "%.1f MB".format(n / 1048576f)
    n >= 1024 -> "%.0f KB".format(n / 1024f)
    else -> "$n B"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var filterMenu by remember { mutableStateOf(false) }
    var filterRead by remember { mutableStateOf<Boolean?>(null) }
    var filterExt by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<DeviceBookInfo?>(null) }
    var actionItem by remember { mutableStateOf<DeviceBookInfo?>(null) }
    var showAnalysis by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(s.view) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var confirmBulk by remember { mutableStateOf(false) }
    val store = remember(s.tree, s.folderPath) { buildStore(context, s) }
    val books = if (running) live else fileBooks
    LaunchedEffect(s.view) { view = s.view }
    LaunchedEffect(books.size) { DeviceState.booksCount.value = books.size }

    LaunchedEffect(running, reload, s.tree) {
        withContext(Dispatchers.IO) {
            if (store != null) {
                cap = store.usableBytes() to store.totalBytes()
                if (!running) {
                    val db = DeviceBooks(store)
                    db.load()
                    if (s.harvest) {
                        db.harvest(DeviceConfig.parseFormats(s.formats).map { it.lowercase() }.toSet())
                    }
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
    val filtering = filterRead != null || filterExt != null
    val shown = books
        .filter {
            (query.isEmpty() || it.title.contains(query, true) ||
                it.authors.contains(query, true) || it.lpath.contains(query, true)) &&
                (filterRead == null || (it.isRead == true) == filterRead) &&
                (filterExt == null || it.lpath.substringAfterLast('.', "").equals(filterExt, true))
        }
        .sortedWith(
            when (sort) {
                1 -> compareBy { it.authors }
                2 -> compareBy { it.series ?: it.title }
                3 -> compareByDescending { it.size }
                else -> compareBy { it.title }
            },
        )

    fun applyRead(lpath: String, read: Boolean) {
        scope.launch(Dispatchers.IO) {
            val ok = DeviceState.markFun?.invoke(lpath, read) ?: run {
                if (store != null) {
                    val st = store
                    val db = DeviceBooks(st)
                    db.load()
                    val hit = db.uuidOf(lpath) != "none"
                    if (hit) {
                        db.setRead(lpath, if (read) true else null)
                        db.save()
                    }
                    hit
                } else false
            }
            if (ok) reload++
        }
    }
    fun applyDelete(lpath: String) {
        scope.launch(Dispatchers.IO) {
            val ok = DeviceState.deleteFun?.invoke(lpath) ?: run {
                if (store != null) {
                    val st = store
                    val db = DeviceBooks(st)
                    db.load()
                    val hit = db.remove(lpath) != null
                    if (hit) {
                        st.delete(lpath)
                        db.save()
                    }
                    hit
                } else false
            }
            if (ok) DeviceState.log(R.string.ev_deleted, lpath, LogKind.OK)
            reload++
        }
    }
    fun toggleRead(b: DeviceBookInfo) = applyRead(b.lpath, b.isRead != true)
    fun deleteBook(b: DeviceBookInfo) = applyDelete(b.lpath)
    fun fmtLine(b: DeviceBookInfo) = b.lpath.substringAfterLast('.', "").uppercase() + " · " + humanSize(b.size)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (store == null) {
            Text(stringResource(R.string.dev_no_tree), style = sans(13, color = InkDim), modifier = Modifier.padding(top = 24.dp))
            return@Column
        }

        // 多選列
        if (sel.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Panel2).border(ruleW(), CardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sel_count, sel.size), style = mono(12, color = Ink), modifier = Modifier.weight(1f))
                TextButton(onClick = { sel.forEach { applyRead(it, true) }; sel = emptySet() }) {
                    Text(stringResource(R.string.dev_mark), style = sans(12))
                }
                TextButton(onClick = { confirmBulk = true }) {
                    Text(stringResource(R.string.dev_delete), style = sans(12, color = inkFor(LogKind.ERR)))
                }
                TextButton(onClick = { sel = emptySet() }) { Text(stringResource(R.string.set_cancel), style = sans(12, color = InkDim)) }
            }
        }

        // 存儲條（點開分析）
        if (cap.second > 0) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Panel).border(ruleW(), CardBorder, RoundedCornerShape(10.dp))
                    .clickable { showAnalysis = true }.padding(12.dp),
            ) {
                Row {
                    Text(
                        stringResource(R.string.dev_storage, humanSize(cap.second - cap.first), humanSize(cap.second)),
                        style = mono(10, color = InkFaint), modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.dev_count, books.size), style = mono(10, color = InkDim))
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                        .background(pal().panel2),
                ) {
                    Box(
                        Modifier.fillMaxWidth(((cap.second - cap.first).toFloat() / cap.second).coerceIn(0.02f, 1f))
                            .height(5.dp).clip(CircleShape).background(pal().switchOn),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it },
                label = { Text(stringResource(R.string.dev_search)) },
                singleLine = true, modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
            Box {
                OutlinedButton(
                    onClick = { filterMenu = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (filtering) pal().sage else InkDim),
                ) { Text("⚡", style = sans(12)) }
                DropdownMenu(filterMenu, { filterMenu = false }, containerColor = Panel2) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fil_all), style = sans(13, color = if (filterRead == null) Ink else InkDim)) },
                        onClick = { filterRead = null; filterMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fil_read), style = sans(13, color = if (filterRead == true) pal().sage else InkDim)) },
                        onClick = { filterRead = true; filterMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fil_unread), style = sans(13, color = if (filterRead == false) pal().sage else InkDim)) },
                        onClick = { filterRead = false; filterMenu = false },
                    )
                    androidx.compose.material3.HorizontalDivider(color = BorderDim)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fil_fmt_all), style = sans(13, color = if (filterExt == null) Ink else InkDim)) },
                        onClick = { filterExt = null; filterMenu = false },
                    )
                    books.map { it.lpath.substringAfterLast('.', "").lowercase() }.filter { it.isNotEmpty() }
                        .distinct().sorted().forEach { ext ->
                            DropdownMenuItem(
                                text = { Text(ext.uppercase(), style = sans(13, color = if (filterExt == ext) pal().sage else InkDim)) },
                                onClick = { filterExt = if (filterExt == ext) null else ext; filterMenu = false },
                            )
                        }
                }
            }
            Box {
                OutlinedButton(onClick = { sortMenu = true }) { Text(sortLabel, style = sans(12, color = InkDim)) }
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
            TextButton(onClick = {
                val next = (view + 1) % 3
                view = next
                scope.launch(Dispatchers.IO) { saveSettings(context, s.copy(view = next)) }
            }) { Text(listOf("☰", "▦", "▤")[view], style = sans(16, color = InkDim)) }
        }

        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.dev_empty), style = mono(11, color = InkFaint),
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp), textAlign = TextAlign.Center,
            )
        } else if (view == 1) {
            LazyVerticalGrid(
                GridCells.Adaptive(128.dp), Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown) { b ->
                    val selected = b.lpath in sel
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Panel2 else Panel)
                            .border(ruleW(), if (selected) pal().ink else CardBorder, RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { if (sel.isNotEmpty()) { if (b.lpath in sel) sel -= b.lpath else sel += b.lpath } else actionItem = b },
                                onLongClick = { sel = setOf(b.lpath) },
                            )
                            .padding(8.dp),
                    ) {
                        Box {
                            Cover(lpath = b.lpath, store = store, modifier = Modifier.fillMaxWidth().height(150.dp))
                            if (selected) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).clip(CircleShape)
                                        .background(pal().switchOn),
                                    contentAlignment = Alignment.Center,
                                ) { Text("✓", style = sans(11, FontWeight.Bold, pal().switchThumbOn)) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            (if (b.isRead == true) "✓ " else "") + b.title,
                            style = sans(12, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(fmtLine(b), style = mono(10, color = InkFaint))
                    }
                }
            }
        } else if (view == 2) {
            val groups = shown.groupBy { b -> b.series?.takeIf { it.isNotBlank() } }
                .toList().sortedWith(compareBy(nullsLast()) { it.first })
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { (series, list) ->
                    item(key = "hdr_${series ?: "_"}") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                            Text(
                                series ?: stringResource(R.string.grp_none),
                                style = sans(13, FontWeight.SemiBold, if (series != null) Ink else InkFaint),
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text("${list.size}", style = mono(10, color = InkFaint))
                        }
                    }
                    items(list.sortedBy { it.seriesIndex ?: Double.MAX_VALUE }, key = { "g_" + it.lpath }) { b ->
                        BookRowCard(
                            b, b.lpath in sel, store,
                            onClick = { if (sel.isNotEmpty()) { sel = if (b.lpath in sel) sel - b.lpath else sel + b.lpath } else actionItem = b },
                            onLongClick = { sel = setOf(b.lpath) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
            ) {
                items(shown, key = { "l_" + it.lpath }) { b ->
                    BookRowCard(
                        b, b.lpath in sel, store,
                        onClick = { if (sel.isNotEmpty()) { sel = if (b.lpath in sel) sel - b.lpath else sel + b.lpath } else actionItem = b },
                        onLongClick = { sel = setOf(b.lpath) },
                    )
                }
            }
        }

    }

    // 網格動作卡
    actionItem?.let { b ->
        AlertDialog(
            onDismissRequest = { actionItem = null },
            title = { Text(b.title, style = sans(16, FontWeight.Medium)) },
            text = {
                Column {
                    Text(listOfNotNull(b.authors, b.series).joinToString(" · "), style = sans(12, color = InkDim))
                    Text(b.lpath + "  " + humanSize(b.size), style = mono(10, color = InkFaint))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { actionItem = null; openBook(context, s, b) }) { Text(stringResource(R.string.dev_open), style = sans(12, color = InkDim)) }
                        TextButton(onClick = { actionItem = null; toggleRead(b) }) {
                            Text(stringResource(if (b.isRead == true) R.string.dev_unmark else R.string.dev_mark), style = sans(12, color = InkDim))
                        }
                        TextButton(onClick = { actionItem = null; confirm = b }) {
                            Text(stringResource(R.string.dev_delete), style = sans(12, color = inkFor(LogKind.ERR)))
                        }
                    }
                }
            },
            containerColor = Panel2,
            titleContentColor = Ink,
            textContentColor = InkDim,
            confirmButton = {},
            dismissButton = { TextButton(onClick = { actionItem = null }) { Text(stringResource(R.string.set_cancel)) } },
        )
    }

    confirm?.let { b ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.dev_delete_q, b.title)) },
            text = { Text(stringResource(R.string.dev_delete_note), style = sans(13, color = InkDim)) },
            containerColor = Panel2,
            titleContentColor = Ink,
            textContentColor = InkDim,
            confirmButton = {
                Button(onClick = { confirm = null; deleteBook(b) }) { Text(stringResource(R.string.dev_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.set_cancel)) } },
        )
    }

    if (confirmBulk) {
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = { Text(stringResource(R.string.sel_delete_q, sel.size)) },
            text = { Text(stringResource(R.string.dev_delete_note), style = sans(13, color = InkDim)) },
            containerColor = Panel2,
            titleContentColor = Ink,
            textContentColor = InkDim,
            confirmButton = {
                Button(onClick = {
                    confirmBulk = false
                    sel.forEach { applyDelete(it) }
                    sel = emptySet()
                }) { Text(stringResource(R.string.dev_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text(stringResource(R.string.set_cancel)) } },
        )
    }

    // 存儲分析
    if (showAnalysis) {
        val totalBooks = books.sumOf { it.size }
        val byType = books.groupBy { it.lpath.substringAfterLast('.', "?").lowercase() }
            .mapValues { entry -> entry.value.sumOf { it.size } }
            .toList().sortedByDescending { it.second }
        val top5 = books.sortedByDescending { it.size }.take(5)
        ModalBottomSheet(
            onDismissRequest = { showAnalysis = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Panel2,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.st_analysis), style = sans(11, FontWeight.Medium, InkFaint, 2f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(humanSize(cap.second - cap.first), style = mono(30, FontWeight.SemiBold))
                    Spacer(Modifier.width(8.dp))
                    Text("/ " + humanSize(cap.second), style = mono(13, color = InkFaint))
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.st_books_total, books.size, humanSize(totalBooks)), style = mono(11, color = InkDim))
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.st_by_type), style = sans(11, FontWeight.Medium, InkFaint, 2f))
                byType.forEach { (ext, sz) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(ext.uppercase(), style = mono(10, color = InkDim), modifier = Modifier.width(52.dp))
                        LinearProgressIndicator(
                            progress = { if (totalBooks > 0) sz.toFloat() / totalBooks else 0f },
                            modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape),
                            color = pal().switchOn,
                            trackColor = pal().panel2,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Text(humanSize(sz), style = mono(10, color = InkDim), modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.st_top5), style = sans(11, FontWeight.Medium, InkFaint, 2f))
                top5.forEach { b ->
                    Row {
                        Text(b.title, style = sans(12, color = InkDim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(humanSize(b.size), style = mono(10, color = InkFaint))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRowCard(
    b: DeviceBookInfo,
    selected: Boolean,
    store: InboxStore?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Panel2 else Panel)
            .border(ruleW(), if (selected) pal().ink else CardBorder, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Cover(lpath = b.lpath, store = store, modifier = Modifier.size(width = 44.dp, height = 66.dp))
        Column(Modifier.weight(1f)) {
            Text((if (b.isRead == true) "✓ " else "") + b.title, style = sans(15, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(b.authors.takeIf { it.isNotEmpty() }, b.series).joinToString(" · "),
                style = sans(12, color = InkDim), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(b.lpath.substringAfterLast('.', "").uppercase() + " · " + humanSize(b.size), style = mono(10, color = InkFaint))
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.CenterVertically).size(20.dp).clip(CircleShape)
                    .background(pal().switchOn),
                contentAlignment = Alignment.Center,
            ) { Text("✓", style = sans(12, FontWeight.Bold, pal().switchThumbOn)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColPicker(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Text(label, style = sans(11, color = InkFaint))
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { menu = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    value.ifEmpty { stringResource(R.string.col_off) }.removePrefix("#"),
                    style = mono(12, color = if (value.isEmpty()) InkFaint else pal().sage),
                )
            }
            DropdownMenu(menu, { menu = false }, containerColor = Panel2) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.col_off), style = sans(13, color = if (value.isEmpty()) Ink else InkDim)) },
                    onClick = { onSelect(""); menu = false },
                )
                val items = if (value.isNotEmpty() && value !in options) options + value else options
                items.forEach { col ->
                    DropdownMenuItem(
                        text = { Text(col.removePrefix("#"), style = mono(13, color = if (col == value) pal().sage else InkDim)) },
                        onClick = { onSelect(col); menu = false },
                    )
                }
            }
        }
    }
}

private fun openBook(context: Context, s: Settings, b: DeviceBookInfo) {
    val uri = try {
        when {
            s.tree.isNotEmpty() -> SafInboxStore(context, Uri.parse(s.tree)).uriFor(b.lpath)
            s.folderPath.isNotEmpty() -> FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider",
                java.io.File(java.io.File(s.folderPath), b.lpath),
            )
            else -> null
        }
    } catch (e: Exception) {
        null
    }
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

