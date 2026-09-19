package dev.hug0.calwireless

import java.io.DataInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/** 測試替身：扮演 calibre 端（TCP server），Session 以裝置身分連入。 */
class FakeCalibre(
    private val store: InboxStore,
    private val config: DeviceConfig = DeviceConfig(deviceKind = "TestKind", deviceName = "TestDevice"),
    private val password: String? = null,
) : AutoCloseable {
    private val server = ServerSocket(0)
    private var serverSock: Socket? = null
    private var sessionThread: Thread? = null
    lateinit var reader: FrameReader
        private set
    lateinit var raw: DataInputStream
        private set
    lateinit var output: OutputStream
        private set
    val events: MutableList<WirelessEvent> = Collections.synchronizedList(mutableListOf())

    fun start() {
        val t = Thread {
            val s = Socket("localhost", server.localPort)
            s.tcpNoDelay = true
            Session(s, store, config, password) { e -> events.add(e) }.run()
        }
        t.isDaemon = true
        sessionThread = t
        t.start()
        serverSock = server.accept()
        serverSock!!.tcpNoDelay = true
        raw = DataInputStream(serverSock!!.getInputStream())
        reader = FrameReader(raw)
        output = serverSock!!.getOutputStream()
    }

    /** calibre→裝置 請求並等一條應答 */
    fun call(op: Int, payload: String = "{}"): Frame {
        output.write(Frame.encode(op, payload))
        output.flush()
        return reader.next() ?: error("no reply (session ended?) events=$events")
    }

    /** one-way：送出不等也不應答 */
    fun send(op: Int, payload: String = "{}") {
        output.write(Frame.encode(op, payload))
        output.flush()
    }

    fun sendRaw(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    /** 讀 calibre 端收到的 n 個原始位元組（裝置供檔） */
    fun readRawExactly(n: Int): ByteArray {
        val b = ByteArray(n)
        raw.readFully(b)
        return b
    }

    fun nextEvent(timeoutMs: Long = 2000): WirelessEvent {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            if (events.isNotEmpty()) return events.removeAt(0)
            Thread.sleep(5)
        }
        error("no event within ${timeoutMs}ms")
    }

    fun expectNoFrame(ms: Int = 150) {
        serverSock!!.soTimeout = ms
        try {
            val probe = ByteArray(1)
            val n = raw.read(probe, 0, 1)
            if (n != -1) error("unexpected bytes from device")
        } catch (e: java.net.SocketTimeoutException) {
            // 期望：超時無資料
        } finally {
            serverSock!!.soTimeout = 0
        }
    }

    override fun close() {
        try { serverSock?.close() } catch (e: Exception) {}
        server.close()
        sessionThread?.join(2000)
    }
}
