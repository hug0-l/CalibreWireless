package dev.hug0.calwireless

data class DeviceConfig(
    val appName: String = "CalibreWireless",
    val appVersion: String = "1",
    val deviceKind: String,
    val deviceName: String,
    val maxPacketLen: Int = 4096,
    val coverHeight: Int = 240,
    val extensions: List<String> = listOf(
        "epub", "mobi", "azw3", "azw", "fb2", "pdf", "txt", "rtf", "html",
        "doc", "docx", "cbz", "cbr", "djvu", "djv", "lit", "lrf", "pdb",
    ),
) {
    val extensionSet: Set<String> get() = extensions.map { it.lowercase() }.toSet()
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
