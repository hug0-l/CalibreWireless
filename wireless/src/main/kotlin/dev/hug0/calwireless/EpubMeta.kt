package dev.hug0.calwireless

import java.util.zip.ZipInputStream

data class BookMeta(
    val title: String?,
    val authors: List<String>,
    val series: String?,
    val seriesIndex: Double?,
)

/** EPUB：container.xml → OPF 的 dc:title / dc:creator / calibre:series。失敗回 null 由呼叫端退回檔名 */
object EpubMeta {
    private val CONTENT = Regex("""content="([^"]*)"""")

    fun read(store: InboxStore, rel: String): BookMeta? = try {
        val container = zipText(store, rel, "META-INF/container.xml", ignoreCase = true) ?: return null
        val rootPath = Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1) ?: return null
        val opf = zipText(store, rel, rootPath, ignoreCase = false) ?: return null
        val title = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(opf)
            ?.groupValues?.get(1)?.trim()?.unescapeXml()?.takeIf { it.isNotEmpty() }
        val authors = Regex("<dc:creator[^>]*>(.*?)</dc:creator>", RegexOption.DOT_MATCHES_ALL)
            .findAll(opf).map { it.groupValues[1].trim().unescapeXml() }.filter { it.isNotEmpty() }.toList()
        val series = metaContent(opf, "calibre:series")
        val seriesIndex = metaContent(opf, "calibre:series_index")?.toDoubleOrNull()
        if (title == null && authors.isEmpty() && series == null) null
        else BookMeta(title, authors, series, seriesIndex)
    } catch (e: Exception) {
        null
    }

    private fun metaContent(opf: String, name: String): String? {
        for (m in Regex("<meta\\b[^>]*>").findAll(opf)) {
            val tag = m.value
            if (!tag.contains("\"$name\"")) continue
            CONTENT.find(tag)?.groupValues?.get(1)?.unescapeXml()?.let { return it }
        }
        return null
    }

    private fun zipText(store: InboxStore, rel: String, entryName: String, ignoreCase: Boolean): String? =
        store.read(rel)?.use { ins ->
            ZipInputStream(ins).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: return@use null
                    val match = if (ignoreCase) e.name.equals(entryName, true) else e.name == entryName
                    if (match) return@use zin.readBytes().toString(Charsets.UTF_8)
                }
                null
            }
        }

    private fun String.unescapeXml(): String = this
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> Integer.parseInt(m.groupValues[1], 16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toInt().toChar().toString() }
        .replace("&amp;", "&")
}
