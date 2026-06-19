# Pokédex — Offline Browse with RemoteMediator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the browse list paginate from Room and work offline — `RemoteMediator` fetches network pages into a Room cache; Paging reads from Room.

**Architecture:** A new `pokemon` cache table + `PokemonDao.pagingSource()`. `PokemonRemoteMediator` fills it (offset = cached row count; `position` column preserves order). `pokemonPager()` becomes a Room-backed `Pager` with the mediator. Replaces the network-only `PokemonPagingSource`. DB v2 → v3 via a real `Migration(2,3)`. Spec stretch.

**Tech Stack:** Paging 3 `RemoteMediator` (experimental API), Room (`withTransaction`), Coroutines/Flow. Tests: Robolectric + in-memory Room + MockK.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Decisions (from grill-me)

1. Offset-from-count + `position` index; **no** remote-keys table (PokéAPI is a clean offset API). New `pokemon` table separate from favorites.
2. Robolectric integration test calling `mediator.load()` directly.
3. `initialize()` → `SKIP_INITIAL_REFRESH` when the cache is non-empty (offline launch shows cache; first-ever launch refreshes from network).

## Global Constraints

- Package root `com.rogerparis.pokedex`; layering `ui → domain → data`.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17.
- `RemoteMediator` + `Pager(remoteMediator=...)` need `@OptIn(ExperimentalPagingApi::class)`.
- Page size 20 everywhere (Pager config + mediator).
- No raw exceptions above the repository (mediator maps to `MediatorResult.Error`).
- **Claude never stages or commits.** Suggested commit per task; user commits.
- Comments: explain non-obvious *why* only.

## File Structure

```
app/src/main/java/com/rogerparis/pokedex/
├── data/local/PokemonEntity.kt          # @Entity "pokemon" (id, name, artworkUrl, position)
├── data/local/PokemonDao.kt             # pagingSource(), insertAll, clearAll, count
├── data/local/PokedexDatabase.kt        # + PokemonEntity, version = 3, pokemonDao()
├── data/local/Migrations.kt             # + MIGRATION_2_3
├── di/DatabaseModule.kt                 # provide PokemonDao; addMigrations(1_2, 2_3)
├── data/remote/PokemonRemoteMediator.kt # the mediator
├── data/mapper/PokemonMappers.kt        # + PokemonEntity.toListEntry()
└── data/repository/DefaultPokemonRepository.kt  # pokemonPager() -> Room + mediator (+ ctor deps)
DELETE: data/remote/PokemonPagingSource.kt + its test
app/src/test/java/com/rogerparis/pokedex/data/remote/PokemonRemoteMediatorTest.kt
(update) data/repository/DefaultPokemonRepositoryTest.kt  # ctor gains db + pokemonDao mocks
```

---

## Task 1: Cache table, DAO, DB v3 + migration

**Files:** Create `data/local/PokemonEntity.kt`, `data/local/PokemonDao.kt`; modify `data/local/PokedexDatabase.kt`, `data/local/Migrations.kt`, `di/DatabaseModule.kt`

- [ ] **Step 1: Entity**

Create `app/src/main/java/com/rogerparis/pokedex/data/local/PokemonEntity.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val position: Int,
)
```

- [ ] **Step 2: DAO**

Create `app/src/main/java/com/rogerparis/pokedex/data/local/PokemonDao.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PokemonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PokemonEntity>)

    @Query("DELETE FROM pokemon")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun count(): Int

    @Query("SELECT * FROM pokemon ORDER BY position")
    fun pagingSource(): PagingSource<Int, PokemonEntity>
}
```

- [ ] **Step 3: Database v3**

In `data/local/PokedexDatabase.kt`:
```kotlin
@Database(entities = [FavoriteEntity::class, PokemonEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pokemonDao(): PokemonDao
}
```

- [ ] **Step 4: Migration 2→3**

In `data/local/Migrations.kt`, add:
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pokemon` (" +
                "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`artworkUrl` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
```

- [ ] **Step 5: DI — provide the DAO + register the migration**

In `di/DatabaseModule.kt`:
- Add imports: `import com.rogerparis.pokedex.data.local.MIGRATION_2_3`, `import com.rogerparis.pokedex.data.local.PokemonDao`.
- Update the builder migrations:
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```
- Add a provider:
```kotlin
    @Provides
    fun providePokemonDao(database: PokedexDatabase): PokemonDao = database.pokemonDao()
```

- [ ] **Step 6: Build (KSP regenerates Room for v3)**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Existing repository/tests still compile — `pokemonPager` is rewired in Task 3.)

- [ ] **Step 7: Suggested commit**

```
feat(data): add pokemon cache table, DAO, DB v3 + Migration(2,3)
```

---

## Task 2: PokemonRemoteMediator (TDD)

**Files:** Create `data/remote/PokemonRemoteMediator.kt`; add `PokemonEntity.toListEntry()` to `data/mapper/PokemonMappers.kt`; test `data/remote/PokemonRemoteMediatorTest.kt`

**Interfaces:**
- Produces: `PokemonRemoteMediator(api, database, pokemonDao)` (a `RemoteMediator<Int, PokemonEntity>`); `PokemonEntity.toListEntry(): PokemonListEntry`.

- [ ] **Step 1: Add the entity→domain mapper**

Append to `data/mapper/PokemonMappers.kt`:
```kotlin
import com.rogerparis.pokedex.data.local.PokemonEntity
```
```kotlin
fun PokemonEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
```

- [ ] **Step 2: Write the failing mediator test**

Create `app/src/test/java/com/rogerparis/pokedex/data/remote/PokemonRemoteMediatorTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonEntity
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PokemonRemoteMediatorTest {
    private val api = mockk<PokeApi>()
    private lateinit var db: PokedexDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PokedexDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun page(range: IntRange, next: String?) = PokemonListResponse(
        count = 1000,
        next = next,
        previous = null,
        results = range.map { PokemonListItemDto(name = "p$it", url = "https://pokeapi.co/api/v2/pokemon/$it/") },
    )

    private fun emptyState() = PagingState<Int, PokemonEntity>(emptyList(), null, PagingConfig(20), 0)

    private fun mediator() = PokemonRemoteMediator(api, db, db.pokemonDao())

    @Test
    fun `refresh inserts first page with positions and not end of pagination`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns
            page(1..20, next = "https://pokeapi.co/api/v2/pokemon?offset=20&limit=20")

        val result = mediator().load(RemoteMediator.LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(20, db.pokemonDao().count())
    }

    @Test
    fun `append continues offset from cached count and detects end`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns
            page(1..20, next = "x")
        coEvery { api.getPokemonList(limit = 20, offset = 20) } returns
            page(21..30, next = null)

        val mediator = mediator()
        mediator.load(RemoteMediator.LoadType.REFRESH, emptyState())
        val result = mediator.load(RemoteMediator.LoadType.APPEND, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(true, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(30, db.pokemonDao().count())
    }

    @Test
    fun `io error becomes MediatorResult Error`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } throws IOException("offline")

        val result = mediator().load(RemoteMediator.LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
    }
}
```

- [ ] **Step 3: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.PokemonRemoteMediatorTest"`
Expected: FAIL — `PokemonRemoteMediator` unresolved.

- [ ] **Step 4: Implement the mediator**

Create `app/src/main/java/com/rogerparis/pokedex/data/remote/PokemonRemoteMediator.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonDao
import com.rogerparis.pokedex.data.local.PokemonEntity
import com.rogerparis.pokedex.data.mapper.toEntry
import retrofit2.HttpException
import java.io.IOException

private const val PAGE_SIZE = 20

@OptIn(ExperimentalPagingApi::class)
class PokemonRemoteMediator(
    private val api: PokeApi,
    private val database: PokedexDatabase,
    private val pokemonDao: PokemonDao,
) : RemoteMediator<Int, PokemonEntity>() {

    override suspend fun initialize(): InitializeAction =
        if (pokemonDao.count() > 0) InitializeAction.SKIP_INITIAL_REFRESH
        else InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PokemonEntity>,
    ): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> pokemonDao.count()
        }
        return try {
            val response = api.getPokemonList(limit = PAGE_SIZE, offset = offset)
            val entities = response.results.mapIndexed { index, dto ->
                val entry = dto.toEntry()
                PokemonEntity(
                    id = entry.id,
                    name = entry.name,
                    artworkUrl = entry.artworkUrl,
                    position = offset + index,
                )
            }
            database.withTransaction {
                if (loadType == LoadType.REFRESH) pokemonDao.clearAll()
                pokemonDao.insertAll(entities)
            }
            MediatorResult.Success(endOfPaginationReached = response.next == null)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }
}
```
Note: the `position` (= `offset + index`) is what lets the Room `PagingSource` reproduce the API's exact order via `ORDER BY position` — without it, `ORDER BY id` would mis-order forms with high ids. The whole DB write is in a `withTransaction` so a crash mid-write can't leave the cache half-updated.

- [ ] **Step 5: Run, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.PokemonRemoteMediatorTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Suggested commit**

```
feat(data): add PokemonRemoteMediator backing browse with Room
```

---

## Task 3: Rewire the repository + remove the old PagingSource

**Files:** Modify `data/repository/DefaultPokemonRepository.kt`; update `data/repository/DefaultPokemonRepositoryTest.kt`; **delete** `data/remote/PokemonPagingSource.kt` and `data/remote/PokemonPagingSourceTest.kt`

- [ ] **Step 1: Delete the obsolete network PagingSource + its test**

```bash
rm app/src/main/java/com/rogerparis/pokedex/data/remote/PokemonPagingSource.kt
rm app/src/test/java/com/rogerparis/pokedex/data/remote/PokemonPagingSourceTest.kt
```
(The Room-backed `Pager` + mediator replaces it.)

- [ ] **Step 2: Rewire `pokemonPager()`**

In `DefaultPokemonRepository.kt`:
- Replace the `PokemonPagingSource` import with:
```kotlin
import androidx.paging.ExperimentalPagingApi
import androidx.paging.map
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonDao
import com.rogerparis.pokedex.data.mapper.toListEntry
import com.rogerparis.pokedex.data.remote.PokemonRemoteMediator
```
- Add `PokedexDatabase` + `PokemonDao` to the constructor:
```kotlin
class DefaultPokemonRepository @Inject constructor(
    private val api: PokeApi,
    private val favoriteDao: FavoriteDao,
    private val database: PokedexDatabase,
    private val pokemonDao: PokemonDao,
) : PokemonRepository {
```
- Replace the `pokemonPager()` body:
```kotlin
    @OptIn(ExperimentalPagingApi::class)
    override fun pokemonPager(): Flow<PagingData<PokemonListEntry>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            remoteMediator = PokemonRemoteMediator(api, database, pokemonDao),
            pagingSourceFactory = { pokemonDao.pagingSource() },
        ).flow.map { pagingData -> pagingData.map { entity -> entity.toListEntry() } }
```
(`kotlinx.coroutines.flow.map` is already imported; `androidx.paging.map` is the new one for the inner `PagingData` transform.)

- [ ] **Step 3: Update the repository test constructor**

In `DefaultPokemonRepositoryTest.kt`:
- Add imports:
```kotlin
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonDao
```
- Update the fields (the new deps are unused by the detail/favorites tests, so bare mocks suffice):
```kotlin
    private val favoriteDao = mockk<FavoriteDao>()
    private val database = mockk<PokedexDatabase>()
    private val pokemonDao = mockk<PokemonDao>()
    private val repository = DefaultPokemonRepository(api, favoriteDao, database, pokemonDao)
```

- [ ] **Step 4: Build + full unit suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (mediator test green; the deleted PagingSource test is gone; everything else unchanged).

- [ ] **Step 5: Suggested commit**

```
feat(data): back browse Pager with Room + RemoteMediator (offline browse)
```

---

## Task 4: Device verification (offline browse)

- [ ] **Step 1: Install (real migration runs)**

Run: `./gradlew installDebug`
The on-device DB migrates **2 → 3** via `MIGRATION_2_3` — favorites are preserved, the new `pokemon` table is added. No uninstall needed (this is the migration working for real).

- [ ] **Step 2: Verify**

- Online: open Browse, scroll a few pages (they fill the Room cache).
- Enable Airplane mode, fully kill and reopen the app.
- Open **Browse** → the previously-loaded Pokémon **now appear offline** (served from Room; `initialize()` skipped the network refresh because the cache is non-empty).
- Scroll to the bottom offline → the append fails gracefully (retry footer), since new pages still need network.
- Favorites + offline favorite detail still work.

- [ ] **Step 3: Suggested commit**

```
chore: verify offline browse via RemoteMediator
```

---

## Self-Review

**Decision coverage:** offset-from-count + `position`, no remote-keys table (Task 2) ✓; new `pokemon` table (Task 1) ✓; `RemoteMediator` replaces `PokemonPagingSource` (Tasks 2–3) ✓; Robolectric `load()` integration test (Task 2) ✓; `SKIP_INITIAL_REFRESH` when cached (Task 2 `initialize()`) ✓; real `Migration(2,3)` (Task 1, used on device Task 4) ✓.

**Placeholder scan:** No TBD; full code + commands with expected output. The constructor change's breakage of the repo test is explicitly updated (Task 3 Step 3); the obsolete PagingSource + test are explicitly deleted (Task 3 Step 1). ✓

**Type consistency:** `PokemonEntity(id, name, artworkUrl, position)`, `PokemonDao.{insertAll, clearAll, count, pagingSource}`, `PokemonRemoteMediator(api, database, pokemonDao)`, `DefaultPokemonRepository(api, favoriteDao, database, pokemonDao)`, `PokemonEntity.toListEntry()`, `MIGRATION_2_3` — consistent across code and tests. ✓

## Notes / remaining
- The browse cache grows as you scroll and is cleared only on REFRESH; fine for a learning app. A production app might cap it or add a freshness/TTL policy.
- Remaining stretch: search, team builder, full Hilt-instrumented UI tests, `exportSchema` for future migrations.
```
