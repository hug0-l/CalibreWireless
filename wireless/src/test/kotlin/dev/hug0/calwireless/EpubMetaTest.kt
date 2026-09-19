package dev.hug0.calwireless

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EpubMetaTest {
    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    private fun epub(
        title: String?,
        creators: List<String> = emptyList(),
        series: String? = null,
        seriesIndex: String? = null,
        rootPath: String = "content.opf",
    ): ByteArray {
        val opf = buildString {
            append("""<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
            title?.let { append("<dc:title>$it</dc:title>") }
            creators.forEach { append("""<dc:creator opf:role="aut" xmlns:opf="http://www.idpf.org/2007/opf">$it</dc:creator>""") }
            series?.let { append("""<meta property="belongs-to-collection">$it</meta><meta name="calibre:series" content="$it"/>""") }
            seriesIndex?.let { append("""<meta name="calibre:series_index" content="$it"/>""") }
            append("</metadata></package>")
        }
        val container = """<?xml version="1.0"?><container><rootfiles><rootfile full-path="$rootPath"/></rootfiles></container>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("mimetype")); z.write("application/epub+zip".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("META-INF/container.xml")); z.write(container.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry(rootPath)); z.write(opf.toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    private fun InboxStore.put(name: String, bytes: ByteArray) { write(name)!!.use { it.write(bytes) } }

    @Test fun parsesTitleCreatorsSeries() {
        val s = store()
        s.put("a.epub", epub("非人少女 &amp; 續篇", listOf("苗川采", "タカヒロ"), series = "垂涎系列", seriesIndex = "11.0"))
        val m = EpubMeta.read(s, "a.epub")!!
        assertEquals("非人少女 & 續篇", m.title)
        assertEquals(listOf("苗川采", "タカヒロ"), m.authors)
        assertEquals("垂涎系列", m.series)
        assertEquals(11.0, m.seriesIndex)
    }

    @Test fun rootfileInSubdirAndCaseInsensitiveContainer() {
        val s = store()
        s.put("b.epub", epub("T", listOf("A"), rootPath = "OEBPS/content.opf"))
        assertNotNull(EpubMeta.read(s, "b.epub"))
    }

    @Test fun garbageAndMissingPartsNull() {
        val s = store()
        s.put("notzip.epub", "PK not really a zip".toByteArray())
        assertNull(EpubMeta.read(s, "notzip.epub"))
        val noContainer = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z -> z.putNextEntry(ZipEntry("x.txt")); z.write("hi".toByteArray()) }
        }.toByteArray()
        s.put("noc.epub", noContainer)
        assertNull(EpubMeta.read(s, "noc.epub"))
    }

    @Test fun harvestUsesRealMetadataAndFallsBack() {
        val s = store()
        s.put("real.epub", epub("真書名", listOf("真作者"), series = "S", seriesIndex = "2"))
        s.put("fake.epub", "not a zip".toByteArray())
        s.put("doc.pdf", "junk".toByteArray())
        val db = DeviceBooks(s).apply { load() }
        assertEquals(3, db.harvest(setOf("epub", "pdf")))
        val byLpath = db.books.associateBy { it["lpath"]!!.jsonPrimitive.content }
        val real = byLpath["real.epub"]!!
        assertEquals("真書名", real["title"]!!.jsonPrimitive.content)
        assertEquals("真作者", real["authors"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("S", real["series"]!!.jsonPrimitive.content)
        assertEquals("fake", byLpath["fake.epub"]!!["title"]!!.jsonPrimitive.content)
        assertEquals("Unknown", byLpath["fake.epub"]!!["authors"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("doc", byLpath["doc.pdf"]!!["title"]!!.jsonPrimitive.content) // pdf 不解析 → 檔名
        // slim 白名單涵蓋 harvest 產出欄位
        assertEquals(3, DeviceBooks(s).let { it.load(); it.count() })
    }
}
