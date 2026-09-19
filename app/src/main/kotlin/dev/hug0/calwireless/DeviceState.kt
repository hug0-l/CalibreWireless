package dev.hug0.calwireless

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow

enum class LogKind { INFO, OK, WARN, ERR }
enum class Led { OFF, PULSE, GREEN, AMBER, RED }

data class LogLine(val time: String, val kind: LogKind, val text: String)

object DeviceState {
    val status = MutableStateFlow("待機")
    val led = MutableStateFlow(Led.OFF)
    val running = MutableStateFlow(false)
    val library = MutableStateFlow<String?>(null)
    val deviceUuid = MutableStateFlow<String?>(null)
    val logs = MutableStateFlow<List<LogLine>>(emptyList())

    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(text: String, kind: LogKind = LogKind.INFO) {
        logs.value = (logs.value + LogLine(LocalTime.now().format(fmt), kind, text)).takeLast(200)
    }

    fun setStatus(text: String, state: Led) {
        status.value = text
        led.value = state
    }
}
