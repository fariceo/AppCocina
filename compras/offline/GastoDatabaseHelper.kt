package com.elrancho.cocina.compras.offline

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GastoDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "GastosDB", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        val sql = """
            CREATE TABLE gastos (
                id INTEGER PRIMARY KEY,
                producto TEXT,
                peso TEXT,
                total REAL,
                fecha TEXT,
                hora TEXT,
                estado INTEGER,
                sincronizado INTEGER DEFAULT 0
            )
        """
        db.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS gastos")
        onCreate(db)
    }
}
