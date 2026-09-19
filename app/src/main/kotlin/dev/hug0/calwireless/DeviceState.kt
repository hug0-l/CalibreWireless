package dev.hug0.calwireless

import android.content.Context
import androidx.annotation.StringRes
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow

enum class LogKind { INFO, OK, WARN, ERR }
enum class Status { IDLE, CONNECTING, CONNECTED, RETRY, PASSWORD, BUSY, EJECTED }

data class LogLine(val time: String, val kind: LogKind, @StringRes val res: Int, val arg: String?, val raw: String?)

object DeviceState {
    val status = MutableStateFlow(Status.IDLE)
    val running = MutableStateFlow(false)
    val library = MutableStateFlow<String?>(null)
    val deviceUuid = MutableStateFlow<String?>(null)
    val logs = MutableStateFlow<List<LogLine>>(emptyList())

    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(@StringRes res: Int, arg: String? = null, kind: LogKind = LogKind.INFO) {
        logs.value = (logs.value + LogLine(LocalTime.now().format(fmt), kind, res, arg, null)).takeLast(200)
    }

    fun logRaw(text: String, kind: LogKind = LogKind.INFO) {
        logs.value = (logs.value + LogLine(LocalTime.now().format(fmt), kind, 0, null, text)).takeLast(200)
    }
}

@StringRes
fun statusRes(s: Status): Int = when (s) {
    Status.IDLE -> R.string.st_idle
    Status.CONNECTING -> R.string.st_connecting
    Status.CONNECTED -> R.string.st_connected
    Status.RETRY -> R.string.st_retry
    Status.PASSWORD -> R.string.st_password
    Status.BUSY -> R.string.st_busy
    Status.EJECTED -> R.string.st_ejected
}

fun statusText(context: Context, s: Status): String = context.getString(statusRes(s))
