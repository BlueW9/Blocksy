/*
 * Blocksy - offline call blocker
 * Copyright (C) 2026 BlueW9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.bluew9.blocksy

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Entry(val key: String, val number: String, val name: String)

object Normalizer {
    /** Digits only; the last 9 digits are compared so +962791234567 matches 0791234567. */
    fun key(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 3) return null
        return if (digits.length > 9) digits.takeLast(9) else digits
    }
}

class BlockDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "blocksy.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE blocked (" +
                "key TEXT PRIMARY KEY, " +
                "number TEXT NOT NULL, " +
                "name TEXT NOT NULL DEFAULT '')"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun isBlocked(key: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM blocked WHERE key = ?", arrayOf(key))
            .use { it.moveToFirst() }

    /** Returns how many new numbers were actually added. */
    fun add(entries: List<Entry>): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (e in entries) {
                val cv = ContentValues().apply {
                    put("key", e.key)
                    put("number", e.number)
                    put("name", e.name)
                }
                if (db.insertWithOnConflict("blocked", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                    added++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun remove(key: String) {
        writableDatabase.delete("blocked", "key = ?", arrayOf(key))
    }

    fun clear() {
        writableDatabase.delete("blocked", null, null)
    }

    fun all(): List<Entry> {
        val out = ArrayList<Entry>()
        readableDatabase.rawQuery(
            "SELECT key, number, name FROM blocked ORDER BY name COLLATE NOCASE, number", null
        ).use { c ->
            while (c.moveToNext()) out.add(Entry(c.getString(0), c.getString(1), c.getString(2)))
        }
        return out
    }

    fun allKeys(): Set<String> {
        val out = HashSet<String>()
        readableDatabase.rawQuery("SELECT key FROM blocked", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    companion object {
        @Volatile
        private var instance: BlockDb? = null

        fun get(context: Context): BlockDb =
            instance ?: synchronized(this) {
                instance ?: BlockDb(context).also { instance = it }
            }
    }
}
