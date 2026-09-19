package dev.hug0.calwireless

import java.io.File
import kotlin.system.exitProcess

fun main(args: List<String>) {
    fun flag(name: String): String? {
        val i = args.indexOf(name)
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
    }

    val inbox = File(flag("--inbox") ?: "./inbox")
    val host = flag("--host")
    val port = flag("--port")?.toIntOrNull()
    val password = flag("--password")
    val name = flag("--name") ?: "CalibreWireless (JVM)"
    val config = DeviceConfig(deviceKind = "JVM-Test-Device", deviceName = name)
    val store = FileInboxStore(inbox)
    val address: () -> Pair<String, Int>? = when {
        host != null && port != null -> { { host to port } }
        else -> { { Discover.hello()?.let { it.address to it.tcpPort } } }
    }
    println("CalibreWireless CLI inbox=$inbox ${if (host != null) "server=$host:$port" else "auto-discover"}")
    WirelessDevice(store, config, password, address, emit = { e ->
        println("[%s] %s".format(java.time.LocalTime.now(), e))
    }).runLoop { false }
    exitProcess(0)
}
