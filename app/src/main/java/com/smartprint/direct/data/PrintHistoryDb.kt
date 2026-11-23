package com.smartprint.direct.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PrintHistoryItem(
    val id: Long,
    val documentTitle: String,
    val printerName: String,
    val timestamp: Long,
    val pageCount: Int,
    val status: String
)

class PrintHistoryDb(context: Context) : SQLiteOpenHelper(context, "smartprint_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE print_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                document_title TEXT NOT NULL,
                printer_name TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                page_count INTEGER NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS print_history")
        onCreate(db)
    }

    fun addHistory(title: String, printerName: String, pages: Int, status: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("document_title", title)
            put("printer_name", printerName)
            put("timestamp", System.currentTimeMillis())
            put("page_count", pages)
            put("status", status)
        }
        db.insert("print_history", null, cv)
    }

    fun getHistory(): List<PrintHistoryItem> {
        val list = mutableListOf<PrintHistoryItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, document_title, printer_name, timestamp, page_count, status FROM print_history ORDER BY id DESC LIMIT 50", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PrintHistoryItem(
                        id = it.getLong(0),
                        documentTitle = it.getString(1),
                        printerName = it.getString(2),
                        timestamp = it.getLong(3),
                        pageCount = it.getInt(4),
                        status = it.getString(5)
                    )
                )
            }
        }
        return list
    }
}
