# Pokédex — Real Room Migration(1,2) Learning Exercise

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the destructive fallback's role for the v1→v2 upgrade with a real, tested `Migration(1,2)` that preserves existing favorites and back-fills the new columns.

**Architecture:** Adds `@ColumnInfo` defaults to the new snapshot columns (so the entity schema matches a migration that back-fills them), a `Migration(1,2)` with `ALTER TABLE ADD COLUMN`, and a self-contained Robolectric migration test that builds a raw v1 database and verifies the upgrade.

**Tech Stack:** Room migrations (`Migration`, `SupportSQLiteDatabase`), Robolectric, raw `SQLiteDatabase` for the v1 fixture.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Framing (read first)

This is **educational/retroactive**: the DB is already at v2 (the destructive fallback recreated it) and we never exported the v1 schema. There are no real v1 installs to migrate. We're writing and testing the migration as the genuine skill — the test proves correctness against a hand-built v1 DB. The schema-default change below means the on-device DB must be reinstalled once (Task 4).

## Decisions (from grill-me)

1. Test by manually building a v1 SQLite DB, then opening Room at v2 with the migration (no `MigrationTestHelper`, which would need exported schemas we don't have).
2. New NOT NULL columns get `DEFAULT 0` (ints) and `DEFAULT '[]'` (JSON list columns — must be valid empty JSON, not `''`, or the converter throws on read).
3. The entity declares matching `@ColumnInfo(defaultValue=...)` so Room's post-migration schema validation passes (entity-expected schema must match the migrated schema, defaults included).

## Global Constraints

- Package root `com.rogerparis.pokedex`. AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17.
- The migration test builds its Room instance with **only** `.addMigrations(MIGRATION_1_2)` (no destructive fallback) so a broken migration fails the test. Production keeps destructive as a backstop.
- **Claude never stages or commits.** Suggested commit per task; user commits.

## File Structure

```
app/src/main/java/com/rogerparis/pokedex/data/local/
├── FavoriteEntity.kt        # @ColumnInfo(defaultValue=...) on heightDm/weightHg/abilities/stats
├── Migrations.kt            # MIGRATION_1_2 (ALTER TABLE ADD COLUMN ...)
└── (PokedexDatabase.kt stays version = 2)
app/src/main/java/com/rogerparis/pokedex/di/DatabaseModule.kt   # .addMigrations(MIGRATION_1_2)
app/src/test/java/com/rogerparis/pokedex/data/local/MigrationTest.kt
```

---

## Task 1: Declare column defaults on the entity

**Why:** When a migration adds a NOT NULL column with a `DEFAULT`, Room's schema validation expects the entity's column definition to declare the same default. Without `@ColumnInfo(defaultValue=...)`, the migrated schema (column has a default) won't match the entity-expected schema (no default) → `IllegalStateException` at open.

**Files:** Modify `data/local/FavoriteEntity.kt`

- [ ] **Step 1: Add `@ColumnInfo` defaults**

Replace `data/local/FavoriteEntity.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rogerparis.pokedex.domain.model.Stat

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val types: List<String>,
    @ColumnInfo(defaultValue = "0") val heightDm: Int,
    @ColumnInfo(defaultValue = "0") val weightHg: Int,
    @ColumnInfo(defaultValue = "[]") val abilities: List<String>,
    @ColumnInfo(defaultValue = "[]") val stats: List<Stat>,
)
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (still version 2; this only annotates the existing columns' expected defaults).

- [ ] **Step 3: Suggested commit**

```
refactor(data): declare column defaults on favorite snapshot fields
```

---

## Task 2: Write the failing migration test

**Files:** Create `app/src/test/java/com/rogerparis/pokedex/data/local/MigrationTest.kt`

**Interfaces:**
- Consumes: `MIGRATION_1_2` (Task 3), `PokedexDatabase`, `FavoriteDao`.

- [ ] **Step 1: Write the test**

It builds a raw **v1** DB (the original 4-column schema, `user_version = 1`), inserts a v1 row, then opens Room at v2 with the migration and asserts the row survived with back-filled defaults.

Create `app/src/test/java/com/rogerparis/pokedex/data/local/MigrationTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run, verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.local.MigrationTest"`
Expected: FAIL — `MIGRATION_1_2` unresolved.

---

## Task 3: Implement and wire the migration

**Files:** Create `data/local/Migrations.kt`; modify `di/DatabaseModule.kt`

- [ ] **Step 1: Write `MIGRATION_1_2`**

Create `app/src/main/java/com/rogerparis/pokedex/data/local/Migrations.kt`:
```kotlin
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
```
Each `ADD COLUMN` default must match the entity's `@ColumnInfo(defaultValue=...)` from Task 1.

- [ ] **Step 2: Run the migration test, verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.local.MigrationTest"`
Expected: PASS. If it fails with a schema-mismatch / "Migration didn't properly handle" error, the `ALTER` defaults and the entity `@ColumnInfo` defaults disagree — reconcile them (same type, same default literal).

- [ ] **Step 3: Wire the migration into the production builder**

In `di/DatabaseModule.kt`, add the migration (keep the destructive fallback as a backstop for any *other* unhandled path):
```kotlin
import com.rogerparis.pokedex.data.local.MIGRATION_1_2
```
```kotlin
        Room.databaseBuilder(context, PokedexDatabase::class.java, "pokedex.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Suggested commit**

```
feat(data): add real Migration(1,2) for the favorites table + test
```

---

## Task 4: Full suite + device sanity

- [ ] **Step 1: Full unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (migration + existing).

- [ ] **Step 2: Device sanity (clean install)**

Because Task 1 changed the v2 schema (columns now declare defaults), the emulator's existing v2 DB no longer matches. Reinstall clean so the DB is recreated under the corrected schema:
Run: `./gradlew uninstallDebug installDebug`
Open the app, favorite a Pokémon, confirm favorites + offline detail still work (no crash on launch). The migration itself is verified by the Task 3 test — this step just confirms the app runs under the corrected schema.

- [ ] **Step 3: Suggested commit**

```
chore: verify favorites under migrated schema
```

---

## Self-Review

**Decision coverage:** manual v1-DB test (Task 2) ✓; `0`/`'[]'` defaults (Task 3) ✓; entity `@ColumnInfo` matching defaults (Task 1) ✓; test uses migration-only builder, production keeps destructive backstop (Tasks 2–3) ✓.

**Placeholder scan:** No TBD; full code + commands with expected output. The schema-default/validation gotcha and the device-reinstall consequence are called out explicitly. ✓

**Type consistency:** `MIGRATION_1_2`, `FavoriteEntity` columns + their `@ColumnInfo` defaults, the raw v1 `CREATE TABLE` (matches Room v1's generated 4-column schema), and the `ALTER` defaults all align. ✓

## Notes
- Going forward, enable `exportSchema = true` + a schema directory so future migrations can use the canonical `MigrationTestHelper` against exported JSON schemas (not retrofittable to this v1→v2 step since v1 was never exported).
