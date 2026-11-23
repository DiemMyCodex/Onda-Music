package com.onda.music.core.transfer

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

class AndroidSqliteOpener : SqliteOpener {
    override fun openReadOnly(dbFile: File): SqliteConnection {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        return AndroidSqliteConnection(db)
    }

    private class AndroidSqliteConnection(
        private val db: SQLiteDatabase
    ) : SqliteConnection {
        override fun tableColumns(tableName: String): Set<String> {
            val cols = linkedSetOf<String>()
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        cursor.getString(nameIndex)?.let { cols.add(it) }
                    }
                }
            }
            return cols
        }

        override fun query(sql: String): SqliteCursor {
            return AndroidSqliteCursor(db.rawQuery(sql, null))
        }

        override fun close() {
            db.close()
        }
    }

    private class AndroidSqliteCursor(
        private val cursor: Cursor
    ) : SqliteCursor {
        override fun moveToNext(): Boolean = cursor.moveToNext()

        override fun getString(column: String): String? {
            val idx = cursor.getColumnIndex(column)
            if (idx < 0 || cursor.isNull(idx)) return null
            return cursor.getString(idx)
        }

        override fun getLong(column: String): Long? {
            val idx = cursor.getColumnIndex(column)
            if (idx < 0 || cursor.isNull(idx)) return null
            return cursor.getLong(idx)
        }

        override fun getInt(column: String): Int? {
            val idx = cursor.getColumnIndex(column)
            if (idx < 0 || cursor.isNull(idx)) return null
            return cursor.getInt(idx)
        }

        override fun close() {
            cursor.close()
        }
    }
}
