package dev.hug0.calwireless.saf

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import dev.hug0.calwireless.InboxStore
import java.io.InputStream
import java.io.OutputStream

/**
 * SAF 收件夾。每次操作重掃目錄（資料夾小；ponytail: 千本級再上增量緩存）。
 */
class SafInboxStore(
    private val context: Context,
    private val treeUri: Uri,
) : InboxStore {

    private val root = DocumentFile.fromTreeUri(context, treeUri)

    private fun findByRel(rel: String): DocumentFile? {
        var dir = root ?: return null
        val parts = rel.split('/')
        for (i in 0 until parts.size - 1) {
            dir = dir.findFile(parts[i]) ?: return null
        }
        return dir.findFile(parts.last())
    }

    override fun list(): List<String> {
        val out = ArrayList<String>()
        fun walk(dir: DocumentFile, prefix: String) {
            for (c in dir.listFiles()) {
                val name = c.name ?: continue
                val rel = if (prefix.isEmpty()) name else "$prefix/$name"
                if (c.isDirectory) walk(c, rel) else if (c.isFile) out.add(rel)
            }
        }
        root?.let { walk(it, "") }
        return out
    }

    override fun size(path: String): Long? = findByRel(path)?.takeIf { it.isFile }?.length()?.takeIf { it >= 0 }

    override fun delete(path: String): Boolean = findByRel(path)?.delete() ?: false

    fun uriFor(rel: String): Uri? = findByRel(rel)?.uri

    override fun read(path: String): InputStream? =
        try { findByRel(path)?.let { context.contentResolver.openInputStream(it.uri) } } catch (e: Exception) { null }

    private fun ensureParent(rel: String): DocumentFile? {
        var dir = root ?: return null
        val parts = rel.split('/')
        for (i in 0 until parts.size - 1) {
            dir = dir.findFile(parts[i]) ?: dir.createDirectory(parts[i]) ?: return null
        }
        return dir
    }

    override fun write(path: String): OutputStream? {
        val name = path.substringAfterLast('/')
        val parent = ensureParent(path) ?: return null
        var doc = parent.findFile(name)
        if (doc == null) {
            // octet-stream + 帶點全名：ExternalStorageProvider 原樣保留檔名（T14 實機核）
            doc = parent.createFile("application/octet-stream", name) ?: return null
        }
        return try { context.contentResolver.openOutputStream(doc.uri, "wt") } catch (e: Exception) { null }
    }

    override fun readText(path: String): String? =
        try { read(path)?.reader(Charsets.UTF_8)?.readText() } catch (e: Exception) { null }

    override fun writeText(path: String, text: String): Boolean =
        try {
            write(path)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        } catch (e: Exception) {
            false
        }

    private fun statFs(): StatFs? {
        val seg = treeUri.pathSegments.drop(1).firstOrNull() ?: return null
        val parts = seg.split(':')
        if (parts.firstOrNull() != "primary") return null
        val path = if (parts.size > 1) java.io.File(Environment.getExternalStorageDirectory(), parts[1]).path else Environment.getExternalStorageDirectory().path
        return try { StatFs(path) } catch (e: Exception) { null }
    }

    override fun usableBytes(): Long =
        statFs()?.availableBytes ?: memoryInfo().first

    override fun totalBytes(): Long =
        statFs()?.totalBytes ?: memoryInfo().second

    private fun memoryInfo(): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem to mi.totalMem
    }
}
