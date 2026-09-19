package dev.hug0.calwireless

object Lpath {
    /** 鏡像 KOReader isSafeLpath 行為：relative、無穿越/反斜線/盤符/NUL、末段為檔名；可選副檔名校驗 */
    fun safe(path: String, extensions: Set<String>? = null): Boolean {
        if (path.isEmpty() || path.contains('\u0000')) return false
        if (path.startsWith("/")) return false
        if (path.contains('\\')) return false
        if (path.length >= 2 && path[0].isLetter() && path[1] == ':') return false
        val components = path.split('/')
        for (c in components) {
            if (c == ".." || c.isEmpty()) return false
        }
        val filename = components.last()
        if (extensions != null) {
            val ext = filename.substringAfterLast('.', "")
            if (ext.isEmpty() || ext.lowercase() !in extensions) return false
        }
        return true
    }
}
