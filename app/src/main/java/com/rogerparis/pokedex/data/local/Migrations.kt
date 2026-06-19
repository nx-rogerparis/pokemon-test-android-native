package com.rogerparis.pokedex.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN heightDm INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE favorites ADD COLUMN weightHg INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE favorites ADD COLUMN abilities TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE favorites ADD COLUMN stats TEXT NOT NULL DEFAULT '[]'")
    }
}
