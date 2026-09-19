package dev.hug0.calwireless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionBooklistTest {
    private fun storeWith(vararg lpaths: Pair<String, String>): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        val s = FileInboxStore(d)
        val db = DeviceBooks(s)
        lpaths.forEach { (lp, uuid) ->
            s.write(lp)!!.use { it.write(ByteArray(4) { 2 }) }
            db.upsert(Json.parseToJsonElement("""{"uuid":"$uuid","lpath":"$lp","title":"舊$uuid","last_modified":"LM-$uuid","size":4,"authors":["甲"],"tags":[],"thumbnail":["junk",240]}""").jsonObject, lp)
        }
        db.save()
        return s
    }

    @Test fun bookCountThenIdFrames() {
        FakeCalibre(storeWith("a/A.epub" to "u1", "b/B.epub" to "u2")).use { fc ->
            fc.start()
            val first = fc.call(Op.GET_BOOK_COUNT, """{"willUseCachedMetadata":true}""")
            assertEquals(Op.OK, first.opcode)
            val o = Json.parseToJsonElement(first.json).jsonObject
            assertEquals(2, o["count"]!!.jsonPrimitive.intOrNull2())
            assertEquals("true", o["willStream"]!!.jsonPrimitive.content)
            val id1 = fc.reader.next()!!
            assertEquals(Op.OK, id1.opcode)
            val j1 = Json.parseToJsonElement(id1.json).jsonObject
            assertEquals("a/A.epub", j1["lpath"]!!.jsonPrimitive.content)
            assertEquals("u1", j1["uuid"]!!.jsonPrimitive.content)
            assertEquals("LM-u1", j1["last_modified"]!!.jsonPrimitive.content)
            val j2 = Json.parseToJsonElement(fc.reader.next()!!.json).jsonObject
            assertEquals("u2", j2["uuid"]!!.jsonPrimitive.content)
            assertEquals(2, j2["priKey"]!!.jsonPrimitive.intOrNull2())
        }
    }

    @Test fun cacheLoopNoopCountIsOneWay() {
        FakeCalibre(storeWith("a/A.epub" to "u1")).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.send(Op.NOOP, """{"count":1}""")
            fc.expectNoFrame()
            val book = fc.call(Op.NOOP, """{"priKey":1}""")
            val o = Json.parseToJsonElement(book.json).jsonObject
            assertEquals("舊u1", o["title"]!!.jsonPrimitive.content)
            assertTrue(o.containsKey("authors"))
            assertFalse(o.containsKey("thumbnail")) // slim 掉了
        }
    }

    @Test fun booklistsAndMetadataAreOneWayAndPersist() {
        val s = storeWith("a/A.epub" to "u1")
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.call(Op.GET_BOOK_COUNT, "{}")
            fc.reader.next() // u1 id frame
            fc.send(Op.SEND_BOOKLISTS, """{"count":1,"collections":{},"willStreamMetadata":true}""")
            fc.expectNoFrame()
            fc.send(Op.SEND_BOOK_METADATA, """{"index":0,"count":1,"data":{"uuid":"u1","lpath":"a/A.epub","title":"新標題","last_modified":"LM2","size":4,"authors":["乙"]}}""")
            fc.expectNoFrame()
            fc.call(Op.NOOP, "{}") // sync barrier: 前面 one-way 都已處理完才會回這題
        }
        val meta = s.readText(DeviceBooks.META_FILE)!!
        assertTrue(meta.contains("新標題"))
        assertFalse(meta.contains("舊u1"))
    }

    @Test fun getBookMetadataFullFrame() {
        FakeCalibre(storeWith("a/A.epub" to "u1", "b/B.epub" to "u2")).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            val rep = fc.call(Op.GET_BOOK_METADATA, """{"index":2}""")
            assertEquals(Op.OK, rep.opcode)
            assertEquals("u2", Json.parseToJsonElement(rep.json).jsonObject["uuid"]!!.jsonPrimitive.content)
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.intOrNull2(): Int? = content.toIntOrNull()
}
