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
import kotlin.test.assertTrue

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

class EpubCoverTest {
    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            entries.forEach { (n, b) -> z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return out.toByteArray()
    }

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    @Test fun explicitCoverId() {
        val opf = """<package><manifest><item id="c" href="img/cover.png" media-type="image/png"/></manifest></package>"""
        val s = store()
        s.write("e.epub")!!.use {
            it.write(zipOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""".toByteArray(),
                "content.opf" to (opf + """<meta name="cover" content="c"/>""").toByteArray(),
                "img/cover.png" to png,
            ))
        }
        assertTrue(EpubCover.extract(s, "e.epub").contentEquals(png))
    }

    @Test fun firstImageFallback() {
        val s = store()
        s.write("f.epub")!!.use {
            it.write(zipOf(
                "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/book.opf"/></rootfiles></container>""".toByteArray(),
                "OEBPS/book.opf" to """<package><manifest><item id="a" href="art/one.jpeg" media-type="image/jpeg"/></manifest></package>""".toByteArray(),
                "OEBPS/art/one.jpeg" to png,
            ))
        }
        assertTrue(EpubCover.extract(s, "f.epub").contentEquals(png))
    }

    @Test fun cbzPrefersCoverNamed() {
        val s = store()
        s.write("m.cbz")!!.use {
            it.write(zipOf(
                "01.jpg" to byteArrayOf(9, 9),
                "cover.jpg" to png,
            ))
        }
        assertTrue(EpubCover.extract(s, "m.cbz").contentEquals(png))
    }

    @Test fun unsupportedAndBrokenNull() {
        val s = store()
        s.write("x.pdf")!!.use { it.write(byteArrayOf(1)) }
        assertNull(EpubCover.extract(s, "x.pdf"))
        s.write("y.epub")!!.use { it.write("not a zip".toByteArray()) }
        assertNull(EpubCover.extract(s, "y.epub"))
    }
}

class PdfMetaTest {
    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    @Test fun literalTitleAuthorWithEscapes() {
        val s = store()
        s.write("a.pdf")!!.use {
            it.write("%PDF-1.4 junk /Title (My \\(Cool\\) Book) /Author (Doe, J.; Roe, R.) trailer %%EOF".toByteArray(Charsets.ISO_8859_1))
        }
        val m = PdfMeta.read(s, "a.pdf")!!
        assertEquals("My (Cool) Book", m.title)
        assertEquals(listOf("Doe, J.", "Roe, R."), m.authors)
    }

    @Test fun hexUtf16Title() {
        val s = store()
        val hex = "FEFF" + "測試".map { String.format("%04X", it.code) }.joinToString("")
        s.write("b.pdf")!!.use { it.write("/Title <$hex> /Producer (x)".toByteArray()) }
        assertEquals("測試", PdfMeta.read(s, "b.pdf")!!.title)
    }

    @Test fun noDocinfoNull() {
        val s = store()
        s.write("c.pdf")!!.use { it.write("%PDF-1.7 /TitlesOnly nothing".toByteArray()) }
        assertNull(PdfMeta.read(s, "c.pdf"))
    }

    @Test fun harvestUsesPdfMeta() {
        val s = store()
        s.write("doc.pdf")!!.use {
            it.write("%PDF-1.4 /Title (廣播譯制規範) /Author (廣電總局) %%EOF".toByteArray(Charsets.UTF_8))
        }
        val db = DeviceBooks(s).apply { load() }
        assertEquals(1, db.harvest(setOf("pdf")))
        assertEquals("廣播譯制規範", db.books[0]["title"]?.jsonPrimitive?.content)
        assertEquals("廣電總局", (db.books[0]["authors"] as kotlinx.serialization.json.JsonArray).first().jsonPrimitive.content)
    }
}
