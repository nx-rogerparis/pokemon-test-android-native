# Pokédex Feature — Offline Detail for Favorites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a favorited Pokémon's full detail offline by falling back to a stored snapshot when the network is unreachable.

**Architecture:** Builds on the favorites + Room plan. Expands the favorite snapshot to a full `Pokemon`, and makes `getPokemon(id)` network-first with a Room fallback on `IOException` when the Pokémon is favorited. Spec chunk 6 stretch.

**Tech Stack:** Room (KSP), kotlinx.serialization (Stat converter), Coroutines/Flow, Hilt. Tests: JUnit + MockK + Robolectric.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Global Constraints

- Package root `com.rogerparis.pokedex`; layering `ui → domain → data`, inward only.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17. Plugin versions at root (`apply false`).
- Snapshot is a **pure offline fallback**: network is always the source of truth; the snapshot is used only when `getPokemon` hits an `IOException` **and** the Pokémon is favorited. `HttpException` (404/500) handling is unchanged — no fallback.
- DB schema changes from v1 → v2 → use **destructive migration** (acceptable: no shipped data).
- No raw exceptions above the repository. Detail UI is unchanged (offline favorite now yields `Success(snapshot)`).
- **Claude never stages or commits.** Each task ends with a suggested commit; the user commits.
- Comments: explain non-obvious *why* only.

## Decisions (from grill-me)

1. Network-first, snapshot fallback on failure (not cache-first, no connectivity API).
2. Full snapshot; `Stat` gets `@Serializable`; new `stats`/`abilities`/`heightDm`/`weightHg` columns.
3. `fallbackToDestructiveMigration` (no real data to preserve).
4. Fallback only on `IOException` + favorited; `HttpException` unchanged.
5. TDD: repository fallback + mapper round-trip + DAO `getById`.

## File Structure

```
app/src/main/java/com/rogerparis/pokedex/
├── domain/model/Pokemon.kt                       # @Serializable on Stat
├── data/local/FavoriteEntity.kt                  # + heightDm, weightHg, abilities, stats
├── data/local/Converters.kt                      # + List<Stat> <-> String
├── data/local/FavoriteDao.kt                     # + suspend getById(id): FavoriteEntity?
├── data/local/PokedexDatabase.kt                 # version = 2
├── di/DatabaseModule.kt                          # fallbackToDestructiveMigration
├── data/mapper/FavoriteMappers.kt                # full toFavoriteEntity + new toPokemon
└── data/repository/DefaultPokemonRepository.kt   # IOException -> snapshot fallback
app/src/test/java/com/rogerparis/pokedex/
├── data/mapper/FavoriteMappersTest.kt            # UPDATE entity ctor + round-trip
├── data/repository/DefaultPokemonRepositoryTest.kt  # UPDATE getById stubs + fallback tests
└── data/local/FavoriteDaoTest.kt                 # + getById test
```

---

## Task 1: Expand the snapshot schema

**Files:** Modify `domain/model/Pokemon.kt`, `data/local/FavoriteEntity.kt`, `data/local/Converters.kt`, `data/local/FavoriteDao.kt`, `data/local/PokedexDatabase.kt`, `di/DatabaseModule.kt`

**Interfaces:**
- Produces: `FavoriteEntity(id, name, artworkUrl, types, heightDm, weightHg, abilities, stats: List<Stat>)`; `FavoriteDao.getById(id): FavoriteEntity?`; `@Serializable Stat`; DB v2.

- [ ] **Step 1: Annotate `Stat`**

In `domain/model/Pokemon.kt`, add the import and annotation:
```kotlin
import kotlinx.serialization.Serializable
```
```kotlin
@Serializable
data class Stat(
    val name: String,
    val baseValue: Int,
)
```

- [ ] **Step 2: Expand the entity**

Replace `data/local/FavoriteEntity.kt` body:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rogerparis.pokedex.domain.model.Stat

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val types: List<String>,
    val heightDm: Int,
    val weightHg: Int,
    val abilities: List<String>,
    val stats: List<Stat>,
)
```

- [ ] **Step 3: Add the `List<Stat>` converter**

In `data/local/Converters.kt`, add the import and two methods:
```kotlin
import com.rogerparis.pokedex.domain.model.Stat
```
```kotlin
    @TypeConverter
    fun fromStatList(value: List<Stat>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStatList(value: String): List<Stat> = Json.decodeFromString(value)
```

- [ ] **Step 4: Add `getById` to the DAO**

In `data/local/FavoriteDao.kt`, add:
```kotlin
    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: Int): FavoriteEntity?
```

- [ ] **Step 5: Bump the DB version**

In `data/local/PokedexDatabase.kt`:
```kotlin
@Database(entities = [FavoriteEntity::class], version = 2, exportSchema = false)
```

- [ ] **Step 6: Destructive migration in the DB builder**

In `di/DatabaseModule.kt`, change the builder:
```kotlin
        Room.databaseBuilder(context, PokedexDatabase::class.java, "pokedex.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
```
NOTE: Room 2.7 uses the `dropAllTables` overload. If the compiler flags it, use the no-arg `fallbackToDestructiveMigration()` instead.

- [ ] **Step 7: Build (KSP regenerates Room code for v2)**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. The existing `FavoriteMappers`/tests won't compile yet against the new entity — that's fine, they're fixed in Task 2; this step just checks the entity/DAO/converter/DB compile. If `assembleDebug` fails only in `main` (not test), proceed; if mappers in `main` fail, they're updated next.

(If main-source mappers fail here because `toFavoriteEntity` now misses fields, do Task 2 Step 3 first, then return — the two are tightly coupled.)

- [ ] **Step 8: Suggested commit**

```
feat(data): expand favorite snapshot to full Pokemon (schema v2)
```

---

## Task 2: Full mappers + round-trip test (TDD)

**Files:** Modify `data/mapper/FavoriteMappers.kt`; update `data/test/.../FavoriteMappersTest.kt`

**Interfaces:**
- Produces: `Pokemon.toFavoriteEntity()` (all fields), `FavoriteEntity.toPokemon()`. `toListEntry()` unchanged.

- [ ] **Step 1: Update the mapper test (it references the old entity shape)**

In `FavoriteMappersTest.kt`, update the snapshot assertion to the full entity and add a round-trip test:
```kotlin
    @Test
    fun `pokemon maps to favorite entity snapshot`() {
        val pokemon = Pokemon(
            id = 25, name = "pikachu", heightDm = 4, weightHg = 60,
            types = listOf("electric"), stats = listOf(Stat("hp", 35)),
            abilities = listOf("static"), artworkUrl = "url25",
        )
        val entity = pokemon.toFavoriteEntity()
        assertEquals(
            FavoriteEntity(
                id = 25, name = "pikachu", artworkUrl = "url25",
                types = listOf("electric"), heightDm = 4, weightHg = 60,
                abilities = listOf("static"), stats = listOf(Stat("hp", 35)),
            ),
            entity,
        )
    }

    @Test
    fun `pokemon survives entity round-trip`() {
        val pokemon = Pokemon(
            id = 25, name = "pikachu", heightDm = 4, weightHg = 60,
            types = listOf("electric"), stats = listOf(Stat("hp", 35)),
            abilities = listOf("static"), artworkUrl = "url25",
        )
        assertEquals(pokemon, pokemon.toFavoriteEntity().toPokemon())
    }
```
(Keep the existing `favorite entity maps to list entry` test, but update its `FavoriteEntity(...)` constructor to include the new fields, e.g. `heightDm = 4, weightHg = 60, abilities = emptyList(), stats = emptyList()`.)

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.mapper.FavoriteMappersTest"`
Expected: FAIL — `toPokemon` unresolved and/or `toFavoriteEntity` not yet capturing all fields.

- [ ] **Step 3: Update the mappers**

Replace `data/mapper/FavoriteMappers.kt` body:
```kotlin
package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry

fun Pokemon.toFavoriteEntity(): FavoriteEntity =
    FavoriteEntity(
        id = id,
        name = name,
        artworkUrl = artworkUrl,
        types = types,
        heightDm = heightDm,
        weightHg = weightHg,
        abilities = abilities,
        stats = stats,
    )

fun FavoriteEntity.toPokemon(): Pokemon =
    Pokemon(
        id = id,
        name = name,
        heightDm = heightDm,
        weightHg = weightHg,
        types = types,
        stats = stats,
        abilities = abilities,
        artworkUrl = artworkUrl,
    )

fun FavoriteEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.mapper.FavoriteMappersTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Suggested commit**

```
feat(data): full favorite snapshot mappers (toFavoriteEntity/toPokemon)
```

---

## Task 3: Repository offline fallback (TDD)

**Files:** Modify `data/repository/DefaultPokemonRepository.kt`; update `data/repository/DefaultPokemonRepositoryTest.kt`

**Interfaces:**
- `getPokemon(id)`: on `IOException`, return `Success(favoriteDao.getById(id).toPokemon())` if the snapshot exists, else `Error(Network)`.

- [ ] **Step 1: Update + extend the repository test**

In `DefaultPokemonRepositoryTest.kt`:
- Add imports:
```kotlin
import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Stat
import io.mockk.coEvery
```
- The existing `maps IOException to Network error` test must now stub `getById` returning null (the fallback queries it). Update that test body's first line to add:
```kotlin
        coEvery { api.getPokemonDetail(1) } throws IOException("no connection")
        coEvery { favoriteDao.getById(1) } returns null
```
- Add a fallback-hit test:
```kotlin
    @Test
    fun `falls back to favorite snapshot when offline and favorited`() = runTest {
        coEvery { api.getPokemonDetail(1) } throws IOException("offline")
        coEvery { favoriteDao.getById(1) } returns FavoriteEntity(
            id = 1, name = "bulbasaur", artworkUrl = "u", types = listOf("grass"),
            heightDm = 7, weightHg = 69, abilities = listOf("overgrow"), stats = listOf(Stat("hp", 45)),
        )

        val result = repository.getPokemon(1)

        assertEquals(ApiResult.Success::class, result::class)
        assertEquals("bulbasaur", (result as ApiResult.Success).data.name)
        assertEquals(listOf("grass"), result.data.types)
    }
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.repository.DefaultPokemonRepositoryTest"`
Expected: FAIL — the fallback test gets `Error(Network)` (no fallback yet), and/or the IOException test fails because the unstubbed `getById` now gets called.

- [ ] **Step 3: Implement the fallback**

In `DefaultPokemonRepository.kt`, add the import and update `getPokemon`:
```kotlin
import com.rogerparis.pokedex.data.mapper.toPokemon
```
```kotlin
    override suspend fun getPokemon(id: Int): ApiResult<Pokemon> =
        try {
            ApiResult.Success(api.getPokemonDetail(id).toDomain())
        } catch (e: HttpException) {
            if (e.code() == 404) ApiResult.Error(AppError.NotFound)
            else ApiResult.Error(AppError.Unknown(e.message()))
        } catch (e: IOException) {
            val snapshot = favoriteDao.getById(id)
            if (snapshot != null) ApiResult.Success(snapshot.toPokemon())
            else ApiResult.Error(AppError.Network)
        }
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.repository.DefaultPokemonRepositoryTest"`
Expected: PASS (4 tests: success, IOException→Network with no snapshot, 404→NotFound, fallback→Success).

- [ ] **Step 5: Suggested commit**

```
feat(data): offline detail fallback to favorite snapshot on IOException
```

---

## Task 4: DAO getById test (Robolectric)

**Files:** Modify `data/local/FavoriteDaoTest.kt`

- [ ] **Step 1: Add the test** (the `entity(id)` helper now needs the new fields — update it too)

In `FavoriteDaoTest.kt`, update the helper and add a test:
```kotlin
    private fun entity(id: Int) =
        FavoriteEntity(
            id = id, name = "p$id", artworkUrl = "u$id", types = listOf("grass"),
            heightDm = 7, weightHg = 69, abilities = listOf("overgrow"), stats = listOf(Stat(name = "hp", baseValue = 45)),
        )

    @Test
    fun `getById returns the entity or null`() = runTest {
        assertEquals(null, dao.getById(1))
        dao.add(entity(1))
        assertEquals(entity(1), dao.getById(1))
    }
```
Add import: `import com.rogerparis.pokedex.domain.model.Stat`.

- [ ] **Step 2: Run**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.local.FavoriteDaoTest"`
Expected: PASS (3 tests). Confirms the `List<Stat>` converter round-trips through real SQLite.

- [ ] **Step 3: Suggested commit**

```
test: cover FavoriteDao.getById and stats converter
```

---

## Task 5: Full build + device verification

- [ ] **Step 1: Full build + full unit suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 2: Device verification**

Run: `./gradlew installDebug`
- Online: favorite a Pokémon (heart fills). Because the DB was recreated (destructive migration), **re-favorite** any previously-saved ones.
- Enable Airplane mode.
- Open that favorited Pokémon → **full detail now renders** (artwork, types, stats, abilities, height/weight) from the snapshot — no error.
- Open a **non-favorited** Pokémon offline → still shows the error message (expected — only favorites are cached).
- Re-favoriting/un-favoriting offline still works (from the earlier fix).

- [ ] **Step 3: Suggested commit**

```
chore: verify offline detail for favorites
```

---

## Self-Review

**Decision coverage:** network-first fallback (Task 3) ✓; full snapshot + `@Serializable Stat` (Tasks 1–2) ✓; destructive migration (Task 1) ✓; `IOException`-only + favorited (Task 3) ✓; TDD repository + mapper round-trip + DAO `getById` (Tasks 2–4) ✓.

**Placeholder scan:** No TBD; all code complete; commands show expected output. The breaking changes to existing tests/mappers (entity gained fields) are explicitly updated (Task 2 Step 1, Task 3 Step 1, Task 4 Step 1). The tight coupling between Task 1's entity and Task 2's mapper is called out (Task 1 Step 7 note). ✓

**Type consistency:** `FavoriteEntity(id, name, artworkUrl, types, heightDm, weightHg, abilities, stats)` used identically in entity, mappers, and all three test files; `toPokemon`/`toFavoriteEntity`/`toListEntry`, `getById(id): FavoriteEntity?`, repository fallback all aligned. ✓

## Notes / remaining
- Chunk 7 polish (shared components, type-colored chips, Compose UI tests) and other stretch (RemoteMediator offline browse, search, team) still open.
- A real `Migration(1,2)` is a worthwhile separate learning exercise (we chose destructive here deliberately).
