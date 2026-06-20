# Pokédex — Team Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a team of up to 6 Pokémon — add/remove from the detail screen, view and reorder the team in its own tab, all offline.

**Architecture:** A `team_members` Room table (denormalized snapshot + `position`), mirroring favorites. Repository enforces max-6 and does reorder transactionally. Detail screen gains a team toggle (with a one-shot "team full" message); a new Team tab lists members with up/down/remove. DB v4 → v5. Spec stretch.

**Tech Stack:** Room (`position` ordering, `withTransaction`), Coroutines/Flow/StateFlow, Compose (Snackbar, icon actions). Tests: Robolectric + in-memory Room + MockK + Turbine.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Decisions (from grill-me)

1. Separate `team_members` table (denormalized snapshot, `position` order) — not reusing favorites, not a serialized blob. DB v5 + `Migration(4,5)`.
2. Reorder via **up/down move actions** (not drag-and-drop): load ordered list → swap neighbors → renumber `0..n-1` → write in one transaction.
3. Add/remove from the **detail screen** (mirrors favorite heart); max-6 enforced in the repository; a one-shot "team full" message surfaced via a `userMessage` StateFlow + Snackbar. New **Team** tab.

## Global Constraints

- Package root `com.rogerparis.pokedex`; layering `ui → domain → data`.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17. Team max = 6.
- Team renders offline (denormalized snapshot). No raw exceptions above the repository.
- **Claude never stages or commits.** Suggested commit per task; user commits.
- Comments: explain non-obvious *why* only. Stable lambdas in Compose.

## File Structure

```
app/src/main/java/com/rogerparis/pokedex/
├── data/local/TeamMemberEntity.kt       # @Entity "team_members" (id, name, artworkUrl, position)
├── data/local/TeamMemberDao.kt          # upsert, upsertAll, remove, count, observeAll, getAllOnce, observeIsMember
├── data/local/PokedexDatabase.kt        # + entity, version 5, teamMemberDao()
├── data/local/Migrations.kt             # + MIGRATION_4_5
├── di/DatabaseModule.kt                 # provide team DAO; addMigrations(..., 4_5)
├── data/mapper/PokemonMappers.kt        # + TeamMemberEntity.toListEntry()
├── data/repository/DefaultPokemonRepository.kt  # team methods (+ ctor dep)
├── domain/repository/PokemonRepository.kt       # + team methods
├── ui/detail/PokemonDetailViewModel.kt  # isInTeam + toggleTeam + userMessage
├── ui/detail/PokemonDetailScreen.kt     # team action icon + Snackbar
├── ui/team/TeamViewModel.kt             # observe team + move/remove
├── ui/team/TeamScreen.kt                # list + up/down/remove
└── ui/navigation/{Routes,PokedexNavHost}.kt  # TeamRoute + tab
app/src/test/java/com/rogerparis/pokedex/
├── data/local/TeamMemberDaoTest.kt
├── data/repository/DefaultPokemonRepositoryTest.kt   # ctor + team tests
├── data/local/MigrationTest.kt                       # chain + MIGRATION_4_5
└── ui/detail/PokemonDetailViewModelTest.kt           # team toggle tests
```

---

## Task 1: Team table, DAO, DB v5 + migration, mapper

**Files:** Create `data/local/TeamMemberEntity.kt`, `data/local/TeamMemberDao.kt`; modify `PokedexDatabase.kt`, `Migrations.kt`, `di/DatabaseModule.kt`, `data/mapper/PokemonMappers.kt`, `data/local/MigrationTest.kt`

- [ ] **Step 1: Entity**

Create `data/local/TeamMemberEntity.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val position: Int,
)
```

- [ ] **Step 2: DAO**

Create `data/local/TeamMemberDao.kt`:
```kotlin
package com.rogerparis.pokedex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: TeamMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<TeamMemberEntity>)

    @Query("DELETE FROM team_members WHERE id = :id")
    suspend fun remove(id: Int)

    @Query("SELECT COUNT(*) FROM team_members")
    suspend fun count(): Int

    @Query("SELECT * FROM team_members ORDER BY position")
    fun observeAll(): Flow<List<TeamMemberEntity>>

    @Query("SELECT * FROM team_members ORDER BY position")
    suspend fun getAllOnce(): List<TeamMemberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM team_members WHERE id = :id)")
    fun observeIsMember(id: Int): Flow<Boolean>
}
```

- [ ] **Step 3: Database v5**

In `PokedexDatabase.kt`:
```kotlin
@Database(
    entities = [FavoriteEntity::class, PokemonEntity::class, PokemonIndexEntity::class, TeamMemberEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pokemonDao(): PokemonDao
    abstract fun pokemonIndexDao(): PokemonIndexDao
    abstract fun teamMemberDao(): TeamMemberDao
}
```

- [ ] **Step 4: Migration 4→5**

In `Migrations.kt`, add:
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `team_members` (" +
                "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`artworkUrl` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
```

- [ ] **Step 5: DI**

In `di/DatabaseModule.kt`:
- Imports: `import com.rogerparis.pokedex.data.local.MIGRATION_4_5`, `import com.rogerparis.pokedex.data.local.TeamMemberDao`.
- Migrations: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`.
- Provider:
```kotlin
    @Provides
    fun provideTeamMemberDao(database: PokedexDatabase): TeamMemberDao = database.teamMemberDao()
```

- [ ] **Step 6: Mapper**

Append to `data/mapper/PokemonMappers.kt`:
```kotlin
import com.rogerparis.pokedex.data.local.TeamMemberEntity
```
```kotlin
fun TeamMemberEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
```

- [ ] **Step 7: Keep the migration test chain current**

The DB is now version 5; `MigrationTest` opens Room (→ v5) so it must supply all migrations. In `data/local/MigrationTest.kt`:
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] **Step 8: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Suggested commit**

```
feat(data): add team_members table, DAO, DB v5 + Migration(4,5)
```

---

## Task 2: Team DAO test (Robolectric)

**Files:** Create `data/local/TeamMemberDaoTest.kt`

- [ ] **Step 1: Write the test**

Create `app/src/test/java/com/rogerparis/pokedex/data/local/TeamMemberDaoTest.kt`:
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
class TeamMemberDaoTest {
    private lateinit var db: PokedexDatabase
    private lateinit var dao: TeamMemberDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PokedexDatabase::class.java).build()
        dao = db.teamMemberDao()
    }

    @After
    fun tearDown() = db.close()

    private fun member(id: Int, position: Int) =
        TeamMemberEntity(id = id, name = "p$id", artworkUrl = "u$id", position = position)

    @Test
    fun `observeAll returns members ordered by position`() = runTest {
        dao.upsert(member(2, position = 1))
        dao.upsert(member(1, position = 0))
        dao.observeAll().test {
            assertEquals(listOf(1, 2), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeIsMember reflects add and remove`() = runTest {
        dao.observeIsMember(1).test {
            assertEquals(false, awaitItem())
            dao.upsert(member(1, position = 0))
            assertEquals(true, awaitItem())
            dao.remove(1)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew testDebugUnitTest --tests '*TeamMemberDaoTest'`
Expected: PASS (2 tests).

- [ ] **Step 3: Suggested commit**

```
test: cover TeamMemberDao ordering and membership
```

---

## Task 3: Repository team methods (TDD)

**Files:** Modify `domain/repository/PokemonRepository.kt`, `data/repository/DefaultPokemonRepository.kt`; update `data/repository/DefaultPokemonRepositoryTest.kt`

**Interfaces:**
- Produces: `observeTeam(): Flow<List<PokemonListEntry>>`, `isInTeam(id): Flow<Boolean>`, `suspend addToTeam(pokemon): Boolean` (false if full), `suspend removeFromTeam(id)`, `suspend moveTeamMember(id, up: Boolean)`. Ctor gains `teamMemberDao: TeamMemberDao`.

- [ ] **Step 1: Extend the interface**

In `domain/repository/PokemonRepository.kt`, add:
```kotlin
    fun observeTeam(): Flow<List<PokemonListEntry>>
    fun isInTeam(id: Int): Flow<Boolean>
    suspend fun addToTeam(pokemon: Pokemon): Boolean
    suspend fun removeFromTeam(id: Int)
    suspend fun moveTeamMember(id: Int, up: Boolean)
```

- [ ] **Step 2: Update the repository test (ctor + team tests)**

In `DefaultPokemonRepositoryTest.kt`:
- Imports:
```kotlin
import com.rogerparis.pokedex.data.local.TeamMemberDao
import com.rogerparis.pokedex.data.local.TeamMemberEntity
import io.mockk.coVerify
import io.mockk.coJustRun
```
(some may already be present — keep one copy)
- Field + ctor:
```kotlin
    private val teamMemberDao = mockk<TeamMemberDao>()
    private val repository =
        DefaultPokemonRepository(api, favoriteDao, database, pokemonDao, pokemonIndexDao, teamMemberDao)
```
- Tests:
```kotlin
    @Test
    fun `addToTeam inserts and returns true when below max`() = runTest {
        coEvery { teamMemberDao.count() } returns 3
        coJustRun { teamMemberDao.upsert(any()) }

        val added = repository.addToTeam(
            Pokemon(1, "bulbasaur", 7, 69, listOf("grass"), emptyList(), listOf("overgrow"), "u"),
        )

        assertEquals(true, added)
        coVerify { teamMemberDao.upsert(any()) }
    }

    @Test
    fun `addToTeam returns false and does not insert when team is full`() = runTest {
        coEvery { teamMemberDao.count() } returns 6

        val added = repository.addToTeam(
            Pokemon(1, "bulbasaur", 7, 69, listOf("grass"), emptyList(), listOf("overgrow"), "u"),
        )

        assertEquals(false, added)
        coVerify(exactly = 0) { teamMemberDao.upsert(any()) }
    }
```

- [ ] **Step 3: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests '*DefaultPokemonRepositoryTest'`
Expected: FAIL — ctor arity / team methods unresolved.

- [ ] **Step 4: Implement in `DefaultPokemonRepository`**

- Imports:
```kotlin
import com.rogerparis.pokedex.data.local.TeamMemberDao
import com.rogerparis.pokedex.data.local.TeamMemberEntity
```
- Add ctor param:
```kotlin
    private val teamMemberDao: TeamMemberDao,
```
- Add a constant near the top of the file (outside the class, by the others) or as a companion — use a top-level private const:
```kotlin
private const val TEAM_MAX = 6
```
- Methods:
```kotlin
    override fun observeTeam(): Flow<List<PokemonListEntry>> =
        teamMemberDao.observeAll().map { list -> list.map { it.toListEntry() } }

    override fun isInTeam(id: Int): Flow<Boolean> = teamMemberDao.observeIsMember(id)

    override suspend fun addToTeam(pokemon: Pokemon): Boolean {
        val count = teamMemberDao.count()
        if (count >= TEAM_MAX) return false
        teamMemberDao.upsert(
            TeamMemberEntity(
                id = pokemon.id,
                name = pokemon.name,
                artworkUrl = pokemon.artworkUrl,
                position = count,
            ),
        )
        return true
    }

    override suspend fun removeFromTeam(id: Int) = teamMemberDao.remove(id)

    override suspend fun moveTeamMember(id: Int, up: Boolean) {
        val ordered = teamMemberDao.getAllOnce().toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target !in ordered.indices) return
        val tmp = ordered[index]
        ordered[index] = ordered[target]
        ordered[target] = tmp
        val renumbered = ordered.mapIndexed { i, m -> m.copy(position = i) }
        database.withTransaction { teamMemberDao.upsertAll(renumbered) }
    }
```
(`androidx.room.withTransaction` is already imported in this file from RemoteMediator work; if not, add it.)

- [ ] **Step 5: Run, verify pass; then full suite**

Run: `./gradlew testDebugUnitTest --tests '*DefaultPokemonRepositoryTest'`
Expected: PASS.
Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Suggested commit**

```
feat(data): add team repository methods (add/remove/reorder, max 6)
```

---

## Task 4: Detail ViewModel — team toggle + message (TDD)

**Files:** Modify `ui/detail/PokemonDetailViewModel.kt`; update `ui/detail/PokemonDetailViewModelTest.kt`

**Interfaces:**
- Produces: `isInTeam: StateFlow<Boolean>`, `userMessage: StateFlow<String?>`, `fun toggleTeam()`, `fun consumeMessage()`.

- [ ] **Step 1: Add team tests**

In `PokemonDetailViewModelTest.kt`:
- The existing `@Before stubFavoriteDefault` should also stub team membership so the new `stateIn` flows have a value. Extend it:
```kotlin
    @Before
    fun stubDefaults() {
        coEvery { repository.isFavorite(1) } returns flowOf(false)
        coEvery { repository.isInTeam(1) } returns flowOf(false)
    }
```
(Rename the old `stubFavoriteDefault` to this, or merge — only one `@Before`.)
- Tests:
```kotlin
    @Test
    fun `toggleTeam adds when not in team and succeeds`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())
        coEvery { repository.isInTeam(1) } returns flowOf(false)
        coEvery { repository.addToTeam(any()) } returns true

        val vm = viewModel()
        runCurrent()
        vm.toggleTeam()
        runCurrent()

        coVerify { repository.addToTeam(pokemon()) }
        assertEquals(null, vm.userMessage.value)
    }

    @Test
    fun `toggleTeam sets message when team is full`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())
        coEvery { repository.isInTeam(1) } returns flowOf(false)
        coEvery { repository.addToTeam(any()) } returns false

        val vm = viewModel()
        runCurrent()
        vm.toggleTeam()
        runCurrent()

        assertEquals("Team is full (max 6).", vm.userMessage.value)
    }

    @Test
    fun `toggleTeam removes when already in team`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())
        coEvery { repository.isInTeam(1) } returns flowOf(true)
        coJustRun { repository.removeFromTeam(1) }

        val vm = viewModel()
        runCurrent()
        vm.toggleTeam()
        runCurrent()

        coVerify { repository.removeFromTeam(1) }
    }
```
Add imports if missing: `io.mockk.coJustRun`, `io.mockk.coVerify`, `kotlinx.coroutines.flow.flowOf`, `kotlinx.coroutines.test.runCurrent` (most already present from favorites tests).

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew testDebugUnitTest --tests '*PokemonDetailViewModelTest'`
Expected: FAIL — `isInTeam`/`toggleTeam`/`userMessage` unresolved.

- [ ] **Step 3: Implement in the ViewModel**

In `PokemonDetailViewModel.kt`:
```kotlin
    val isInTeam: StateFlow<Boolean> =
        repository.isInTeam(pokemonId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun consumeMessage() {
        _userMessage.value = null
    }

    fun toggleTeam() {
        viewModelScope.launch {
            if (isInTeam.value) {
                repository.removeFromTeam(pokemonId)
            } else {
                val current = state.value as? DetailUiState.Success ?: return@launch
                if (!repository.addToTeam(current.pokemon)) {
                    _userMessage.value = "Team is full (max 6)."
                }
            }
        }
    }
```
(`MutableStateFlow`, `asStateFlow`, `stateIn`, `SharingStarted`, `launch` are already imported from favorites work.)

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew testDebugUnitTest --tests '*PokemonDetailViewModelTest'`
Expected: PASS.

- [ ] **Step 5: Suggested commit**

```
feat(ui): add team toggle + full-team message to detail ViewModel
```

---

## Task 5: Detail screen — team action + Snackbar

**Files:** Modify `ui/detail/PokemonDetailScreen.kt`

- [ ] **Step 1: Add the team action icon + Snackbar host**

In `PokemonDetailScreen.kt`:
- Imports:
```kotlin
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
```
- Collect team state + a snackbar host, near the existing `state`/`isFavorite`:
```kotlin
    val isInTeam by viewModel.isInTeam.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
```
- Add `snackbarHost` to the `Scaffold`:
```kotlin
        snackbarHost = { SnackbarHost(snackbarHostState) },
```
- Add a team action in the TopAppBar `actions` block, next to the favorite `IconButton`:
```kotlin
                    IconButton(onClick = viewModel::toggleTeam) {
                        Icon(
                            imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Outlined.Groups,
                            contentDescription = if (isInTeam) "Remove from team" else "Add to team",
                        )
                    }
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. (`Icons.Filled.Groups`/`Icons.Outlined.Groups` are in `material-icons-extended`, already a dependency.)

- [ ] **Step 3: Suggested commit**

```
feat(ui): add team action and snackbar to detail screen
```

---

## Task 6: Team screen + tab

**Files:** Create `ui/team/TeamViewModel.kt`, `ui/team/TeamScreen.kt`; modify `ui/navigation/Routes.kt`, `ui/navigation/PokedexNavHost.kt`

**Interfaces:**
- Produces: `TeamViewModel.team: StateFlow<List<PokemonListEntry>>`, `moveUp(id)`, `moveDown(id)`, `remove(id)`; `@Composable TeamScreen(onPokemonClick)`.

- [ ] **Step 1: ViewModel**

Create `ui/team/TeamViewModel.kt`:
```kotlin
package com.rogerparis.pokedex.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val repository: PokemonRepository,
) : ViewModel() {
    val team: StateFlow<List<PokemonListEntry>> =
        repository.observeTeam()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveUp(id: Int) = move(id, up = true)
    fun moveDown(id: Int) = move(id, up = false)
    fun remove(id: Int) {
        viewModelScope.launch { repository.removeFromTeam(id) }
    }

    private fun move(id: Int, up: Boolean) {
        viewModelScope.launch { repository.moveTeamMember(id, up) }
    }
}
```

- [ ] **Step 2: Screen**

Create `ui/team/TeamScreen.kt`:
```kotlin
package com.rogerparis.pokedex.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.ui.components.EmptyState

@Composable
fun TeamScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val team by viewModel.team.collectAsStateWithLifecycle()

    if (team.isEmpty()) {
        EmptyState("Your team is empty. Add up to 6 from a Pokémon's page.", modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items = team, key = { _, entry -> entry.id }) { index, entry ->
            TeamRow(
                entry = entry,
                isFirst = index == 0,
                isLast = index == team.lastIndex,
                onClick = onPokemonClick,
                onMoveUp = viewModel::moveUp,
                onMoveDown = viewModel::moveDown,
                onRemove = viewModel::remove,
            )
        }
    }
}

@Composable
private fun TeamRow(
    entry: PokemonListEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
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
        trailingContent = {
            androidx.compose.foundation.layout.Row {
                IconButton(onClick = { onMoveUp(entry.id) }, enabled = !isFirst) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMoveDown(entry.id) }, enabled = !isLast) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = { onRemove(entry.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove from team")
                }
            }
        },
    )
}
```

- [ ] **Step 3: Add the route**

In `ui/navigation/Routes.kt`, add:
```kotlin
@Serializable
data object TeamRoute
```

- [ ] **Step 4: Wire the tab + destination**

In `ui/navigation/PokedexNavHost.kt`:
- Import: `import com.rogerparis.pokedex.ui.team.TeamScreen`.
- Extend the enum:
```kotlin
private enum class TopDestination(val label: String) {
    LIST("Browse"),
    FAVORITES("Favorites"),
    TEAM("Team"),
}
```
- Add a nav item after Favorites:
```kotlin
            item(
                selected = currentRoute?.contains("TeamRoute") == true,
                onClick = { navController.navigate(TeamRoute) },
                icon = {},
                label = { Text(TopDestination.TEAM.label) },
            )
```
- Add the destination in the `NavHost`:
```kotlin
            composable<TeamRoute> {
                TeamScreen(onPokemonClick = { id -> navController.navigate(DetailRoute(id)) })
            }
```

- [ ] **Step 5: Build + device verification**

Run: `./gradlew installDebug`
The DB migrates **4 → 5** (favorites/cache/index preserved, `team_members` added).
- Open a Pokémon → tap the team icon (outline → filled). Add a few.
- Add a 7th → Snackbar "Team is full (max 6)."
- Open the **Team** tab → members listed in order; up/down reorder live; Close removes.
- Kill/reopen and Airplane mode → team persists and renders offline.

- [ ] **Step 6: Suggested commit**

```
feat(ui): add Team screen, tab, and reorder
```

---

## Self-Review

**Decision coverage:** separate `team_members` table + denormalized snapshot + `position` (Task 1) ✓; max-6 in repository (Task 3) ✓; up/down reorder transactionally (Task 3 `moveTeamMember`) ✓; detail toggle + one-shot full message (Tasks 4–5) ✓; Team tab (Task 6) ✓; `Migration(4,5)` + chain kept current (Task 1) ✓.

**Placeholder scan:** No TBD; full code + commands with expected output. Every breaking change to existing tests is updated explicitly: repo ctor (Task 3 Step 2), detail VM `@Before`/tests (Task 4 Step 1), migration-chain (Task 1 Step 7).

**Type consistency:** `TeamMemberEntity(id, name, artworkUrl, position)`, DAO `{upsert, upsertAll, remove, count, observeAll, getAllOnce, observeIsMember}`, repo `{observeTeam, isInTeam, addToTeam: Boolean, removeFromTeam, moveTeamMember(id, up)}`, VM `{isInTeam, userMessage, toggleTeam, consumeMessage}` / `{team, moveUp, moveDown, remove}`, `TeamRoute` — consistent across code and tests. ✓

## Notes / remaining
- Reorder uses up/down (not drag) by design — drag-reorder is a separate, gesture-heavy exercise.
- Remaining stretch: full Hilt-instrumented UI tests; `exportSchema = true` for `MigrationTestHelper`.
```
