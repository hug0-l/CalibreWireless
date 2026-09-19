package dev.hug0.calwireless

import java.io.InputStream

data class Frame(val opcode: Int, val json: String) {
    companion object {
        fun encode(opcode: Int, payloadJson: String): ByteArray {
            val json = "[$opcode,${payloadJson}]".toByteArray(Charsets.UTF_8)
            val prefix = json.size.toString().toByteArray(Charsets.UTF_8)
            return prefix + json
        }
    }
}

class FrameReader(private val input: InputStream) {
    /** null = stream ended (socket closed). */
    fun next(): Frame? {
        val prefix = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            val c = b.toChar()
            if (c == '[') break
            if (!c.isDigit()) continue
            prefix.append(c)
        }
        val total = prefix.toString().toIntOrNull() ?: return null
        val buf = ByteArray(total)
        buf[0] = '['.code.toByte()
        var pos = 1
        while (pos < total) {
            val n = input.read(buf, pos, total - pos)
            if (n <= 0) return null
            pos += n
        }
        val json = String(buf, Charsets.UTF_8)
        val comma = json.indexOf(',')
        val opcode = json.substring(1, comma).trim().toInt()
        return Frame(opcode, json.substring(comma + 1, json.length - 1))
    }
}
