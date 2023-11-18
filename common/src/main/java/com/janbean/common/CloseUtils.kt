package com.janbean.common

import java.io.Closeable
import java.io.IOException

object CloseUtils {
    fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (var2: IOException) {
        }
    }
}