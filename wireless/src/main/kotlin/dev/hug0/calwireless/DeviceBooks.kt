package dev.hug0.calwireless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class DeviceBooks(private val store: InboxStore) {
    val books = ArrayList<JsonObject>()

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    fun load() {
        books.clear()
        val raw = store.readText(META_FILE) ?: return
        try {
            books.addAll(Json.parseToJsonElement(raw).jsonArray.map { it.jsonObject })
        } catch (e: Exception) {
            books.clear()
        }
        prune()
    }

    fun save() {
        store.writeText(META_FILE, JsonArray(books.map { it as JsonElement }).toString())
    }

    fun count(): Int = books.size

    fun upsert(meta: JsonObject, lpath: String) {
        val pinned = JsonObject(meta.toMutableMap().apply { this["lpath"] = JsonPrimitive(lpath) })
        replaceOrAdd(pinned)
    }

    fun update(meta: JsonObject) = replaceOrAdd(meta)

    private fun replaceOrAdd(book: JsonObject) {
        val lpath = book.str("lpath")
        val slim = slim(book)
        if (lpath == null) { books.add(slim); return }
        val i = books.indexOfFirst { it.str("lpath") == lpath }
        if (i >= 0) books[i] = slim else books.add(slim)
    }

    fun remove(lpath: String): String? {
        val i = books.indexOfFirst { it.str("lpath") == lpath }
        if (i < 0) return null
        val uuid = books[i].str("uuid") ?: "none"
        books.removeAt(i)
        return uuid
    }

    fun uuidOf(lpath: String): String =
        books.firstOrNull { it.str("lpath") == lpath }?.str("uuid") ?: "none"

    /** 1-based; calibre cache protocol frame */
    fun idFrame(index1Based: Int): String {
        val b = books[index1Based - 1]
        val o = LinkedHashMap<String, JsonElement>().apply {
            put("priKey", JsonPrimitive(index1Based))
            put("uuid", b["uuid"] ?: JsonPrimitive("none"))
            put("lpath", b["lpath"] ?: JsonPrimitive(""))
            put("last_modified", b["last_modified"] ?: JsonPrimitive("None"))
            b["_is_read_"]?.let { put("_is_read_", it) }
            b["_last_read_date_"]?.let { put("_last_read_date_", it) }
            b["_sync_type_"]?.let { put("_sync_type_", it) }
        }
        return JsonObject(o).toString()
    }

    fun frame(index1Based: Int): String = books[index1Based - 1].toString()

    fun prune() {
        val existing = store.list().toHashSet()
        books.removeAll { it.str("lpath")?.let { p -> p !in existing } ?: true }
    }

    /** 使用者手動丟進收件夾的書：自動入表（最小 metadata，calibre 入庫後可再編輯）。回傳新增數 */
    fun harvest(extensions: Set<String>): Int {
        val known = books.mapNotNull { it.str("lpath") }.toHashSet()
        var added = 0
        for (rel in store.list()) {
            if (rel in known) continue
            val ext = rel.substringAfterLast('.', "").lowercase()
            if (ext == "calibre" || ext !in extensions) continue
            val name = rel.substringAfterLast('/').substringBeforeLast('.')
            books.add(
                Json.parseToJsonElement(
                    """{"uuid":"${java.util.UUID.randomUUID()}","lpath":${JsonPrimitive(rel)},"title":${JsonPrimitive(name)},"last_modified":"None","size":${store.size(rel) ?: 0},"authors":["Unknown"],"tags":[]}"""
                ).jsonObject
            )
            added++
        }
        if (added > 0) save()
        return added
    }

    fun deviceUuid(): String {
        val raw = store.readText(DRIVE_FILE)
        if (raw != null) {
            try {
                Json.parseToJsonElement(raw).jsonObject.str("device_store_uuid")?.let { return it }
            } catch (e: Exception) { /* regenerate below */ }
        }
        val fresh = UUID.randomUUID().toString()
        val existing = LinkedHashMap<String, JsonElement>().apply {
            try { raw?.let { Json.parseToJsonElement(it).jsonObject.forEach { k, v -> put(k, v) } } } catch (e: Exception) {}
            put("device_store_uuid", JsonPrimitive(fresh))
            put("device_name", JsonPrimitive("CalibreWireless"))
        }
        store.writeText(DRIVE_FILE, JsonObject(existing).toString())
        return fresh
    }

    fun saveDriveInfo(o: JsonObject) {
        store.writeText(DRIVE_FILE, JsonObject(o).toString())
    }

    private fun slim(book: JsonObject): JsonObject {
        val o = LinkedHashMap<String, JsonElement>()
        for (k in USED_KEYS) book[k]?.let { o[k] = it }
        for (k in SYNC_KEYS) book[k]?.let { o[k] = it }
        return JsonObject(o)
    }

    companion object {
        const val META_FILE = "metadata.calibre"
        const val DRIVE_FILE = "driveinfo.calibre"
        val USED_KEYS = listOf("uuid", "lpath", "last_modified", "size", "title", "authors", "author_sort", "tags", "series", "series_index")
        val SYNC_KEYS = listOf("_is_read_", "_last_read_date_", "_sync_type_", "_format_mtime_")
    }
}
