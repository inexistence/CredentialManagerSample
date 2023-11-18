package com.janbean.server

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import com.janbean.common.AppUtils
import com.janbean.common.CloseUtils
import com.webauthn4j.authenticator.Authenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.Executors

class ServerDatabase(context: Context) : SQLiteOpenHelper(context, "server", null, DB_VERSION) {
    companion object {
        const val DB_VERSION = 1
        private var database: ServerDatabase? = null
        private val threadPool by lazy {
            Executors.newSingleThreadExecutor()
        }
        private val DATABASE_DISPATCHER by lazy {
            threadPool.asCoroutineDispatcher()
        }
        private val dataBaseScope by lazy {
            CoroutineScope(DATABASE_DISPATCHER)
        }

        private const val TABLE_AUTHENTICATORS = "authenticators"

        fun get(): ServerDatabase {
            if (database == null) {
                synchronized(this) {
                    if (database == null) {
                        database = ServerDatabase(AppUtils.getApplication()!!)
                    }
                }
            }
            return database!!
        }
    }

    private val db: SQLiteDatabase by lazy {
        writableDatabase
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_AUTHENTICATORS " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "credentialId TEXT, " +
                    "authenticator BLOB, " +
                    "insertAt INTEGER, " +
                    "UNIQUE(credentialId));"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {

    }

    suspend fun bindAuthenticator(credentialId: String, authenticator: Authenticator): Long {
        val time = SystemClock.elapsedRealtime()
        return withContext(DATABASE_DISPATCHER) {
            val arrayOutputStream = ByteArrayOutputStream()
            var objectOutputStream: ObjectOutputStream? = null
            try {
                objectOutputStream = ObjectOutputStream(arrayOutputStream)
                objectOutputStream.writeObject(authenticator)
                objectOutputStream.flush()

                val data = arrayOutputStream.toByteArray()

                CloseUtils.closeQuietly(objectOutputStream)
                objectOutputStream = null
                CloseUtils.closeQuietly(arrayOutputStream)

                db.insert(TABLE_AUTHENTICATORS, null, ContentValues().apply {
                    put("authenticator", data)
                    put("insertAt", time)
                    put("credentialId", credentialId)
                })
            } catch (th: Throwable) {
                CloseUtils.closeQuietly(objectOutputStream)
                CloseUtils.closeQuietly(arrayOutputStream)
                -1
            }
        }
    }

    suspend fun getAuthenticator(credentialId: String): Authenticator? {
        return withContext(DATABASE_DISPATCHER) {
            val cursor = db.query(
                TABLE_AUTHENTICATORS,
                arrayOf("authenticator"),
                "credentialId=?",
                arrayOf(
                    credentialId
                ), null, null, null, "1"
            )
            cursor.use {
                val result = ArrayList<Authenticator>()
                while (it.moveToNext()) {
                    val index = it.getColumnIndex("authenticator")
                    val data = if (index >= 0) {
                        cursor.getBlob(index)
                    } else null
                    val arrayInputStream = ByteArrayInputStream(data)
                    var inputStream: ObjectInputStream? = null
                    try {
                        inputStream = ObjectInputStream(arrayInputStream)
                        val authenticator = inputStream.readObject() as Authenticator
                        result.add(authenticator)
                        CloseUtils.closeQuietly(inputStream)
                        inputStream = null
                        CloseUtils.closeQuietly(arrayInputStream)
                    } catch (th: Throwable) {
                        CloseUtils.closeQuietly(inputStream)
                        CloseUtils.closeQuietly(arrayInputStream)
                    }
                }
                result.firstOrNull()
            }
        }
    }
}