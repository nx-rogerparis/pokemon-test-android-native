package com.rogerparis.pokedex.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val dbName = "migration-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    private fun createV1Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val v1 = SQLiteDatabase.openOrCreateDatabase(file, null)
        v1.execSQL(
            "CREATE TABLE `favorites` (" +
                "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`artworkUrl` TEXT NOT NULL, `types` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        v1.execSQL(
            "INSERT INTO favorites (id, name, artworkUrl, types) " +
                "VALUES (1, 'bulbasaur', 'u', '[\"grass\"]')",
        )
        v1.version = 1
        v1.close()
    }

    @Test
    fun `migrate 1 to 2 preserves rows and back-fills new columns`() = runTest {
        createV1Database()

        val db = Room.databaseBuilder(context, PokedexDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .build()

        val migrated = db.favoriteDao().getById(1)
        db.close()

        assertEquals(1, migrated?.id)
        assertEquals("bulbasaur", migrated?.name)
        assertEquals(listOf("grass"), migrated?.types)
        assertEquals(0, migrated?.heightDm)
        assertEquals(0, migrated?.weightHg)
        assertEquals(emptyList<String>(), migrated?.abilities)
        assertEquals(emptyList<Any>(), migrated?.stats)
    }
}
