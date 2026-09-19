package dev.hug0.calwireless

data class DeviceConfig(
    val appName: String = "CalibreWireless",
    val appVersion: String = "1",
    val deviceKind: String,
    val deviceName: String,
    val maxPacketLen: Int = 65536,
    val coverHeight: Int = 240,
    val readSyncCol: String? = null,
    val readDateSyncCol: String? = null,
    val extensions: List<String> = DEFAULT_FORMATS,
) {
    val extensionSet: Set<String> get() = extensions.map { it.lowercase() }.toSet()

    companion object {
        val DEFAULT_FORMATS = listOf(
            "epub", "mobi", "azw3", "azw", "fb2", "pdf", "txt", "rtf", "html",
            "doc", "docx", "cbz", "cbr", "djvu", "djv", "lit", "lrf", "pdb",
        )

        fun parseFormats(raw: String?, fallback: List<String> = DEFAULT_FORMATS): List<String> {
            val list = raw.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            return list.ifEmpty { fallback }
        }
    }
}

data class DeviceBookInfo(
    val uuid: String, val lpath: String, val title: String,
    val authors: String, val series: String?, val size: Long,
    val isRead: Boolean? = null, val lastReadDate: String? = null,
) {
    companion object {
        fun from(o: kotlinx.serialization.json.JsonObject): DeviceBookInfo {
            fun str(k: String): String? = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val authors = (o["authors"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.joinToString(", ")
                ?: "Unknown"
            return DeviceBookInfo(
                uuid = str("uuid") ?: "none",
                lpath = str("lpath") ?: "",
                title = str("title") ?: str("lpath") ?: "",
                authors = authors,
                series = str("series"),
                size = str("size")?.toLongOrNull() ?: 0L,
                isRead = (o["_is_read_"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull(),
                lastReadDate = str("_last_read_date_"),
            )
        }
    }
}

sealed class WirelessEvent {
    object Connecting : WirelessEvent()
    data class Connected(val libraryName: String?, val deviceUuid: String? = null) : WirelessEvent()
    data class BookReceived(val lpath: String, val size: Long) : WirelessEvent()
    data class BookServed(val lpath: String) : WirelessEvent()
    data class BookDeleted(val lpath: String) : WirelessEvent()
    object PasswordRejected : WirelessEvent()
    data class Busy(val otherDevice: String) : WirelessEvent()
    object Ejected : WirelessEvent()
    data class Disconnected(val cause: String) : WirelessEvent()
    data class Log(val message: String) : WirelessEvent()
}
