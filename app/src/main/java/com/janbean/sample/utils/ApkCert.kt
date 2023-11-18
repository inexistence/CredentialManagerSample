package com.janbean.sample.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Base64
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


object ApkCert {
    const val TAG = "ApkCert"

    fun getHashKey(context: Context): String? {
        try {
            val info: PackageInfo =
                context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md = MessageDigest.getInstance("SHA256")
                md.update(signature.toByteArray())
                val key = Base64.encodeToString(
                    md.digest(),
                    Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
                )
                Log.e(TAG, "MY KEY HASH: $key")
                return key
            }
        } catch (e: NameNotFoundException) {
        } catch (e: NoSuchAlgorithmException) {
        }
        return null
    }

//    fun getSHA1Fingerprint(uri: String?): String? {
//        uri ?: return null
//        return try {
//            if (!FileUtil.isFileExists(uri)) {
//                return null
//            }
//
//            val localFile = File(uri)
//            val md: MessageDigest = MessageDigest.getInstance("SHA-1")
//
//            val byteSize = 8192
//            val inputStream = FileInputStream(localFile)
//            val buffer = ByteArray(byteSize)
//            var count: Int
//            try {
//                while (inputStream.read(buffer, 0, byteSize).also { count = it } != -1) {
//                    md.update(buffer, 0, count)
//                }
//                MD5Utils.bytesToHexString(md.digest())
//            } finally {
//                closeQuietly(inputStream)
//            }
//        } catch (e: Throwable) {
//            null
//        }
//    }

}