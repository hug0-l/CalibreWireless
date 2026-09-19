package dev.hug0.calwireless

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class CalibreServer(val address: String, val name: String, val tcpPort: Int, val opdsPort: Int?)

object Discover {
    val BROADCAST_PORTS = intArrayOf(54982, 48123, 39001, 44044, 59678)
    private val REPLY = Regex("""calibre wireless device client \(on ([^)]*)\);(\d*),(\d+)""")

    /** dp 來源 IP 為準（calibre 主機名可能不可解析） */
    fun parseReply(text: String, fromHost: String): CalibreServer? {
        val m = REPLY.find(text) ?: return null
        val tcp = m.groupValues[3].toIntOrNull() ?: return null
        return CalibreServer(fromHost, m.groupValues[1].ifEmpty { fromHost }, tcp, m.groupValues[2].toIntOrNull())
    }

    fun hello(timeoutMsPerPort: Int = 1000): CalibreServer? {
        DatagramSocket().use { sock ->
            sock.broadcast = true
            sock.soTimeout = timeoutMsPerPort
            val req = "hello".toByteArray()
            for (port in BROADCAST_PORTS) {
                try {
                    sock.send(DatagramPacket(req, req.size, InetAddress.getByName("255.255.255.255"), port))
                    val buf = ByteArray(512)
                    val dp = DatagramPacket(buf, buf.size)
                    sock.receive(dp)
                    parseReply(String(dp.data, 0, dp.length).trim(), dp.address.hostAddress)?.let { return it }
                } catch (e: Exception) {
                    // 此 port 無人回，換下一個
                }
            }
            return null
        }
    }
}
