package dev.hug0.calwireless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionServeDeleteTest {
    private fun storeWith(vararg lpaths: Pair<String, String>): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        val s = FileInboxStore(d)
        val db = DeviceBooks(s)
        lpaths.forEach { (lp, uuid) ->
            s.write(lp)!!.use { it.write(ByteArray(10000) { (it % 251).toByte() }) }
            db.upsert(Json.parseToJsonElement("""{"uuid":"$uuid","lpath":"$lp","title":"T","last_modified":"L","size":10000}""").jsonObject, lp)
        }
        db.save()
        return s
    }

    private fun FakeCalibre.bareInit() = call(Op.GET_INITIALIZATION_INFO, "{}")

    @Test fun servesWholeFileIgnoringPosition() {
        FakeCalibre(storeWith("in/x.epub" to "u1")).use { fc ->
            fc.start(); fc.bareInit()
            val rep = fc.call(Op.GET_BOOK_FILE_SEGMENT, """{"lpath":"in/x.epub","position":0,"thisBook":0,"totalBooks":1}""")
            assertEquals(Op.OK, rep.opcode)
            val len = Json.parseToJsonElement(rep.json).jsonObject["fileLength"]!!.jsonPrimitive.content.toLong()
            assertEquals(10000L, len)
            val got = fc.readRawExactly(10000)
            assertContentEquals(ByteArray(10000) { (it % 251).toByte() }, got)
            assertEquals(Op.OK, fc.call(Op.NOOP, "{}").opcode) // 通道仍鎖步
            assertTrue(fc.events.any { it is WirelessEvent.BookServed })
        }
    }

    @Test fun missingAndBadLpathAnswerNoop() {
        FakeCalibre(storeWith("in/x.epub" to "u1")).use { fc ->
            fc.start(); fc.bareInit()
            assertEquals(Op.NOOP, fc.call(Op.GET_BOOK_FILE_SEGMENT, """{"lpath":"nope.epub","position":0}""").opcode)
            assertEquals(Op.NOOP, fc.call(Op.GET_BOOK_FILE_SEGMENT, """{"lpath":"../x.epub","position":0}""").opcode)
            assertEquals(Op.OK, fc.call(Op.NOOP, "{}").opcode)
        }
    }

    @Test fun deleteAnswersOneUuidFramePerPath() {
        val s = storeWith("a/A.epub" to "u1", "b/B.epub" to "u2")
        FakeCalibre(s).use { fc ->
            fc.start(); fc.bareInit()
            fc.call(Op.GET_BOOK_COUNT, "{}")
            fc.reader.next(); fc.reader.next()
            val first = fc.call(Op.DELETE_BOOK, """{"lpaths":["a/A.epub","nope.epub"]}""")
            assertEquals(Op.OK, first.opcode)
            val r1 = Json.parseToJsonElement(fc.reader.next()!!.json).jsonObject
            val r2 = Json.parseToJsonElement(fc.reader.next()!!.json).jsonObject
            assertEquals("u1", r1["uuid"]!!.jsonPrimitive.content)
            assertEquals("none", r2["uuid"]!!.jsonPrimitive.content)
            assertNull(s.size("a/A.epub"))
            assertNotNull(s.size("b/B.epub"))
            val meta = s.readText(DeviceBooks.META_FILE)!!
            assertTrue(!meta.contains("a/A.epub") && meta.contains("b/B.epub"))
        }
    }

    @Test fun deleteUnsafePathDoesNotTouchFiles() {
        val s = storeWith("a/A.epub" to "u1")
        FakeCalibre(s).use { fc ->
            fc.start(); fc.bareInit()
            fc.call(Op.DELETE_BOOK, """{"lpaths":["../a/A.epub","/abs"]}""")
            assertEquals("none", Json.parseToJsonElement(fc.reader.next()!!.json).jsonObject["uuid"]!!.jsonPrimitive.content)
            assertEquals("none", Json.parseToJsonElement(fc.reader.next()!!.json).jsonObject["uuid"]!!.jsonPrimitive.content)
            assertNotNull(s.size("a/A.epub"))
        }
    }
}
