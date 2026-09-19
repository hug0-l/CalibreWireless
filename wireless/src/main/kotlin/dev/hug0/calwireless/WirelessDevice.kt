package dev.hug0.calwireless

import java.net.InetAddress
import java.net.Socket

class WirelessDevice(
    private val store: InboxStore,
    private val config: DeviceConfig,
    private val password: String?,
    private val addressProvider: () -> Pair<String, Int>?,
    private val emit: (WirelessEvent) -> Unit = {},
    private val socketFactory: (String, Int) -> Socket = { h, p ->
        Socket(InetAddress.getByName(h), p).apply { tcpNoDelay = true }
    },
) {
    fun runLoop(isStopped: () -> Boolean) {
        var delayMs = 5_000L
        while (!isStopped()) {
            val addr = try {
                addressProvider()
            } catch (e: Exception) {
                null
            }
            if (addr == null) {
                emit(WirelessEvent.Log("找不到 calibre，重試中"))
                delayMs = sleep(delayMs, isStopped)
                continue
            }
            emit(WirelessEvent.Connecting)
            val startedAt = System.currentTimeMillis()
            try {
                socketFactory(addr.first, addr.second).use { sock ->
                    Session(sock, store, config, password, emit).run()
                }
                delayMs = 5_000L // 正常斷線（eject 等）：快速重連
            } catch (e: Exception) {
                emit(WirelessEvent.Disconnected("連線失敗: ${e.message}"))
                delayMs = if (System.currentTimeMillis() - startedAt > 60_000) 5_000L else delayMs
                delayMs = sleep(delayMs, isStopped)
                continue
            }
            delayMs = sleep(delayMs, isStopped)
        }
    }

    /** 睡 delayMs（可被 stop 中斷），回傳下一輪的退避值 */
    private fun sleep(delayMs: Long, stopped: () -> Boolean): Long {
        val until = System.currentTimeMillis() + delayMs
        while (!stopped() && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(50)
            } catch (e: Exception) {
            }
        }
        return minOf(delayMs * 2, 60_000L)
    }
}
