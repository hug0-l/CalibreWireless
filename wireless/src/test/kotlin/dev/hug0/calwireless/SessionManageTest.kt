package dev.hug0.calwireless

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManageTest {
    private class RecordingSink : CoverSink {
        val got = HashMap<String, String>()
        override fun put(lpath: String, base64Jpeg: String, width: Int, height: Int) { got[lpath] = base64Jpeg }
    }

    @Test fun thumbnailStashedOnSendBookAndMetadata() {
        val s = store()
        val sink = RecordingSink()
        val b64 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        FakeCalibre(s, coverSink = sink).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.receive("cv/cover.epub", "cv1", 3) // receive() 的 metadata 無 thumbnail → 不進 sink
            kotlin.test.assertTrue(sink.got.isEmpty())
            // 帶 thumbnail 的 SEND_BOOK
            fc.output.write(Frame.encode(Op.SEND_BOOK, """{"lpath":"cv/cover.epub","length":2,"metadata":{"uuid":"cv2","lpath":"cv/cover.epub","title":"C","thumbnail":[160,240,"$b64"]}}"""))
            fc.output.flush()
            fc.reader.next()
            fc.sendRaw(byteArrayOf(7, 8))
            fc.call(Op.NOOP, "{}")
            kotlin.test.assertEquals(b64, sink.got["cv/cover.epub"])
            // SEND_BOOK_METADATA 更新也收
            fc.send(Op.SEND_BOOK_METADATA, """{"index":0,"count":1,"data":{"uuid":"cv2","lpath":"cv/cover.epub","title":"C2","thumbnail":[160,240,"AAAA"]}}""")
            fc.call(Op.NOOP, "{}")
            kotlin.test.assertEquals("AAAA", sink.got["cv/cover.epub"])
            // 表內不得殘留 thumbnail（防殭屍膨脹）
            kotlin.test.assertFalse(s.readText(DeviceBooks.META_FILE)!!.contains("thumbnail"))
        }
    }

    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    private fun FakeCalibre.receive(lpath: String, uuid: String, bytes: Int) {
        output.write(Frame.encode(Op.SEND_BOOK, """{"lpath":"$lpath","length":$bytes,"metadata":{"uuid":"$uuid","lpath":"$lpath","title":"T$uuid","authors":["甲"],"size":$bytes}}"""))
        output.flush()
        reader.next() // OK before binary
        sendRaw(ByteArray(bytes) { 65 })
        call(Op.NOOP, "{}") // barrier
    }

    @Test fun deleteKnownBookTouchesFileTableAndKeepsProtocol() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.receive("a/1.epub", "m1", 3)
            fc.receive("b/2.epub", "m2", 4)
            val sess = fc.session!!
            assertTrue(sess.alive)
            assertEquals(2, sess.snapshot().size)

            assertTrue(sess.deleteBook("a/1.epub"))
            assertNull(s.size("a/1.epub"))
            assertNotNull(s.size("b/2.epub"))
            val snap = sess.snapshot()
            assertEquals(1, snap.size)
            assertEquals("m2", snap[0].uuid)
            assertEquals("甲", snap[0].authors)
            // 表落盤：新 FakeCalibre session 讀得到剩一本
            val meta = s.readText(DeviceBooks.META_FILE)!!
            assertTrue(meta.contains("b/2.epub") && !meta.contains("a/1.epub"))
            // 通道仍鎖步
            assertEquals(Op.OK, fc.call(Op.NOOP, "{}").opcode)
        }
    }

    @Test fun deleteUnknownAndUnsafeRejected() {
        val s = store()
        s.write("ghost.epub")!!.use { it.write(ByteArray(2)) }
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            val sess = fc.session!!
            assertFalse(sess.deleteBook("ghost.epub")) // 表內沒有 → 不動檔
            assertNotNull(s.size("ghost.epub"))
            assertFalse(sess.deleteBook("../x.epub"))
            assertFalse(sess.deleteBook("nope.epub"))
        }
    }

    @Test fun snapshotReflectsHarvestedBooks() {
        val s = store()
        s.write("手動丟.epub")!!.use { it.write(ByteArray(900)) }
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            fc.call(Op.GET_BOOK_COUNT, "{}")
            fc.reader.next()
            val snap = fc.session!!.snapshot()
            assertEquals(1, snap.size)
            assertEquals("手動丟", snap[0].title)
            assertEquals(900L, snap[0].size)
        }
    }

    @Test fun sessionDiesWithSocket() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.call(Op.GET_INITIALIZATION_INFO, "{}")
            val sess = fc.session!!
            assertTrue(sess.alive)
            fc.close()
            Thread.sleep(300)
            assertFalse(sess.alive)
        }
    }
}
