# Pokédex Feature — Favorites + Room Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add local persistence with Room — favorite a Pokémon from its detail screen, see favorites in their own tab, and have them survive offline.

**Architecture:** Builds on foundation + list + detail plans. Adds a Room database (the create/update/delete half of CRUD; PokéAPI is read-only). Favorites are a denormalized snapshot, so the favorites list renders with no network. Spec chunk 6.

**Tech Stack:** Room (KSP) + Coroutines/Flow, Hilt, Compose. Tests: JUnit + MockK + coroutines-test + Turbine (JVM) and Robolectric (Room DAO).

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Global Constraints

- Package root `com.rogerparis.pokedex`; layering `ui → domain → data`, inward only.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17. Plugin versions at root (`apply false`).
- Room is the **single source of truth for favorites only** — the browse list stays network-paged. Favorites must render offline (denormalized snapshot, no network read).
- Favorite toggle lives **only on the detail screen** (where full data exists).
- New repository methods return `Flow` for observation; writes are `suspend`. No raw exceptions above the repository.
- **Claude never stages or commits.** Each task ends with a suggested commit; the user commits. Plain Conventional Commits messages.
- Comments: explain non-obvious *why* only. Compose memoization: hoist state, stable lambdas.

## File Structure

```
gradle/libs.versions.toml                                       # + room, robolectric
app/build.gradle.kts                                            # + room (ksp), robolectric (test), testOptions
app/src/main/java/com/rogerparis/pokedex/
├── data/local/
│   ├── FavoriteEntity.kt                                       # @Entity (denormalized snapshot)
│   ├── Converters.kt                                           # List<String> <-> String TypeConverter
│   ├── FavoriteDao.kt                                          # @Dao: add/remove/observeAll/observeIsFavorite
│   └── PokedexDatabase.kt                                      # @Database
├── data/mapper/FavoriteMappers.kt                             # Pokemon->Entity, Entity->ListEntry
├── data/repository/DefaultPokemonRepository.kt                # + favorites methods (now also takes FavoriteDao)
├── domain/repository/PokemonRepository.kt                     # + favorites methods
├── di/DatabaseModule.kt                                       # provide DB + DAO
├── ui/detail/PokemonDetailViewModel.kt                        # + isFavorite + toggleFavorite()
├── ui/detail/PokemonDetailScreen.kt                           # + favorite heart action
├── ui/favorites/FavoritesViewModel.kt                         # observe favorites
├── ui/favorites/FavoritesScreen.kt                            # list + empty state
└── ui/navigation/PokedexNavHost.kt                            # wire Favorites screen
app/src/test/java/com/rogerparis/pokedex/
├── data/local/FavoriteDaoTest.kt                              # Robolectric + in-memory Room
├── data/mapper/FavoriteMappersTest.kt                         # JVM
├── data/repository/DefaultPokemonRepositoryTest.kt           # UPDATE: constructor now takes a dao
└── ui/detail/PokemonDetailViewModelTest.kt                    # UPDATE: constructor + favorite tests
```

---

## Task 1: Add Room + Robolectric dependencies

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [ ] **Step 1: Catalog versions + libraries**

`[versions]`:
```toml
room = "2.7.1"
robolectric = "4.14.1"
```
`[libraries]`:
```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
```

- [ ] **Step 2: `app/build.gradle.kts` — deps + Robolectric test option**

In `dependencies { }`:
```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
testImplementation(libs.robolectric)
```
In the `android { }` block, add:
```kotlin
testOptions {
    unitTests.isIncludeAndroidResources = true
}
```

- [ ] **Step 3: Sync (version gate)**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. If `room 2.7.1` or `robolectric 4.14.1` don't resolve, bump to the nearest existing stable and re-run.

- [ ] **Step 4: Suggested commit**

```
build: add Room (KSP) and Robolectric
```

---

## Task 2: Room entity, converter, DAO, database, DI

**Files:** Create `data/local/FavoriteEntity.kt`, `data/local/Converters.kt`, `data/local/FavoriteDao.kt`, `data/local/PokedexDatabase.kt`, `di/DatabaseModule.kt`

**Interfaces:**
- Produces:
  - `FavoriteEntity(id, name, artworkUrl, types: List<String>)` table `favorites`
  - `FavoriteDao { suspend add(e); suspend remove(id); observeAll(): Flow<List<FavoriteEntity>>; observeIsFavorite(id): Flow<Boolean> }`
  - `PokedexDatabase` + Hilt provides `PokedexDatabase` and `FavoriteDao`.

- [ ] **Step 1: Entity**

Create `app/src/main/java/com/rogerparis/pokedex/data/local/FavoriteEntity.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val types: List<String>,
)
```

- [ ] **Step 2: TypeConverter** (Room stores primitives; `List<String>` needs a converter)

Create `app/src/main/java/com/rogerparis/pokedex/data/local/Converters.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)
}
```

- [ ] **Step 3: DAO**

Create `app/src/main/java/com/rogerparis/pokedex/data/local/FavoriteDao.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun remove(id: Int)

    @Query("SELECT * FROM favorites ORDER BY id")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    fun observeIsFavorite(id: Int): Flow<Boolean>
}
```

- [ ] **Step 4: Database**

Create `app/src/main/java/com/rogerparis/pokedex/data/local/PokedexDatabase.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FavoriteEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
```

- [ ] **Step 5: Hilt DatabaseModule**

Create `app/src/main/java/com/rogerparis/pokedex/di/DatabaseModule.kt`:
```kotlin
package com.rogerparis.pokedex.di

import android.content.Context
import androidx.room.Room
import com.rogerparis.pokedex.data.local.FavoriteDao
import com.rogerparis.pokedex.data.local.PokedexDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PokedexDatabase =
        Room.databaseBuilder(context, PokedexDatabase::class.java, "pokedex.db").build()

    @Provides
    fun provideFavoriteDao(database: PokedexDatabase): FavoriteDao = database.favoriteDao()
}
```

- [ ] **Step 6: Build (KSP generates the Room code)**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Room's KSP processor validates the DAO queries at compile time — a typo in SQL fails the build here.

- [ ] **Step 7: Suggested commit**

```
feat(data): add Room database, favorite entity, DAO, converters, DI
```

---

## Task 3: Favorite DAO test (Robolectric, in-memory Room)

**Files:** Test `app/src/test/java/com/rogerparis/pokedex/data/local/FavoriteDaoTest.kt`

Room needs an Android runtime even in-memory, so this runs under Robolectric (on the JVM, no emulator). `@Config(sdk = [34])` pins a Robolectric-supported SDK regardless of `compileSdk 36`.

- [ ] **Step 1: Write the DAO test**

Create `app/src/test/java/com/rogerparis/pokedex/data/local/FavoriteDaoTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Room
import app.cash.turbine.test
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
class FavoriteDaoTest {
    private lateinit var db: PokedexDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            PokedexDatabase::class.java,
        ).build()
        dao = db.favoriteDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(id: Int) =
        FavoriteEntity(id = id, name = "p$id", artworkUrl = "u$id", types = listOf("grass"))

    @Test
    fun `add then observeAll emits the favorite`() = runTest {
        dao.add(entity(1))
        dao.observeAll().test {
            assertEquals(listOf(entity(1)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeIsFavorite reflects add and remove`() = runTest {
        dao.observeIsFavorite(1).test {
            assertEquals(false, awaitItem())
            dao.add(entity(1))
            assertEquals(true, awaitItem())
            dao.remove(1)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.local.FavoriteDaoTest"`
Expected: PASS (2 tests). First run downloads a Robolectric android-all jar (needs network). If it complains the SDK isn't available, change `@Config(sdk = [34])` to an SDK Robolectric lists as supported and re-run.

- [ ] **Step 3: Suggested commit**

```
test: add Robolectric FavoriteDao tests (in-memory Room)
```

---

## Task 4: Favorite mappers + repository methods (TDD mappers)

**Files:**
- Create `data/mapper/FavoriteMappers.kt`
- Modify `domain/repository/PokemonRepository.kt`, `data/repository/DefaultPokemonRepository.kt`
- Test create `data/mapper/FavoriteMappersTest.kt`; **update** `data/repository/DefaultPokemonRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `Pokemon.toFavoriteEntity(): FavoriteEntity`, `FavoriteEntity.toListEntry(): PokemonListEntry`
  - `PokemonRepository`: `observeFavorites(): Flow<List<PokemonListEntry>>`, `isFavorite(id): Flow<Boolean>`, `suspend addFavorite(pokemon)`, `suspend removeFavorite(id)`
  - `DefaultPokemonRepository` constructor now `(api: PokeApi, favoriteDao: FavoriteDao)`.

- [ ] **Step 1: Write the failing mapper test**

Create `app/src/test/java/com/rogerparis/pokedex/data/mapper/FavoriteMappersTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.Stat
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteMappersTest {
    @Test
    fun `pokemon maps to favorite entity snapshot`() {
        val pokemon = Pokemon(
            id = 25, name = "pikachu", heightDm = 4, weightHg = 60,
            types = listOf("electric"), stats = listOf(Stat("hp", 35)),
            abilities = listOf("static"), artworkUrl = "url25",
        )
        val entity = pokemon.toFavoriteEntity()
        assertEquals(FavoriteEntity(id = 25, name = "pikachu", artworkUrl = "url25", types = listOf("electric")), entity)
    }

    @Test
    fun `favorite entity maps to list entry`() {
        val entity = FavoriteEntity(id = 25, name = "pikachu", artworkUrl = "url25", types = listOf("electric"))
        val entry = entity.toListEntry()
        assertEquals(25, entry.id)
        assertEquals("pikachu", entry.name)
        assertEquals("url25", entry.artworkUrl)
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.mapper.FavoriteMappersTest"`
Expected: FAIL — `toFavoriteEntity` / `toListEntry` unresolved.

- [ ] **Step 3: Write the mappers**

Create `app/src/main/java/com/rogerparis/pokedex/data/mapper/FavoriteMappers.kt`:
```kotlin
package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry

fun Pokemon.toFavoriteEntity(): FavoriteEntity =
    FavoriteEntity(id = id, name = name, artworkUrl = artworkUrl, types = types)

fun FavoriteEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.mapper.FavoriteMappersTest"`
Expected: PASS.

- [ ] **Step 5: Extend the repository interface**

In `domain/repository/PokemonRepository.kt` add imports + methods:
```kotlin
import com.rogerparis.pokedex.domain.model.PokemonListEntry  // (already imported)
```
```kotlin
interface PokemonRepository {
    fun pokemonPager(): Flow<PagingData<PokemonListEntry>>
    suspend fun getPokemon(id: Int): ApiResult<Pokemon>
    fun observeFavorites(): Flow<List<PokemonListEntry>>
    fun isFavorite(id: Int): Flow<Boolean>
    suspend fun addFavorite(pokemon: Pokemon)
    suspend fun removeFavorite(id: Int)
}
```

- [ ] **Step 6: Implement in `DefaultPokemonRepository`**

Add the `FavoriteDao` constructor param + imports + methods:
```kotlin
import com.rogerparis.pokedex.data.local.FavoriteDao
import com.rogerparis.pokedex.data.mapper.toFavoriteEntity
import com.rogerparis.pokedex.data.mapper.toListEntry
import kotlinx.coroutines.flow.map
```
Change the constructor:
```kotlin
class DefaultPokemonRepository @Inject constructor(
    private val api: PokeApi,
    private val favoriteDao: FavoriteDao,
) : PokemonRepository {
```
Add the methods (anywhere in the class body):
```kotlin
    override fun observeFavorites(): Flow<List<PokemonListEntry>> =
        favoriteDao.observeAll().map { list -> list.map { it.toListEntry() } }

    override fun isFavorite(id: Int): Flow<Boolean> = favoriteDao.observeIsFavorite(id)

    override suspend fun addFavorite(pokemon: Pokemon) = favoriteDao.add(pokemon.toFavoriteEntity())

    override suspend fun removeFavorite(id: Int) = favoriteDao.remove(id)
```

- [ ] **Step 7: Update the existing repository test constructor**

The existing `DefaultPokemonRepositoryTest` constructs `DefaultPokemonRepository(api)`, which no longer compiles. In `app/src/test/java/com/rogerparis/pokedex/data/repository/DefaultPokemonRepositoryTest.kt`:
- Add imports:
```kotlin
import com.rogerparis.pokedex.data.local.FavoriteDao
```
- Change the field:
```kotlin
    private val favoriteDao = mockk<FavoriteDao>()
    private val repository = DefaultPokemonRepository(api, favoriteDao)
```

- [ ] **Step 8: Build + full unit suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (including the updated repository test and DAO test).

- [ ] **Step 9: Suggested commit**

```
feat(data): add favorite mappers + repository favorites methods
```

---

## Task 5: Detail favorite toggle (ViewModel, TDD)

**Files:** Modify `ui/detail/PokemonDetailViewModel.kt`; **update** `ui/detail/PokemonDetailViewModelTest.kt`

**Interfaces:**
- Produces: `PokemonDetailViewModel.isFavorite: StateFlow<Boolean>` and `fun toggleFavorite()`.

- [ ] **Step 1: Add favorite tests to the ViewModel test**

In `PokemonDetailViewModelTest.kt`, add imports + tests. The existing tests stub `getPokemon`; the favorite ones also stub `isFavorite`. Add to the class:
```kotlin
import io.mockk.coVerify
import io.mockk.coJustRun
import kotlinx.coroutines.flow.flowOf
```
```kotlin
    @Test
    fun `toggleFavorite adds when not currently favorite`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())
        coEvery { repository.isFavorite(1) } returns flowOf(false)
        coJustRun { repository.addFavorite(any()) }

        val vm = viewModel()
        vm.state.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() } // let load settle
        vm.toggleFavorite()
        runCurrent()

        coVerify { repository.addFavorite(pokemon()) }
    }

    @Test
    fun `toggleFavorite removes when currently favorite`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())
        coEvery { repository.isFavorite(1) } returns flowOf(true)
        coJustRun { repository.removeFavorite(1) }

        val vm = viewModel()
        vm.state.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.toggleFavorite()
        runCurrent()

        coVerify { repository.removeFavorite(1) }
    }
```
Add `import kotlinx.coroutines.test.runCurrent`. The existing two tests need `isFavorite` stubbed too — add `coEvery { repository.isFavorite(1) } returns flowOf(false)` to each (or in a `@Before`). Simplest: add a `@Before` that stubs the common default:
```kotlin
import org.junit.Before
```
```kotlin
    @Before
    fun stubFavorite() {
        coEvery { repository.isFavorite(1) } returns flowOf(false)
    }
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.ui.detail.PokemonDetailViewModelTest"`
Expected: FAIL — `isFavorite` / `toggleFavorite` unresolved (and `addFavorite`/`removeFavorite` on the mock).

- [ ] **Step 3: Implement in the ViewModel**

In `PokemonDetailViewModel.kt` add imports + members:
```kotlin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
```
```kotlin
    val isFavorite: StateFlow<Boolean> =
        repository.isFavorite(pokemonId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        val current = state.value
        if (current !is DetailUiState.Success) return
        viewModelScope.launch {
            if (isFavorite.value) repository.removeFavorite(pokemonId)
            else repository.addFavorite(current.pokemon)
        }
    }
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.ui.detail.PokemonDetailViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Suggested commit**

```
feat(ui): add favorite toggle to detail ViewModel
```

---

## Task 6: Detail screen favorite action (Compose)

**Files:** Modify `ui/detail/PokemonDetailScreen.kt`

- [ ] **Step 1: Add a favorite heart to the top bar**

In `PokemonDetailScreen.kt`:
- Add imports:
```kotlin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```
(`collectAsStateWithLifecycle` is already imported.)
- Collect favorite state at the top of `PokemonDetailScreen`, next to `state`:
```kotlin
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
```
- Add an `actions` lambda to the `TopAppBar` (only meaningful once loaded, but harmless otherwise):
```kotlin
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        )
                    }
                },
```
NOTE: if `Icons.Filled.Favorite` / `Icons.Outlined.FavoriteBorder` are unresolved, they're in the larger `material-icons-extended` artifact — swap `material-icons-core` for `material-icons-extended` in the catalog/build and re-sync.

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Suggested commit**

```
feat(ui): add favorite heart action to detail screen
```

---

## Task 7: Favorites screen + tab

**Files:** Create `ui/favorites/FavoritesViewModel.kt`, `ui/favorites/FavoritesScreen.kt`; modify `ui/navigation/PokedexNavHost.kt`

**Interfaces:**
- Produces: `FavoritesViewModel.favorites: StateFlow<List<PokemonListEntry>>`; `@Composable FavoritesScreen(onPokemonClick: (Int) -> Unit, ...)`.

- [ ] **Step 1: ViewModel**

Create `app/src/main/java/com/rogerparis/pokedex/ui/favorites/FavoritesViewModel.kt`:
```kotlin
package com.rogerparis.pokedex.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    repository: PokemonRepository,
) : ViewModel() {
    val favorites: StateFlow<List<PokemonListEntry>> =
        repository.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 2: Screen**

Create `app/src/main/java/com/rogerparis/pokedex/ui/favorites/FavoritesScreen.kt`:
```kotlin
package com.rogerparis.pokedex.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.model.PokemonListEntry

@Composable
fun FavoritesScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No favorites yet. Tap the heart on a Pokémon.")
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items = favorites, key = { it.id }) { entry ->
            FavoriteRow(entry = entry, onClick = onPokemonClick)
        }
    }
}

@Composable
private fun FavoriteRow(entry: PokemonListEntry, onClick: (Int) -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(entry.id) },
        leadingContent = {
            AsyncImage(
                model = entry.artworkUrl,
                contentDescription = entry.name,
                modifier = Modifier.size(56.dp),
            )
        },
        headlineContent = { Text(entry.name.capitalize(Locale.current)) },
    )
}
```

- [ ] **Step 3: Wire into nav** — replace the Favorites stub

In `ui/navigation/PokedexNavHost.kt`:
- Add import: `import com.rogerparis.pokedex.ui.favorites.FavoritesScreen`
- Replace:
```kotlin
            composable<FavoritesRoute> {
                Text("Favorites — coming in the next plan")
            }
```
with:
```kotlin
            composable<FavoritesRoute> {
                FavoritesScreen(onPokemonClick = { id -> navController.navigate(DetailRoute(id)) })
            }
```
- Remove the now-unused `import androidx.compose.material3.Text` if nothing else uses it (compiler will flag).

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run on device (manual verification)**

Run: `./gradlew installDebug`
- Open a Pokémon → tap the heart (outline → filled).
- Go to the **Favorites** tab → it appears.
- Kill and reopen the app → favorite persists (Room on disk).
- Enable Airplane mode → Favorites tab still lists favorites (offline, from Room). (The browse list won't load offline — expected.)
- Open the favorite, tap the filled heart → removed; Favorites tab updates live.

- [ ] **Step 6: Suggested commit**

```
feat(ui): add Favorites screen + tab backed by Room
```

---

## Self-Review

**Spec coverage (chunk 6):**
- Room local persistence (entity/DAO/DB/converter/DI) → Tasks 1–2. ✓
- Favorites = denormalized snapshot, render offline → entity + `observeFavorites` (Tasks 2, 4, 7). ✓
- Favorite toggle on detail only → Tasks 5–6. ✓
- Favorites tab reads Room → Task 7. ✓
- Local create/delete (the CRUD writes) → `addFavorite`/`removeFavorite` (Task 4). ✓
- Robolectric DAO tests → Task 3. ✓
- Out of scope (next/stretch): RemoteMediator offline browse, "My Team", search, type-colored chips, Compose UI tests, storing full stats for offline detail.

**Placeholder scan:** No TBD; all code complete; commands show expected output. The two existing tests that break from the repository constructor change are explicitly updated (Task 4 Step 7; Task 5 Steps 1–3). ✓

**Type consistency:** `FavoriteEntity(id, name, artworkUrl, types)`, `FavoriteDao.{add,remove,observeAll,observeIsFavorite}`, repository `{observeFavorites,isFavorite,addFavorite,removeFavorite}`, `DefaultPokemonRepository(api, favoriteDao)`, `PokemonDetailViewModel.{isFavorite,toggleFavorite}`, `FavoritesViewModel.favorites` — consistent across Interfaces, code, and tests. Reuses `Pokemon`, `PokemonListEntry`, `toListEntry`. ✓

## Notes carried forward (chunk 7 + stretch)
- Polish: shared loading/empty/error composables; type-colored chips; capitalize helper extracted.
- Instrumented Compose UI tests (list renders, detail toggle).
- Stretch: store full stats in the favorite snapshot for offline detail; RemoteMediator offline browse; My Team; search/type filter.
