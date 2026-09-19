package dev.hug0.calwireless

/** calibre 推書/同步 metadata 時內嵌的封面（已按 coverHeight 壓好的 jpeg，base64）。實作端自行解碼落盤。 */
interface CoverSink {
    fun put(lpath: String, base64Jpeg: String, width: Int, height: Int)
}
