package dev.hug0.calwireless

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameTest {
    @Test fun encodeLengthMatchesBytes() {
        val f = Frame.encode(9, """{"a":1}""")
        val s = String(f, Charsets.UTF_8)
        val len = s.takeWhile { it.isDigit() }.toInt()
        val jsonPart = s.substring(len.toString().length)
        assertEquals(len, jsonPart.toByteArray(Charsets.UTF_8).size)
        assertTrue(jsonPart.startsWith("[9,"))
    }

    @Test fun decodeRoundTrip() {
        val bytes = Frame.encode(0, "{}")
        val fr = FrameReader(ByteArrayInputStream(bytes))
        assertEquals(Frame(0, "{}"), fr.next())
        assertNull(fr.next())
    }

    @Test fun decodeMultiFrameStream() {
        val a = Frame.encode(17, """{"messageKind":1}""")
        val b = Frame.encode(0, """{"x":[1,2]}""")
        val fr = FrameReader(ByteArrayInputStream(a + b))
        assertEquals(17, fr.next()!!.opcode)
        val second = fr.next()!!
        assertEquals(0, second.opcode)
        assertEquals("""{"x":[1,2]}""", second.json)
    }

    @Test fun decodeByteAtATime() {
        val bytes = Frame.encode(17, """{"messageKind": 1}""")
        val fr = FrameReader(object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xff else -1
            override fun read(b: ByteArray, o: Int, n: Int): Int {
                if (n == 0) return 0
                if (pos >= bytes.size) return -1
                b[o] = bytes[pos++]
                return 1
            }
        })
        val f = fr.next()!!
        assertEquals(17, f.opcode)
        assertTrue(f.json.contains("messageKind"))
    }

    @Test fun payloadMayContainBrackets() {
        val bytes = Frame.encode(16, """{"data":{"tags":["a]b","c"]}}""")
        val f = FrameReader(ByteArrayInputStream(bytes)).next()!!
        assertEquals("""{"data":{"tags":["a]b","c"]}}""", f.json)
    }
}
