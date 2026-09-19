package dev.hug0.calwireless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.Socket
import java.security.MessageDigest

private val EMPTY_OBJ = JsonObject(emptyMap())
private fun sha1Hex(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

class Session(
    private val socket: Socket,
    private val store: InboxStore,
    private val config: DeviceConfig,
    private val password: String?,
    private val emit: (WirelessEvent) -> Unit = {},
    private val coverSink: CoverSink? = null,
) {
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private val reader = FrameReader(input)
    private val books = DeviceBooks(store)
    val booksLock = Any()
    @Volatile var alive = false
        private set
    @Volatile private var ejecting = false
    private var endEvent: WirelessEvent = WirelessEvent.Disconnected("socket closed")

    fun run() {
        synchronized(booksLock) { books.load() }
        alive = true
        while (!ejecting) {
            val f = try { reader.next() } catch (e: Exception) { null } ?: break
            try {
                handle(f)
            } catch (e: Exception) {
                endEvent = WirelessEvent.Disconnected("opcode ${f.opcode}: $e")
                break
            }
        }
        alive = false
        emit(endEvent)
    }

    /** UI 管理入口：只刪表內認識的書，不動 calibre 通道（視圖靠重連刷新） */
    fun deleteBook(lpath: String): Boolean = synchronized(booksLock) {
        if (books.uuidOf(lpath) == "none") return false
        val ok = store.delete(lpath)
        books.remove(lpath)
        books.save()
        ok
    }

    fun snapshot(): List<DeviceBookInfo> = synchronized(booksLock) {
        books.infos()
    }

    fun markRead(lpath: String, read: Boolean?): Boolean = synchronized(booksLock) {
        if (books.uuidOf(lpath) == "none") return false
        books.setRead(lpath, read)
        books.save()
        true
    }

    private fun json(s: String): JsonObject =
        if (s.isBlank()) EMPTY_OBJ
        else try { Json.parseToJsonElement(s).jsonObject } catch (e: Exception) { EMPTY_OBJ }

    private fun send(opcode: Int, payloadJson: String = "{}") {
        output.write(Frame.encode(opcode, payloadJson))
        output.flush()
    }

    private fun error(msg: String) =
        send(Op.ERROR, JsonObject(mapOf("message" to JsonPrimitive(msg))).toString())

    private fun handle(f: Frame) {
        when (f.opcode) {
            Op.GET_INITIALIZATION_INFO -> onInit(f.json)
            Op.GET_DEVICE_INFORMATION -> {
                val o = buildJsonObject {
                    putJsonObject("device_info") {
                        put("device_store_uuid", books.deviceUuid())
                        put("device_name", config.deviceName)
                    }
                    put("version", config.appVersion)
                    put("device_version", config.appVersion)
                }
                send(Op.OK, o.toString())
            }
            Op.FREE_SPACE -> send(Op.OK, """{"free_space_on_device":${store.usableBytes()}}""")
            Op.TOTAL_SPACE -> send(Op.OK, """{"total_space_on_device":${store.totalBytes()}}""")
            Op.GET_BOOK_COUNT -> onBookCount()
            Op.SEND_BOOK -> onSendBook(f.json)
            Op.DELETE_BOOK -> onDeleteBook(f.json)
            Op.GET_BOOK_FILE_SEGMENT -> onServeSegment(f.json)
            Op.GET_BOOK_METADATA -> send(Op.OK, books.frame(json(f.json)["index"]!!.jsonPrimitive.int))
            Op.SEND_BOOKLISTS -> { /* one-way: calibre 不等應答 */ }
            Op.SEND_BOOK_METADATA -> {
                json(f.json)["data"]?.jsonObject?.let { m ->
                m["lpath"]?.jsonPrimitive?.contentOrNull?.let { stashCover(m, it) }
                synchronized(booksLock) { books.update(m); books.save() }
            }
            }
            Op.SET_CALIBRE_DEVICE_INFO -> { books.saveDriveInfo(json(f.json)); send(Op.OK) }
            Op.SET_CALIBRE_DEVICE_NAME -> send(Op.OK)
            Op.SET_LIBRARY_INFO -> {
                emit(WirelessEvent.Connected(json(f.json)["libraryName"]?.jsonPrimitive?.contentOrNull, books.deviceUuid()))
                send(Op.OK)
            }
            Op.DISPLAY_MESSAGE -> {
                val kind = json(f.json)["messageKind"]?.jsonPrimitive?.intOrNull
                if (kind == 1) emit(WirelessEvent.PasswordRejected)
                send(Op.OK)
            }
            Op.GET_COLLECTIONS -> send(Op.OK, """{"collections":{}}""")
            Op.UPDATE_COLLECTIONS -> send(Op.OK)
            Op.NOOP -> onNoop(f.json)
            Op.CALIBRE_BUSY -> {
                emit(WirelessEvent.Busy(json(f.json)["otherDevice"]?.jsonPrimitive?.contentOrNull ?: ""))
                ejecting = true
            }
            else -> send(Op.OK)
        }
    }

    private fun onInit(req: String) {
        val o = json(req)
        val challenge = o["passwordChallenge"]?.jsonPrimitive?.contentOrNull ?: ""
        val hash = if (password.isNullOrEmpty()) "" else sha1Hex(password + challenge)
        val init = buildJsonObject {
            put("versionOK", true)
            put("appName", config.appName)
            put("deviceKind", config.deviceKind)
            put("deviceName", config.deviceName)
            put("ccVersionNumber", config.appVersion)
            put("canStreamBooks", true)
            put("canStreamMetadata", true)
            put("canReceiveBookBinary", true)
            put("canDeleteMultipleBooks", true)
            put("canUseCachedMetadata", true)
            put("cacheUsesLpaths", true)
            put("canSendOkToSendbook", true)
            put("canAcceptLibraryInfo", true)
            put("willAskForUpdateBooks", false)
            put("coverHeight", config.coverHeight)
            put("maxBookContentPacketLen", config.maxPacketLen)
            put("useUuidFileNames", false)
            config.readSyncCol?.takeIf { it.isNotBlank() }?.let { put("isReadSyncCol", it) }
            config.readDateSyncCol?.takeIf { it.isNotBlank() }?.let { put("isReadDateSyncCol", it) }
            put("passwordHash", hash)
            put("acceptedExtensions", JsonArray(config.extensions.map { JsonPrimitive(it) }))
            putJsonObject("extensionPathLengths") { config.extensions.forEach { put(it, it.length) } }
        }
        send(Op.OK, init.toString())
    }

    private fun onBookCount() {
        val n = synchronized(booksLock) { books.harvest(config.extensionSet); books.count() }
        send(Op.OK, """{"count":$n,"willStream":true,"willScan":true}""")
        for (i in 1..n) send(Op.OK, synchronized(booksLock) { books.idFrame(i) })
    }

    private fun onSendBook(req: String) {
        val o = json(req)
        val lpath = o["lpath"]?.jsonPrimitive?.contentOrNull
        val length = o["length"]?.jsonPrimitive?.longOrNull ?: -1L
        val meta = o["metadata"]?.jsonObject
        if (lpath == null || length < 0 || meta == null) return error("bad SEND_BOOK request")
        if (!Lpath.safe(lpath, config.extensionSet)) return error("invalid path: $lpath")
        val usable = store.usableBytes()
        if (usable < 0 || usable < length + 131072) return error("no space left on device")
        send(Op.OK)
        var remaining = length
        val buf = ByteArray(64 * 1024)
        val out = store.write(lpath)
        try {
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n <= 0) throw IOException("binary stream truncated")
                out?.write(buf, 0, n)
                remaining -= n
            }
        } finally {
            try { out?.close() } catch (e: Exception) {}
        }
        if (out == null) { emit(WirelessEvent.Log("write failed: $lpath")); return }
        stashCover(meta, lpath)
        synchronized(booksLock) { books.upsert(meta, lpath); books.save() }
        emit(WirelessEvent.BookReceived(lpath, length))
    }

    private fun onDeleteBook(req: String) {
        val paths = json(req)["lpaths"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        send(Op.OK)
        paths.forEach { p ->
            val uuid = if (Lpath.safe(p)) synchronized(booksLock) {
                val removed = books.remove(p)
                if (removed != null) store.delete(p)
                removed ?: "none"
            } else "none"
            if (uuid != "none") emit(WirelessEvent.BookDeleted(p))
            send(Op.OK, """{"uuid":"$uuid"}""")
        }
        books.save()
    }

    private fun onServeSegment(req: String) {
        val lpath = json(req)["lpath"]?.jsonPrimitive?.contentOrNull
        if (lpath == null || !Lpath.safe(lpath)) return send(Op.NOOP)
        val size = store.size(lpath) ?: return send(Op.NOOP)
        send(Op.OK, """{"fileLength":$size}""")
        store.read(lpath)?.use { ins ->
            val buf = ByteArray(config.maxPacketLen)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
            }
            output.flush()
        }
        emit(WirelessEvent.BookServed(lpath))
    }

    private fun stashCover(meta: JsonObject, lpath: String) {
        val sink = coverSink ?: return
        val t = meta["thumbnail"] as? kotlinx.serialization.json.JsonArray ?: return
        if (t.size < 3) return
        val b64 = t[2].jsonPrimitive.contentOrNull ?: return
        val w = t[0].jsonPrimitive.intOrNull ?: 0
        val h = t[1].jsonPrimitive.intOrNull ?: 0
        try { sink.put(lpath, b64, w, h) } catch (e: Exception) {}
    }

    private fun onNoop(req: String) {
        val o = json(req)
        when {
            o["ejecting"]?.jsonPrimitive?.booleanOrNull == true -> {
                send(Op.OK)
                endEvent = WirelessEvent.Ejected
                ejecting = true
            }
            o["priKey"] != null -> send(Op.OK, books.frame(o["priKey"]!!.jsonPrimitive.int))
            o["count"] != null -> { /* one-way metadata 前導 */ }
            else -> send(Op.OK)
        }
    }
}
