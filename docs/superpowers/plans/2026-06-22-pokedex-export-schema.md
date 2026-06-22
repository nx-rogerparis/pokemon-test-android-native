# Pokédex — Enable Room Schema Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Room schema export so the current (v5) schema is captured as JSON, unlocking `MigrationTestHelper` for future migrations.

**Architecture:** Apply the Room Gradle plugin, point it at a `schemas/` directory, flip `exportSchema = true`, build to emit `5.json`, and commit it as the baseline. Add `room-testing` to `androidTest` so `MigrationTestHelper` is available going forward. No production behavior changes.

**Tech Stack:** Room Gradle plugin (`androidx.room`), KSP, Room.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Framing (read first)

Export is **forward-looking**: it captures v5 now. It **cannot** reproduce the v1–v4 schemas (those versions were never exported), so `MigrationTestHelper` only helps for **future** migrations (v5→v6+). The existing `MigrationTest` (manual v1-DB build, validates 1→5) stays unchanged and remains our coverage for the historical chain.

## Decisions (from grill-me)

1. Declare the schema location via the **Room Gradle plugin** (`room { schemaDirectory(...) }`) — recommended; auto-wires the dir as a test asset/build IO. (Not the KSP `room.schemaLocation` arg.)
2. Scope = enable export + commit `5.json` + add `room-testing` (available, no new test). No `MigrationTestHelper` test yet (nothing to migrate to). Keep the manual `MigrationTest`.

## Global Constraints

- Package root `com.rogerparis.pokedex`. AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17.
- Plugin versions declared once at the root `build.gradle.kts` (`apply false`); module applies the version-less alias.
- The destructive fallback stays as a backstop; real migrations (1→5) stay registered.
- **Claude never stages or commits.** Suggested commit per task; user commits (the `5.json` is a committed artifact).
- Comments: explain non-obvious *why* only.

## File Structure

```
gradle/libs.versions.toml          # + room (plugin) alias + room-testing lib
build.gradle.kts                   # + alias(libs.plugins.room) apply false
app/build.gradle.kts               # apply room plugin; room { schemaDirectory(...) }; room-testing androidTest dep
app/src/main/java/com/rogerparis/pokedex/data/local/PokedexDatabase.kt  # exportSchema = true
app/schemas/com.rogerparis.pokedex.PokedexDatabase/5.json              # generated, committed
```

---

## Task 1: Apply the Room Gradle plugin + schema directory

**Files:** Modify `gradle/libs.versions.toml`, `build.gradle.kts` (root), `app/build.gradle.kts`

- [ ] **Step 1: Catalog — plugin alias + testing lib** (reuse the existing `room` version)

In `gradle/libs.versions.toml`:
- Under `[libraries]`:
```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```
- Under `[plugins]`:
```toml
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Root `build.gradle.kts` — declare the plugin (apply false)**

In the root `plugins { }` block:
```kotlin
    alias(libs.plugins.room) apply false
```

- [ ] **Step 3: `app/build.gradle.kts` — apply plugin, set schema dir, add testing dep**

In the module `plugins { }` block, add:
```kotlin
    alias(libs.plugins.room)
```
After the `android { }` block (top-level in the file), add the Room config:
```kotlin
room {
    schemaDirectory("$projectDir/schemas")
}
```
In `dependencies { }`:
```kotlin
    androidTestImplementation(libs.androidx.room.testing)
```

- [ ] **Step 4: Sync**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. (The Room plugin resolves; no schema emitted yet because `exportSchema` is still false.)

- [ ] **Step 5: Suggested commit**

```
build: apply Room Gradle plugin + schema directory; add room-testing
```

---

## Task 2: Turn on export and capture the v5 baseline

**Files:** Modify `app/src/main/java/com/rogerparis/pokedex/data/local/PokedexDatabase.kt`; generate `app/schemas/.../5.json`

- [ ] **Step 1: Flip exportSchema**

In `PokedexDatabase.kt`, change the annotation:
```kotlin
@Database(
    entities = [FavoriteEntity::class, PokemonEntity::class, PokemonIndexEntity::class, TeamMemberEntity::class],
    version = 5,
    exportSchema = true,
)
```

- [ ] **Step 2: Build to emit the schema**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, and a new file at
`app/schemas/com.rogerparis.pokedex.PokedexDatabase/5.json`.

- [ ] **Step 3: Verify the schema file exists**

Run: `ls app/schemas/com.rogerparis.pokedex.PokedexDatabase/`
Expected: `5.json` listed. (It encodes every table — favorites, pokemon, pokemon_index, team_members — with columns, defaults, and indices.)

- [ ] **Step 4: Confirm the unit suite is unaffected**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. The existing manual `MigrationTest` (1→5) still passes; export changes generated metadata only, not runtime behavior.

- [ ] **Step 5: Suggested commit** (commit the generated baseline)

```
build: enable Room schema export; commit v5 schema baseline
```
The `app/schemas/**/5.json` file is committed intentionally — it is the recorded baseline future migrations validate against. (Do NOT gitignore it.)

---

## Self-Review

**Decision coverage:** Room Gradle plugin + `schemaDirectory` (Task 1) ✓; `exportSchema = true` + committed `5.json` + `room-testing` available, no premature test (Task 2) ✓; manual `MigrationTest` untouched ✓.

**Placeholder scan:** No TBD; full code + commands with expected output. The forward-only limitation and the committed-artifact intent are stated explicitly.

**Type consistency:** `@Database` entity list/version unchanged except `exportSchema`. Plugin alias `libs.plugins.room` matches the catalog id `androidx.room`. ✓

## Going-forward recipe (when you add v6)

1. Add the new column/table to the entity; bump `@Database(version = 6)`.
2. Write `MIGRATION_5_6` (ALTER/CREATE), register it in `DatabaseModule` and in `MigrationTest`'s chain.
3. Build → Room emits `6.json` next to `5.json`.
4. Add a `MigrationTestHelper` test (now that `5.json` exists):
```kotlin
@get:Rule
val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    PokedexDatabase::class.java,
)

@Test
fun migrate5To6() {
    helper.createDatabase(TEST_DB, 5).apply { /* insert v5 rows */ close() }
    helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)
}
```
This validates the migration against the recorded `5.json` automatically — the payoff of exporting now.

## Notes
- This was the final optional item; with it done, the project has full schema-export hygiene for all future migrations.
