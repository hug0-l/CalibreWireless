package dev.hug0.calwireless

import android.content.Context
import android.util.Base64
import dev.hug0.calwireless.CoverSink
import java.io.File
import java.security.MessageDigest

class FileCoverSink(context: Context) : CoverSink {
    private val dir = File(context.cacheDir, "covers").apply { mkdirs() }

    fun fileFor(lpath: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(lpath.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.jpg")
    }

    fun putRaw(lpath: String, bytes: ByteArray) {
        try {
            val f = fileFor(lpath)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(f)
        } catch (e: Exception) {
        }
    }

    override fun put(lpath: String, base64Jpeg: String, width: Int, height: Int) {
        try {
            val bytes = Base64.decode(base64Jpeg, Base64.DEFAULT)
            val f = fileFor(lpath)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(f)
        } catch (e: Exception) {
        }
    }
}
