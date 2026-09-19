package dev.hug0.calwireless

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

interface InboxStore {
    /** recursive, '/'-separated relative paths of files (not dirs) */
    fun list(): List<String>
    fun size(path: String): Long?
    fun delete(path: String): Boolean
    fun read(path: String): InputStream?
    /** mkdirs parents, truncate; null on failure */
    fun write(path: String): OutputStream?
    fun readText(path: String): String?
    fun writeText(path: String, text: String): Boolean
    fun usableBytes(): Long
    fun totalBytes(): Long
}

class FileInboxStore(private val root: File) : InboxStore {
    init { root.mkdirs() }

    private fun resolve(rel: String): File? {
        val f = File(root, rel)
        val rootCanon = root.canonicalFile
        val canon = try { f.canonicalFile } catch (e: Exception) { return null }
        if (!canon.path.startsWith(rootCanon.path + File.separator) && canon != rootCanon) return null
        return canon
    }

    override fun list(): List<String> {
        val base = root.canonicalFile.path
        return root.walkTopDown().filter { it.isFile && !it.isHidden }
            .map { it.canonicalFile.path.removePrefix(base).trimStart(File.separatorChar).replace(File.separatorChar, '/') }
            .toList()
    }

    override fun size(path: String): Long? = resolve(path)?.takeIf { it.isFile }?.length()

    override fun delete(path: String): Boolean = resolve(path)?.delete() ?: false

    override fun read(path: String): InputStream? =
        try { resolve(path)?.takeIf { it.isFile }?.inputStream() } catch (e: Exception) { null }

    override fun write(path: String): OutputStream? {
        val f = resolve(path) ?: return null
        return try {
            f.parentFile?.mkdirs()
            FileOutputStream(f)
        } catch (e: Exception) {
            null
        }
    }

    override fun readText(path: String): String? =
        try { resolve(path)?.takeIf { it.isFile }?.readText() } catch (e: Exception) { null }

    override fun writeText(path: String, text: String): Boolean {
        val f = resolve(path) ?: return false
        return try {
            f.parentFile?.mkdirs()
            f.writeText(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun usableBytes(): Long = root.usableSpace
    override fun totalBytes(): Long = root.totalSpace
}
