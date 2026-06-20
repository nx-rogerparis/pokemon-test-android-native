# Pokédex — Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add name search to the Browse screen — a debounced query filters a locally-cached full Pokémon index, results paged like browse.

**Architecture:** PokéAPI has no search endpoint, so we bulk-fetch the full name list once into a `pokemon_index` table and filter with a Room `LIKE` query. The list ViewModel swaps between the browse `RemoteMediator` pager and a local search pager via `flatMapLatest` on a debounced query `StateFlow`. DB v3 → v4. Spec stretch.

**Tech Stack:** Room (`LIKE` query + Room-only `Pager`), Paging 3, Coroutines `debounce`/`flatMapLatest`, Compose `OutlinedTextField`. Tests: Robolectric + in-memory Room + MockK.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Decisions (from grill-me)

1. Bulk full-index fetch (`/pokemon?limit=100000&offset=0`) + local `LIKE` filter (no server search; B/C are broken/incomplete).
2. Search field on the Browse screen; `flatMapLatest` swaps browse ↔ search pager; both `PagingData<PokemonListEntry>`; debounce non-blank queries ~300ms.
3. Separate `pokemon_index` table; lazy `ensureSearchIndex()` (fetch only if empty, best-effort); search is a Room-only `Pager` (no `RemoteMediator`).

## Global Constraints

- Package root `com.rogerparis.pokedex`; layering `ui → domain → data`.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17. Page size 20.
- `ensureSearchIndex()` is best-effort: it swallows network errors (offline + empty index → no results, no crash).
- `flatMapLatest`/`debounce` need `@OptIn(ExperimentalCoroutinesApi::class)`.
- No raw exceptions above the repository.
- **Claude never stages or commits.** Suggested commit per task; user commits.
- Comments: explain non-obvious *why* only. Stable lambdas in Compose.

## File Structure

```
app/src/main/java/com/rogerparis/pokedex/
├── data/local/PokemonIndexEntity.kt     # @Entity "pokemon_index" (id, name, artworkUrl)
├── data/local/PokemonIndexDao.kt        # insertAll, count, search(query): PagingSource
├── data/local/PokedexDatabase.kt        # + entity, version 4, pokemonIndexDao()
├── data/local/Migrations.kt             # + MIGRATION_3_4
├── di/DatabaseModule.kt                 # provide index DAO; addMigrations(..., 3_4)
├── data/mapper/PokemonMappers.kt        # + PokemonIndexEntity.toListEntry()
├── data/repository/DefaultPokemonRepository.kt  # ensureSearchIndex() + searchPager() (+ ctor dep)
├── domain/repository/PokemonRepository.kt       # + the two methods
├── ui/list/PokemonListViewModel.kt      # query StateFlow + debounce + flatMapLatest
└── ui/list/PokemonListScreen.kt         # search field + empty-results state
app/src/test/java/com/rogerparis/pokedex/
├── data/local/PokemonIndexDaoTest.kt           # LIKE filtering
└── data/repository/DefaultPokemonRepositoryTest.kt  # ensureSearchIndex + ctor
```

---

## Task 1: Index table, DAO, DB v4 + migration, mapper

**Files:** Create `data/local/PokemonIndexEntity.kt`, `data/local/PokemonIndexDao.kt`; modify `PokedexDatabase.kt`, `Migrations.kt`, `di/DatabaseModule.kt`, `data/mapper/PokemonMappers.kt`

- [ ] **Step 1: Entity**

Create `data/local/PokemonIndexEntity.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon_index")
data class PokemonIndexEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
)
```

- [ ] **Step 2: DAO**

Create `data/local/PokemonIndexDao.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PokemonIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PokemonIndexEntity>)

    @Query("SELECT COUNT(*) FROM pokemon_index")
    suspend fun count(): Int

    @Query("SELECT * FROM pokemon_index WHERE name LIKE '%' || :query || '%' ORDER BY id")
    fun search(query: String): PagingSource<Int, PokemonIndexEntity>
}
```

- [ ] **Step 3: Database v4**

In `PokedexDatabase.kt`:
```kotlin
@Database(
    entities = [FavoriteEntity::class, PokemonEntity::class, PokemonIndexEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pokemonDao(): PokemonDao
    abstract fun pokemonIndexDao(): PokemonIndexDao
}
```

- [ ] **Step 4: Migration 3→4**

In `Migrations.kt`, add:
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pokemon_index` (" +
                "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`artworkUrl` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
```

- [ ] **Step 5: DI**

In `di/DatabaseModule.kt`:
- Imports: `import com.rogerparis.pokedex.data.local.MIGRATION_3_4`, `import com.rogerparis.pokedex.data.local.PokemonIndexDao`.
- Migrations: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.
- Provider:
```kotlin
    @Provides
    fun providePokemonIndexDao(database: PokedexDatabase): PokemonIndexDao = database.pokemonIndexDao()
```

- [ ] **Step 6: Mapper**

Append to `data/mapper/PokemonMappers.kt`:
```kotlin
import com.rogerparis.pokedex.data.local.PokemonIndexEntity
```
```kotlin
fun PokemonIndexEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
```

- [ ] **Step 7: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Suggested commit**

```
feat(data): add pokemon_index table, DAO, DB v4 + Migration(3,4)
```

---

## Task 2: Index DAO test (LIKE filtering, Robolectric)

**Files:** Create `data/local/PokemonIndexDaoTest.kt`

- [ ] **Step 1: Write the test** — insert rows, drive the search `PagingSource` directly and assert `LIKE` matches.

Create `app/src/test/java/com/rogerparis/pokedex/data/local/PokemonIndexDaoTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PokemonIndexDaoTest {
    private lateinit var db: PokedexDatabase
    private lateinit var dao: PokemonIndexDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PokedexDatabase::class.java).build()
        dao = db.pokemonIndexDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `search matches by substring case-insensitively, ordered by id`() = runTest {
        dao.insertAll(
            listOf(
                PokemonIndexEntity(1, "bulbasaur", "u1"),
                PokemonIndexEntity(4, "charmander", "u4"),
                PokemonIndexEntity(6, "charizard", "u6"),
            ),
        )

        val result = dao.search("char").load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(4, 6), page.data.map { it.id })
    }
}
```
(SQLite `LIKE` is case-insensitive for ASCII by default, so "char" matches "Charmander" too.)

- [ ] **Step 2: Run**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.local.PokemonIndexDaoTest"`
Expected: PASS.

- [ ] **Step 3: Suggested commit**

```
test: cover pokemon_index LIKE search
```

---

## Task 3: Repository — ensureSearchIndex + searchPager (TDD)

**Files:** Modify `domain/repository/PokemonRepository.kt`, `data/repository/DefaultPokemonRepository.kt`; update `data/repository/DefaultPokemonRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun ensureSearchIndex()`; `fun searchPager(query: String): Flow<PagingData<PokemonListEntry>>`. Constructor gains `pokemonIndexDao: PokemonIndexDao`.

- [ ] **Step 1: Extend the interface**

In `domain/repository/PokemonRepository.kt`, add:
```kotlin
    suspend fun ensureSearchIndex()
    fun searchPager(query: String): Flow<PagingData<PokemonListEntry>>
```

- [ ] **Step 2: Update the repository test (ctor + new tests)**

In `DefaultPokemonRepositoryTest.kt`:
- Imports:
```kotlin
import com.rogerparis.pokedex.data.local.PokemonIndexDao
import io.mockk.coVerify
import io.mockk.coJustRun
import io.mockk.just
import io.mockk.Runs
```
- Add the dao + pass it:
```kotlin
    private val pokemonIndexDao = mockk<PokemonIndexDao>()
    private val repository = DefaultPokemonRepository(api, favoriteDao, database, pokemonDao, pokemonIndexDao)
```
- Add tests for the fetch-if-empty logic:
```kotlin
    @Test
    fun `ensureSearchIndex fetches and inserts when index is empty`() = runTest {
        coEvery { pokemonIndexDao.count() } returns 0
        coEvery { api.getPokemonList(limit = 100_000, offset = 0) } returns
            com.rogerparis.pokedex.data.remote.dto.PokemonListResponse(
                count = 1, next = null, previous = null,
                results = listOf(
                    com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto(
                        name = "bulbasaur", url = "https://pokeapi.co/api/v2/pokemon/1/",
                    ),
                ),
            )
        coJustRun { pokemonIndexDao.insertAll(any()) }

        repository.ensureSearchIndex()

        coVerify { pokemonIndexDao.insertAll(any()) }
    }

    @Test
    fun `ensureSearchIndex does nothing when already populated`() = runTest {
        coEvery { pokemonIndexDao.count() } returns 1300

        repository.ensureSearchIndex()

        coVerify(exactly = 0) { api.getPokemonList(any(), any()) }
    }
```

- [ ] **Step 3: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.repository.DefaultPokemonRepositoryTest"`
Expected: FAIL — constructor arity / `ensureSearchIndex` unresolved.

- [ ] **Step 4: Implement in `DefaultPokemonRepository`**

- Imports:
```kotlin
import com.rogerparis.pokedex.data.local.PokemonIndexDao
import com.rogerparis.pokedex.data.local.PokemonIndexEntity
```
- Add the constructor param:
```kotlin
    private val pokemonIndexDao: PokemonIndexDao,
```
- Add the methods:
```kotlin
    override suspend fun ensureSearchIndex() {
        if (pokemonIndexDao.count() > 0) return
        try {
            val response = api.getPokemonList(limit = 100_000, offset = 0)
            pokemonIndexDao.insertAll(
                response.results.map {
                    val entry = it.toEntry()
                    PokemonIndexEntity(id = entry.id, name = entry.name, artworkUrl = entry.artworkUrl)
                },
            )
        } catch (e: IOException) {
            // best-effort: offline search is unavailable until the index is populated
        } catch (e: HttpException) {
            // best-effort
        }
    }

    override fun searchPager(query: String): Flow<PagingData<PokemonListEntry>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { pokemonIndexDao.search(query) },
        ).flow.map { pagingData -> pagingData.map { entity -> entity.toListEntry() } }
```
Note: `pokemonIndexDao.search(query)` returns `PagingSource<Int, PokemonIndexEntity>`; the `entity.toListEntry()` here resolves to the `PokemonIndexEntity` overload (the `PokemonEntity`/`FavoriteEntity` ones differ by receiver).

- [ ] **Step 5: Run, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.repository.DefaultPokemonRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Build + full suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Suggested commit**

```
feat(data): add ensureSearchIndex + local search pager
```

---

## Task 4: ViewModel — debounced query + mode switch

**Files:** Modify `ui/list/PokemonListViewModel.kt`

**Interfaces:**
- Produces: `query: StateFlow<String>`, `fun onQueryChange(q: String)`; `pokemon` now switches browse ↔ search.

- [ ] **Step 1: Rewrite the ViewModel**

Replace `ui/list/PokemonListViewModel.kt`:
```kotlin
package com.rogerparis.pokedex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PokemonListViewModel @Inject constructor(
    private val repository: PokemonRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    val pokemon: Flow<PagingData<PokemonListEntry>> =
        _query
            .debounce { q -> if (q.isBlank()) 0L else 300L }
            .flatMapLatest { q ->
                if (q.isBlank()) {
                    repository.pokemonPager()
                } else {
                    repository.ensureSearchIndex()
                    repository.searchPager(q.trim())
                }
            }
            .cachedIn(viewModelScope)
}
```
Note: blank query has 0 ms debounce (browse loads immediately); typed queries debounce 300 ms. `flatMapLatest` cancels the previous pager when the query changes — exactly the "latest wins" search behavior. `ensureSearchIndex()` runs before the search pager so the `LIKE` query reads a populated table (and Room's `PagingSource` auto-refreshes as rows land).

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Suggested commit**

```
feat(ui): switch list between browse and debounced search
```

---

## Task 5: Search field on the list screen

**Files:** Modify `ui/list/PokemonListScreen.kt`

- [ ] **Step 1: Add the search field + empty-results state**

In `PokemonListScreen.kt`:
- Imports:
```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerparis.pokedex.ui.components.EmptyState
```
- Wrap the content in a `Column` with the field on top:
```kotlin
@Composable
fun PokemonListScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonListViewModel = hiltViewModel(),
) {
    val items = viewModel.pokemon.collectAsLazyPagingItems()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search Pokémon") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        when (items.loadState.refresh) {
            is LoadState.Loading -> LoadingState()
            is LoadState.Error -> ErrorState(
                message = "Couldn't load Pokémon. Check your connection.",
                onRetry = items::retry,
            )
            else -> if (items.itemCount == 0 && query.isNotBlank()) {
                EmptyState("No Pokémon match \"$query\".")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val entry = items[index] ?: return@items
                        PokemonRow(entry = entry, onClick = onPokemonClick)
                    }
                    when (items.loadState.append) {
                        is LoadState.Loading -> item { LoadingFooter() }
                        is LoadState.Error -> item { ErrorFooter(onRetry = items::retry) }
                        else -> Unit
                    }
                }
            }
        }
    }
}
```
(Remove the `modifier` argument previously passed to `LoadingState(modifier)`/`ErrorState(..., modifier)` since they now sit inside the `Column`; `KeyboardOptions` import can be dropped if unused — keep imports the compiler accepts.)

- [ ] **Step 2: Build + device**

Run: `./gradlew installDebug`
- Type "char" → list narrows to Charmander/Charizard/etc. (first search triggers the one-time index fetch).
- Clear the field → browse list returns (paged).
- Type gibberish → "No Pokémon match …".
- With the index already fetched, search works in Airplane mode too.

- [ ] **Step 3: Suggested commit**

```
feat(ui): add search field to the browse screen
```

---

## Self-Review

**Decision coverage:** bulk index + `LIKE` (Tasks 1–3) ✓; search field on Browse with `flatMapLatest`/`debounce` swap (Tasks 4–5) ✓; separate table + lazy `ensureSearchIndex` + Room-only search `Pager` (Tasks 1, 3) ✓; `Migration(3,4)` (Task 1) ✓.

**Placeholder scan:** No TBD; full code + commands with expected output. The repository constructor change's break of the repo test is updated explicitly (Task 3 Step 2).

**Type consistency:** `PokemonIndexEntity(id, name, artworkUrl)`, `PokemonIndexDao.{insertAll, count, search}`, repo `{ensureSearchIndex, searchPager}`, `DefaultPokemonRepository(api, favoriteDao, database, pokemonDao, pokemonIndexDao)`, `PokemonListViewModel.{query, onQueryChange, pokemon}`, `PokemonIndexEntity.toListEntry()` — consistent across code and tests. ✓

**Note:** the browse/search switch is a thin `flatMapLatest` over Paging flows (hard to unit-test meaningfully); its pieces are covered by the DAO `LIKE` test and the repository `ensureSearchIndex` tests, plus device verification.

## Notes / remaining
- The index is fetched once and never refreshed; fine for a near-static dataset. A production app might add a periodic refresh.
- Remaining stretch: team builder, full Hilt-instrumented UI tests, `exportSchema`.
```
