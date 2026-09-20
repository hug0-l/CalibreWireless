package dev.hug0.calwireless

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscoverParseTest {
    @Test fun fullReply() {
        val s = Discover.parseReply(
            "calibre wireless device client (on somehost);8080,8135", "10.1.2.3"
        )!!
        assertEquals("10.1.2.3", s.address) // 來源 IP 優先於主機名
        assertEquals("somehost", s.name)
        assertEquals(8135, s.tcpPort)
        assertEquals(8080, s.opdsPort)
    }

    @Test fun noContentServer() {
        val s = Discover.parseReply("calibre wireless device client (on mac);,8135", "10.0.0.2")!!
        assertEquals(8135, s.tcpPort)
        assertNull(s.opdsPort)
    }

    @Test fun garbageIgnored() {
        assertNull(Discover.parseReply("hello", "1.2.3.4"))
        assertNull(Discover.parseReply("calibre wireless device client (on x);1,notaport", "1.2.3.4"))
    }
}
