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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
            )
        }.collect { ns -> s = ns; loaded = true }
    }

    val status by DeviceState.status.collectAsState()
    val running by DeviceState.running.collectAsState()
    val library by DeviceState.library.collectAsState()
    val uuid by DeviceState.deviceUuid.collectAsState()
    val logs by DeviceState.logs.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    val savePrefs: (Settings) -> Unit = { ns ->
        scope.launch {
            context.dataStore.edit {
                it[KeyAuto] = ns.auto; it[KeyHost] = ns.host; it[KeyPort] = ns.port
                it[KeyPassword] = ns.password; it[KeyName] = ns.name; it[KeyTree] = ns.tree
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

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Panel).border(1.dp, BorderDim, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLed(ledColor, ledPulse)
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

    if (showSheet) {
        var t by remember { mutableStateOf(s) }
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Panel2,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
private val KeyTree = stringPreferencesKey("tree")
