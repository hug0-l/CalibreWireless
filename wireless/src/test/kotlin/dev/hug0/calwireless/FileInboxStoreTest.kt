package dev.hug0.calwireless

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileInboxStoreTest {
    private fun tmp(): File {
        val d = createTempDir()
        d.deleteOnExit()
        return d
    }

    @Test fun nestedWriteReadListDelete() {
        val store: InboxStore = FileInboxStore(tmp())
        store.write("作者/書名.epub")!!.use { it.write("12345".toByteArray()) }
        assertEquals(5L, store.size("作者/書名.epub"))
        assertTrue(store.list().contains("作者/書名.epub"))
        assertEquals("12345", store.read("作者/書名.epub")!!.reader().readText())
        assertTrue(store.delete("作者/書名.epub"))
        assertNull(store.size("作者/書名.epub"))
        assertFalse(store.delete("nope.epub"))
    }

    @Test fun textFiles() {
        val s = FileInboxStore(tmp())
        assertTrue(s.writeText("metadata.calibre", "[]"))
        assertEquals("[]", s.readText("metadata.calibre"))
        assertNull(s.readText("missing.calibre"))
    }

    @Test fun traversalBlocked() {
        val dir = tmp()
        val s = FileInboxStore(dir)
        assertNull(s.write("../escape.epub"))
        assertNull(s.size("../../etc/passwd"))
        assertFalse(s.writeText("../x.txt", "hi"))
    }

    @Test fun capacitySane() {
        val s = FileInboxStore(tmp())
        assertTrue(s.usableBytes() in 1..s.totalBytes())
    }
}
