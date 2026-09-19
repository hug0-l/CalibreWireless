package dev.hug0.calwireless

/** 從 PDF 明文 docinfo 提 /Title /Author（best-effort：不解壓縮流、不碰加密檔）。 */
object PdfMeta {
    private const val HEAD = 64 * 1024
    private const val TAIL = 1024 * 1024

    fun read(store: InboxStore, rel: String): BookMeta? = try {
        val size = store.size(rel) ?: return null
        val data = store.read(rel)?.use { ins ->
            val head = ins.readNBytes(HEAD)
            if (size <= HEAD) head
            else {
                ins.skip((size - HEAD - TAIL).coerceAtLeast(0))
                head + ins.readNBytes(TAIL.toInt())
            }
        } ?: return null
        val text = String(data, Charsets.ISO_8859_1)
        val title = pdfValue(text, "Title")
        val author = pdfValue(text, "Author")
        if (title.isNullOrBlank() && author.isNullOrBlank()) null
        else BookMeta(
            title = title?.takeIf { it.isNotBlank() },
            authors = author?.split(';', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            series = null,
            seriesIndex = null,
        )
    } catch (e: Exception) {
        null
    }

    private fun pdfValue(text: String, key: String): String? {
        var i = text.indexOf("/$key")
        while (i >= 0) {
            val after = text.getOrNull(i + key.length + 1)
            if (after == null || after in " (<\r\n/[{") break
            i = text.indexOf("/$key", i + 1)
        }
        if (i < 0) return null
        var j = i + key.length + 1
        while (j < text.length && (text[j] == ' ' || text[j] == '\r' || text[j] == '\n')) j++
        return when (text.getOrNull(j)) {
            '(' -> {
                val sb = StringBuilder()
                var depth = 1
                var k = j + 1
                while (k < text.length && depth > 0) {
                    val c = text[k]
                    if (c == '\\' && k + 1 < text.length) {
                        k++
                        when (val e = text[k]) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            in '0'..'7' -> {
                                var v = 0
                                var d = 0
                                while (d < 3 && k < text.length && text[k] in '0'..'7') {
                                    v = v * 8 + (text[k] - '0')
                                    k++
                                    d++
                                }
                                k--
                                sb.append(v.toChar())
                            }
                            else -> sb.append(e)
                        }
                    } else if (c == '(') {
                        depth++
                        sb.append(c)
                    } else if (c == ')') {
                        depth--
                        if (depth > 0) sb.append(c)
                    } else sb.append(c)
                    k++
                }
                decodeDocString(sb.toString())
            }
            '<' -> {
                val end = text.indexOf('>', j)
                if (end < 0) null
                else hexValue(text.substring(j + 1, end))
            }
            else -> null
        }
    }

    // literal bytes 經 ISO-8859-1 進來的；嘗試還原 UTF-8，無效則保留
    private fun decodeDocString(s: String): String {
        val raw = s.toByteArray(Charsets.ISO_8859_1)
        val u = String(raw, Charsets.UTF_8)
        return if (u.contains('\uFFFD')) s else u
    }

    private fun hexValue(hex0: String): String? = try {
        val hex = hex0.filter { !it.isWhitespace() }
        if (hex.length % 2 != 0 || hex.isEmpty()) {
            null
        } else {
            val bytes = ByteArray(hex.length / 2) { k -> hex.substring(k * 2, k * 2 + 2).toInt(16).toByte() }
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            } else {
                val u = String(bytes, Charsets.UTF_8)
                if (u.contains('\uFFFD')) String(bytes, Charsets.ISO_8859_1) else u
            }
        }
    } catch (e: Exception) {
        null
    }
}
