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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pokemon` (" +
                "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`artworkUrl` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pokemon_index` (" +
                "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`artworkUrl` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
