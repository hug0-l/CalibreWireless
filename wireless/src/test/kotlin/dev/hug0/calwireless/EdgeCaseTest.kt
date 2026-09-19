package dev.hug0.calwireless

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeCaseTest {
    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    @Test fun absurdLengthPrefixKillsStream() {
        val junk = "99999999999[".toByteArray() + ByteArray(64)
        assertNull(FrameReader(ByteArrayInputStream(junk)).next())
    }

    @Test fun truncatedBinaryEndsSessionNotDesync() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.output.write(Frame.encode(Op.SEND_BOOK, """{"lpath":"cut.epub","length":10,"metadata":{"uuid":"c1","lpath":"cut.epub"}}"""))
            fc.output.flush()
            assertEquals(Op.OK, fc.reader.next()!!.opcode) // 應答 OK 後 Session 進 raw 態
            fc.sendRaw("123".toByteArray()) // 只給 3/10
            fc.close() // EOF 在 binary 中間
            Thread.sleep(200)
            assertTrue(fc.events.any { it is WirelessEvent.Disconnected }, "events=$fc.events")
            assertTrue((s.size("cut.epub") ?: 0L) < 10) // 殘檔不完整，且沒卡死
        }
    }

    @Test fun zeroLengthBook() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            assertEquals(Op.OK, fc.call(Op.SEND_BOOK, """{"lpath":"e.epub","length":0,"metadata":{"uuid":"e1","lpath":"e.epub","title":"E"}}""").opcode)
            assertEquals(Op.OK, fc.call(Op.NOOP, "{}").opcode) // 0 長度不卡流
            assertNotNull(s.size("e.epub"))
            assertEquals(0L, s.size("e.epub"))
        }
    }

    @Test fun deleteFileNotInTableLeavesUntouched() {
        val s = store()
        s.write("ghost.epub")!!.use { it.write(ByteArray(3)) } // 檔在、表沒有
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.call(Op.DELETE_BOOK, """{"lpaths":["ghost.epub"]}""")
            val r = fc.reader.next()!!
            assertTrue(r.json.contains("none"))
            assertNotNull(s.size("ghost.epub")) // 未入表不動檔（鏡像 KOReader）
        }
    }

    @Test fun harvestAcceptsUppercaseExtension() {
        val s = store()
        s.write("UP.EPUB")!!.use { it.write(ByteArray(2)) }
        val db = DeviceBooks(s).apply { load() }
        assertEquals(1, db.harvest(setOf("epub")))
    }
}
