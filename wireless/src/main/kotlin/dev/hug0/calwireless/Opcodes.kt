package dev.hug0.calwireless

object Op {
    const val OK = 0
    const val SET_CALIBRE_DEVICE_INFO = 1
    const val SET_CALIBRE_DEVICE_NAME = 2
    const val GET_DEVICE_INFORMATION = 3
    const val TOTAL_SPACE = 4
    const val FREE_SPACE = 5
    const val GET_BOOK_COUNT = 6
    const val SEND_BOOKLISTS = 7
    const val SEND_BOOK = 8
    const val GET_INITIALIZATION_INFO = 9
    const val BOOK_DONE = 11
    const val NOOP = 12
    const val DELETE_BOOK = 13
    const val GET_BOOK_FILE_SEGMENT = 14
    const val GET_BOOK_METADATA = 15
    const val SEND_BOOK_METADATA = 16
    const val DISPLAY_MESSAGE = 17
    const val CALIBRE_BUSY = 18
    const val SET_LIBRARY_INFO = 19
    const val GET_COLLECTIONS = 21
    const val UPDATE_COLLECTIONS = 22
    const val ERROR = 20
}
