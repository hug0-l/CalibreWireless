package dev.hug0.calwireless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionSendBookTest {
    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    private fun limitedStore(usable: Long): InboxStore = object : InboxStore {
        val base = this@SessionSendBookTest.store()
        override fun list() = base.list()
        override fun size(path: String) = base.size(path)
        override fun delete(path: String) = base.delete(path)
        override fun read(path: String) = base.read(path)
        override fun write(path: String) = base.write(path)
        override fun readText(path: String) = base.readText(path)
        override fun writeText(path: String, text: String) = base.writeText(path, text)
        override fun usableBytes() = usable
        override fun totalBytes() = 1L shl 40
    }

    private fun sendBook(lpath: String, bytes: ByteArray, uuid: String = "u1") =
        """{"lpath":"$lpath","length":${bytes.size},"thisBook":0,"totalBooks":1,"willStreamBooks":true,"willStreamBinary":true,"wantsSendOkToSendbook":true,"metadata":{"uuid":"$uuid","lpath":"$lpath","title":"T$uuid","last_modified":"2026-01-01","size":${bytes.size},"authors":["甲"]}}"""

    @Test fun receiveBookWritesFileAndTable() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            val payload = sendBook("作者/書名.epub", "12345".toByteArray())
            assertEquals(Op.OK, fc.call(Op.SEND_BOOK, payload).opcode)
            fc.sendRaw("12345".toByteArray())
            fc.call(Op.NOOP, "{}") // barrier
            assertEquals("12345", String(s.read("作者/書名.epub")!!.readBytes()))
            val meta = s.readText(DeviceBooks.META_FILE)!!
            assertTrue(meta.contains("作者/書名.epub"))
            assertTrue(fc.events.any { it is WirelessEvent.BookReceived && it.lpath == "作者/書名.epub" })
        }
    }

    @Test fun metadataLpathPinnedToRequest() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            val p = """{"lpath":"x.epub","length":1,"metadata":{"uuid":"u2","lpath":"WRONG.epub","title":"T"}}"""
            fc.call(Op.SEND_BOOK, p)
            fc.sendRaw("z".toByteArray())
            fc.call(Op.NOOP, "{}")
            assertNotNull(s.size("x.epub"))
            assertNull(s.size("WRONG.epub"))
        }
    }

    @Test fun badLpathRejectedWithError() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            val rep = fc.call(Op.SEND_BOOK, sendBook("../evil.epub", "abc".toByteArray()))
            assertEquals(Op.ERROR, rep.opcode)
            // calibre 收到 ERROR 後不會再發 raw —— 通道仍乾淨：
            assertEquals(Op.OK, fc.call(Op.NOOP, "{}").opcode)
            assertNull(s.size("evil.epub"))
        }
    }

    @Test fun noSpaceRejectedWithoutDesync() {
        FakeCalibre(limitedStore(1000)).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            val rep = fc.call(Op.SEND_BOOK, sendBook("big.epub", ByteArray(5000)))
            assertEquals(Op.ERROR, rep.opcode)
            assertEquals(Op.OK, fc.call(Op.NOOP, "{}").opcode)
        }
    }

    @Test fun twoBooksInARowStayInLockstep() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.call(Op.SEND_BOOK, sendBook("a/1.epub", "AAA".toByteArray(), "n1"))
            fc.sendRaw("AAA".toByteArray())
            assertEquals(Op.OK, fc.call(Op.FREE_SPACE).opcode) // 中途插一題
            fc.call(Op.SEND_BOOK, sendBook("b/2.epub", "BB".toByteArray(), "n2"))
            fc.sendRaw("BB".toByteArray())
            fc.call(Op.NOOP, "{}")
            assertEquals("AAA", String(s.read("a/1.epub")!!.readBytes()))
            assertEquals("BB", String(s.read("b/2.epub")!!.readBytes()))
            val o = Json.parseToJsonElement(s.readText(DeviceBooks.META_FILE)!!).jsonArray
            assertEquals(2, o.size)
        }
    }
}
