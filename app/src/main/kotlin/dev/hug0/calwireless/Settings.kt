package dev.hug0.calwireless

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.hug0.calwireless.saf.SafInboxStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal val Context.dataStore by preferencesDataStore(name = "settings")

internal data class Settings(
    val auto: Boolean = true,
    val host: String = "",
    val port: Int = 8135,
    val password: String = "",
    val name: String = "CalibreWireless",
    val tree: String = "",
    val formats: String = DeviceConfig.DEFAULT_FORMATS.joinToString(","),
    val packet: Int = 65536,
    val anim: Boolean = true,
    val readCol: String = "",
    val dateCol: String = "",
    val autoStart: Boolean = false,
    val harvest: Boolean = true,
    val grid: Boolean = false,
    val view: Int = 0,
    val lang: String = "system",
    val folderPath: String = "",
    val themeMode: Int = 0,
    val textScale: Int = 0,
) {
    fun ready() = (tree.isNotEmpty() || folderPath.isNotEmpty()) && (auto || host.isNotEmpty())
    fun folderName(): String = when {
        tree.isNotEmpty() -> Uri.parse(tree).lastPathSegment?.substringAfter(':').orEmpty()
        folderPath.isNotEmpty() -> folderPath.trimEnd('/').substringAfterLast('/')
        else -> ""
    }
}

internal val KeyAuto = booleanPreferencesKey("auto")
internal val KeyHost = stringPreferencesKey("host")
internal val KeyPort = intPreferencesKey("port")
internal val KeyPassword = stringPreferencesKey("password")
internal val KeyName = stringPreferencesKey("name")
internal val KeyTree = stringPreferencesKey("tree")
internal val KeyFormats = stringPreferencesKey("formats")
internal val KeyPacket = intPreferencesKey("packet")
internal val KeyAnim = booleanPreferencesKey("anim")
internal val KeyReadCol = stringPreferencesKey("read_col")
internal val KeyDateCol = stringPreferencesKey("date_col")
internal val KeyAutoStart = booleanPreferencesKey("auto_start")
internal val KeyHarvest = booleanPreferencesKey("harvest")
internal val KeyGrid = booleanPreferencesKey("grid")
internal val KeyLang = stringPreferencesKey("lang")
internal val KeyPath = stringPreferencesKey("folder_path")
internal val KeyTheme = intPreferencesKey("theme")
internal val KeyTextScale = intPreferencesKey("text_scale")
internal val KeyView = intPreferencesKey("view")

internal fun animatorOn(context: Context): Boolean = try {
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
} catch (e: Exception) {
    true
}

internal fun settingsFrom(p: androidx.datastore.preferences.core.Preferences, context: Context) = Settings(
    auto = p[KeyAuto] ?: true,
    host = p[KeyHost] ?: "",
    port = p[KeyPort] ?: 8135,
    password = p[KeyPassword] ?: "",
    name = p[KeyName] ?: "CalibreWireless",
    tree = p[KeyTree] ?: "",
    formats = p[KeyFormats] ?: DeviceConfig.DEFAULT_FORMATS.joinToString(","),
    packet = p[KeyPacket] ?: 65536,
    anim = p[KeyAnim] ?: animatorOn(context),
    readCol = p[KeyReadCol] ?: "",
    dateCol = p[KeyDateCol] ?: "",
    autoStart = p[KeyAutoStart] ?: false,
    harvest = p[KeyHarvest] ?: true,
    grid = p[KeyGrid] ?: false,
    lang = p[KeyLang] ?: "system",
    view = p[KeyView] ?: if (p[KeyGrid] == true) 1 else 0,
    folderPath = p[KeyPath] ?: "",
    themeMode = p[KeyTheme] ?: 0,
    textScale = p[KeyTextScale] ?: 0,
)

/** 依 app 內語言設定包一層 localized context；"system" 則原樣 */
internal fun Context.localized(lang: String = readLangBlocking(this)): Context {
    val locale = when (lang) {
        "zh" -> java.util.Locale("zh", "TW")
        "en" -> java.util.Locale.ENGLISH
        else -> return this
    }
    val cfg = android.content.res.Configuration(resources.configuration)
    cfg.setLocale(locale)
    return createConfigurationContext(cfg)
}

internal fun readLangBlocking(context: Context): String = try {
    runBlocking { context.dataStore.data.first()[KeyLang] ?: "system" }
} catch (e: Exception) {
    "system"
}

internal fun buildStore(context: Context, s: Settings): InboxStore? = when {
    s.tree.isNotEmpty() -> SafInboxStore(context, Uri.parse(s.tree))
    s.folderPath.isNotEmpty() -> try { FileInboxStore(java.io.File(s.folderPath)) } catch (e: Exception) { null }
    else -> null
}

internal fun readSettings(context: Context): Settings = runBlocking {
    try {
        settingsFrom(context.dataStore.data.first(), context)
    } catch (e: Exception) {
        Settings()
    }
}

internal fun saveSettings(context: Context, s: Settings) {
    runBlocking {
        context.dataStore.edit {
            it[KeyAuto] = s.auto; it[KeyHost] = s.host; it[KeyPort] = s.port
            it[KeyPassword] = s.password; it[KeyName] = s.name; it[KeyTree] = s.tree
            it[KeyFormats] = s.formats; it[KeyPacket] = s.packet; it[KeyAnim] = s.anim
            it[KeyReadCol] = s.readCol; it[KeyDateCol] = s.dateCol; it[KeyAutoStart] = s.autoStart
            it[KeyHarvest] = s.harvest; it[KeyGrid] = s.grid; it[KeyLang] = s.lang; it[KeyView] = s.view; it[KeyPath] = s.folderPath; it[KeyTheme] = s.themeMode; it[KeyTextScale] = s.textScale
        }
    }
}

internal fun serviceIntent(context: Context, s: Settings) = Intent(context, WirelessService::class.java).apply {
    putExtra(WirelessService.EXTRA_AUTO, s.auto)
    putExtra(WirelessService.EXTRA_HOST, s.host)
    putExtra(WirelessService.EXTRA_PORT, s.port)
    putExtra(WirelessService.EXTRA_PASSWORD, s.password)
    putExtra(WirelessService.EXTRA_NAME, s.name)
    putExtra(WirelessService.EXTRA_TREE, s.tree)
    putExtra(WirelessService.EXTRA_PATH, s.folderPath)
    putExtra(WirelessService.EXTRA_FORMATS, s.formats)
    putExtra(WirelessService.EXTRA_PACKET, s.packet)
    putExtra(WirelessService.EXTRA_READ_COL, s.readCol)
    putExtra(WirelessService.EXTRA_DATE_COL, s.dateCol)
    putExtra(WirelessService.EXTRA_HARVEST, s.harvest)
}
