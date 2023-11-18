package com.janbean.sample.utils

import android.util.Log
import java.security.MessageDigest


object MD5Utils {
    fun md5(bytes: ByteArray?): String {
        val sb = StringBuilder()
        try {
            val md5 = MessageDigest.getInstance("MD5")
            val digest = md5.digest(bytes)
            sb.append(bytesToHexString(digest))
        } catch (var4: Exception) {
            Log.e("MD5", "md5 $var4")
        }
        return sb.toString()
    }

    fun md5(str: String?): String? {
        return if (str == null) null else md5(str.toByteArray())
    }

    fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        val var3 = bytes.size
        for (var4 in 0 until var3) {
            val b = bytes[var4]
            val `val` = b.toInt() and 255
            if (`val` < 16) {
                sb.append("0")
            }
            sb.append(Integer.toHexString(`val`))
        }
        return sb.toString()
    }
}

