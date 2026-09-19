package dev.hug0.calwireless

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LpathTest {
    private val exts = setOf("epub", "pdf", "txt")

    @Test fun rejectsBadPaths() {
        listOf(
            "",
            "../x.epub",
            "a/../x.epub",
            "/abs.epub",
            "back\\slash.epub",
            "C:\\x.epub",
            "a/b\u0000c.epub",
            "noext",
            "a//b.epub",
            "trailing/",
        ).forEach { assertFalse(Lpath.safe(it, exts), "should reject: $it") }
    }

    @Test fun acceptsGoodPaths() {
        listOf("Book.epub", "Author/Book.epub", "a b/中文字.epub", "A/B/C.PDF").forEach {
            assertTrue(Lpath.safe(it, exts), "should accept: $it")
        }
    }

    @Test fun rejectsUnknownExtension() {
        assertFalse(Lpath.safe("evil.exe", exts))
    }

    @Test fun noExtensionSetMeansAnyFilename() {
        assertTrue(Lpath.safe("weird.name.bin", null))
        assertFalse(Lpath.safe("../x.bin", null))
    }
}
