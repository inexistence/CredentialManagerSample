package com.janbean.sample.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.janbean.common.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class ClientDatabase(context: Context) : SQLiteOpenHelper(context, "server", null, DB_VERSION) {
    companion object {
        const val DB_VERSION = 1

        private var database: ClientDatabase? = null

        private val threadPool by lazy {
            Executors.newSingleThreadExecutor()
        }
        private val DATABASE_DISPATCHER by lazy {
            threadPool.asCoroutineDispatcher()
        }
        private val dataBaseScope by lazy {
            CoroutineScope(DATABASE_DISPATCHER)
        }

        private const val TABLE_USERS_CREDENTIAL = "users_credential"

        fun get(): ClientDatabase {
            if (ClientDatabase.database == null) {
                synchronized(this) {
                    if (ClientDatabase.database == null) {
                        ClientDatabase.database = ClientDatabase(AppUtils.getApplication()!!)
                    }
                }
            }
            return ClientDatabase.database!!
        }
    }

    private val db: SQLiteDatabase by lazy {
        writableDatabase
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_USERS_CREDENTIAL " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT, " +
                    "credentialId TEXT, "+
                    "clientDataJson TEXT, " +
                    "UNIQUE(credentialId));"
        )
    }

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {

    }

    suspend fun addCredential(userId: String, credentialId: String, clientDataJson: String): Long {
        return withContext(DATABASE_DISPATCHER) {
            db.insert(TABLE_USERS_CREDENTIAL, null, ContentValues().apply {
                put("username", userId)
                put("credentialId", credentialId)
                put("clientDataJson", clientDataJson)
            })
        }
    }

    suspend fun getCredentialIds(userId: String): List<String> {
        return withContext(DATABASE_DISPATCHER) {
            val cursor = db.query(
                TABLE_USERS_CREDENTIAL,
                arrayOf("credentialId"),
                "username=?",
                arrayOf(userId),
                null,
                null,
                null,
                null
            )
            cursor.use { c ->
                val result = ArrayList<String>()
                while (c.moveToNext()) {
                    val index = c.getColumnIndex("credentialId")
                    val data = if (index >= 0) {
                        c.getString(index)
                    } else null
                    data?.let { result.add(it) }
                }
                result
            }
        }
    }
}