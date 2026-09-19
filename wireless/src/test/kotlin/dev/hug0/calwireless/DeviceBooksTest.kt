package dev.hug0.calwireless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceBooksTest {
    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    private fun book(uuid: String, lpath: String, title: String = "T") =
        Json.parseToJsonElement(
            """{"uuid":"$uuid","lpath":"$lpath","title":"$title","last_modified":"2026-01-01","size":10,"authors":["A"],"tags":["t"]}"""
        ).jsonObject

    @Test fun upsertLoadSaveRoundTrip() {
        val s = store()
        s.write("a/A.epub")!!.use { it.write(ByteArray(10) { 1 }) }
        s.write("b/B.epub")!!.use { it.write(ByteArray(10) { 1 }) }
        val db = DeviceBooks(s).apply { load() }
        assertEquals(0, db.count())
        db.upsert(book("u1", "a/A.epub", "甲"), "a/A.epub")
        db.upsert(book("u2", "b/B.epub", "乙"), "b/B.epub")
        db.save()

        val db2 = DeviceBooks(s).apply { load() }
        assertEquals(2, db2.count())
        assertEquals("u1", db2.uuidOf("a/A.epub"))
        db2.upsert(book("u1", "a/A.epub", "甲改版"), "a/A.epub")
        assertEquals(2, db2.count())
        assertEquals("甲改版", Json.parseToJsonElement(db2.frame(1)).jsonObject["title"]?.jsonPrimitive?.content)
    }

    @Test fun prunesMissingFiles() {
        val s = store()
        s.write("a/A.epub")!!.use { it.write(ByteArray(1)) }
        val db = DeviceBooks(s)
        db.upsert(book("u1", "a/A.epub"), "a/A.epub")
        db.upsert(book("u9", "gone.epub"), "gone.epub")
        db.save()
        val db2 = DeviceBooks(s).apply { load() }
        assertEquals(1, db2.count())
        assertEquals("u1", db2.uuidOf("a/A.epub"))
    }

    @Test fun updateSlimsUnknownKeys() {
        val s = store()
        s.write("b/B.epub")!!.use { it.write(ByteArray(1)) }
        val db = DeviceBooks(s).apply { load() }
        db.update(Json.parseToJsonElement("""{"lpath":"b/B.epub","uuid":"u2","title":"乙","thumbnail":["x",240],"junk":1}""").jsonObject)
        val stored = db.frame(1)
        assertFalse(stored.contains("thumbnail"))
        assertFalse(stored.contains("junk"))
        assertTrue(stored.contains("乙"))
    }

    @Test fun removeAndIdFrame() {
        val s = store()
        s.write("a/A.epub")!!.use { it.write(ByteArray(1)) }
        s.write("b/B.epub")!!.use { it.write(ByteArray(1)) }
        val db = DeviceBooks(s).apply { load() }
        db.upsert(book("u1", "a/A.epub"), "a/A.epub")
        db.upsert(book("u2", "b/B.epub"), "b/B.epub")
        val f1 = Json.parseToJsonElement(db.idFrame(1)).jsonObject
        assertEquals("a/A.epub", f1["lpath"]?.jsonPrimitive?.content)
        assertEquals(1, f1["priKey"]?.jsonPrimitive?.content?.toInt())
        assertEquals("u1", db.remove("a/A.epub"))
        assertNull(db.remove("nope"))
        assertEquals("none", db.uuidOf("a/A.epub"))
    }

    @Test fun deviceUuidStableAcrossReloads() {
        val s = store()
        val db = DeviceBooks(s)
        val u = db.deviceUuid()
        assertEquals(36, u.length)
        assertEquals(u, DeviceBooks(s).deviceUuid())
        db.saveDriveInfo(Json.parseToJsonElement("""{"device_store_uuid":"$u","device_name":"CalibreWireless (MIX 2S)"}""").jsonObject)
        assertEquals(u, DeviceBooks(s).deviceUuid())
    }
}
