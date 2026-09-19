package dev.hug0.calwireless

import java.util.zip.ZipInputStream

/** 從書檔本體提封面：EPUB 走 OPF（meta[name=cover] → cover-image 屬性 → 第一張圖），CBZ 走首圖。 */
object EpubCover {
    private val IMG_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

    fun extract(store: InboxStore, rel: String): ByteArray? = try {
        val ext = rel.substringAfterLast('.', "").lowercase()
        when (ext) {
            "epub" -> epub(store, rel)
            "cbz" -> cbz(store, rel)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun epub(store: InboxStore, rel: String): ByteArray? {
        val container = EpubMetaZip.text(store, rel, "META-INF/container.xml", true) ?: return null
        val rootPath = Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1) ?: return null
        val opf = EpubMetaZip.text(store, rel, rootPath, false) ?: return null
        val base = rootPath.substringBeforeLast('/', "")

        var target: String? = null
        val coverId = Regex("""<meta[^>]*name="cover"[^>]*content="([^"]+)"""").find(opf)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]*content="([^"]+)"[^>]*name="cover"""").find(opf)?.groupValues?.get(1)
        if (coverId != null) {
            target = Regex("""<item[^>]*id="$coverId"[^>]*""").find(opf)?.let { itemTag ->
                Regex("""href="([^"]+)"""").find(itemTag.value)?.groupValues?.get(1)
            } ?: Regex("""<item[^>]*href="([^"]+)"[^>]*id="$coverId"""").find(opf)?.groupValues?.get(1)
        }
        if (target == null) {
            target = Regex("""<item[^>]*properties="[^"]*cover-image[^"]*"[^>]*""").find(opf)?.let {
                Regex("""href="([^"]+)"""").find(it.value)?.groupValues?.get(1)
            }
        }
        val opfDir = base
        fun full(h: String) = if (opfDir.isEmpty()) h else "$opfDir/$h"
        if (target != null) return EpubMetaZip.bytes(store, rel, full(target))
        // fallback：manifest 第一張 image item
        val firstImg = Regex("""<item[^>]*media-type="image/[a-z+]+"[^>]*""").find(opf)
        val href = firstImg?.let { Regex("""href="([^"]+)"""").find(it.value)?.groupValues?.get(1) }
            ?: return cbzLike(store, rel)
        return EpubMetaZip.bytes(store, rel, full(href)) ?: cbzLike(store, rel)
    }

    private fun cbzLike(store: InboxStore, rel: String): ByteArray? {
        var first: ByteArray? = null
        store.read(rel)?.use { ins ->
            ZipInputStream(ins).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name.lowercase()
                    if (!e.isDirectory && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) {
                        if (n.contains("cover")) return@use z.readBytes().also { first = it }
                        if (first == null) first = z.readBytes()
                    }
                }
            }
        }
        return first
    }

    private fun cbz(store: InboxStore, rel: String): ByteArray? = cbzLike(store, rel)
}

/** zip 讀取共用小工具（EPUB 檔可能 80MB，只取需要的前段） */
internal object EpubMetaZip {
    fun text(store: InboxStore, rel: String, entry: String, ignoreCase: Boolean): String? =
        bytes(store, rel, entry, ignoreCase)?.toString(Charsets.UTF_8)

    fun bytes(store: InboxStore, rel: String, entry: String, ignoreCase: Boolean = false): ByteArray? =
        store.read(rel)?.use { ins ->
            ZipInputStream(ins).use { z ->
                while (true) {
                    val e = z.nextEntry ?: return@use null
                    val hit = if (ignoreCase) e.name.equals(entry, true) else e.name == entry
                    if (hit) return@use z.readBytes()
                }
                null
            }
        }
}
