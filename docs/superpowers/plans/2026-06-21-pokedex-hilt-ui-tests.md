# Pokédex — Hilt-Instrumented UI Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add end-to-end instrumented UI tests that boot the real Hilt graph + navigation on a device/emulator, with the repository replaced by a deterministic fake.

**Architecture:** Standard Hilt instrumented-test harness in `androidTest/`: a custom runner swaps `HiltTestApplication`; a `@TestInstallIn` module replaces `RepositoryModule` with a `FakePokemonRepository`; tests launch the real `MainActivity` (which hosts `PokedexNavHost`) via `createAndroidComposeRule` and assert UI + navigation. Spec stretch.

**Tech Stack:** Hilt testing (`hilt-android-testing`, `@TestInstallIn`, `HiltAndroidRule`), Compose UI test (`createAndroidComposeRule`), Espresso runner. Runs on a connected device/emulator.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Decisions (from grill-me)

1. Replace the repository with a `FakePokemonRepository` via Hilt `@TestInstallIn` — fast, deterministic, offline; no network/Room in these tests.
2. Scope = 2–3 focused end-to-end flows: (a) browse list renders + tap → detail navigates; (b) detail renders + favorite toggle reflects state; (c) empty-state path (Favorites tab with empty data).
3. Canonical `androidTest/` Hilt setup: custom `HiltTestRunner` + `HiltTestApplication`; launch the real `MainActivity`; needs an emulator (`connectedDebugAndroidTest`).

## Global Constraints

- Package root `com.rogerparis.pokedex`. AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17.
- New deps go on the **androidTest** configuration; Hilt's KSP compiler must also run for `androidTest` (`kspAndroidTest`).
- The fake repo lives in `androidTest` and replaces `RepositoryModule` via `@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])`.
- These tests require a booted emulator/device; they are NOT part of the JVM unit suite.
- **Claude never stages or commits.** Suggested commit per task; user commits.
- Comments: explain non-obvious *why* only.

## File Structure

```
gradle/libs.versions.toml                 # + hilt-android-testing alias (reuse hilt version)
app/build.gradle.kts                       # androidTest deps + kspAndroidTest; testInstrumentationRunner
app/src/androidTest/java/com/rogerparis/pokedex/
├── HiltTestRunner.kt                       # AndroidJUnitRunner -> HiltTestApplication
├── fake/FakePokemonRepository.kt           # in-memory PokemonRepository
├── di/TestRepositoryModule.kt              # @TestInstallIn replacing RepositoryModule
└── ui/PokedexUiTest.kt                     # the end-to-end flows
```

## Pre-req note

`MainActivity` is already `@AndroidEntryPoint` and hosts `PokedexNavHost()`. Launching it in a Hilt test exercises real navigation. No production code changes are required by this plan.

---

## Task 1: Test harness — deps, runner, runner config

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`; create `app/src/androidTest/java/com/rogerparis/pokedex/HiltTestRunner.kt`

- [ ] **Step 1: Catalog — add the Hilt testing artifact** (reuses the existing `hilt` version)

In `gradle/libs.versions.toml` under `[libraries]`:
```toml
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
```

- [ ] **Step 2: `app/build.gradle.kts` — androidTest deps + KSP + runner**

In `defaultConfig`, set the custom runner (replace the existing `testInstrumentationRunner` line):
```kotlin
        testInstrumentationRunner = "com.rogerparis.pokedex.HiltTestRunner"
```
In `dependencies { }` add:
```kotlin
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
```
(The `androidx.compose.ui.test.junit4`, `espresso`, and compose BOM are already on `androidTestImplementation` from the starter.)

- [ ] **Step 3: Custom test runner**

Create `app/src/androidTest/java/com/rogerparis/pokedex/HiltTestRunner.kt`:
```kotlin
package com.rogerparis.pokedex

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```
This makes instrumented tests run under `HiltTestApplication` (which hosts the test Hilt graph) instead of the production `PokedexApp`.

- [ ] **Step 4: Sync**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Suggested commit**

```
build: add Hilt instrumented-test harness (runner + testing deps)
```

---

## Task 2: Fake repository + Hilt test module

**Files:** Create `app/src/androidTest/java/com/rogerparis/pokedex/fake/FakePokemonRepository.kt`, `app/src/androidTest/java/com/rogerparis/pokedex/di/TestRepositoryModule.kt`

**Interfaces:**
- Produces: a `FakePokemonRepository` implementing `PokemonRepository` with controllable in-memory state; a test module replacing `RepositoryModule`.

- [ ] **Step 1: Fake repository**

Implements every `PokemonRepository` method with simple in-memory backing. Browse/search return a static `PagingData` page; detail returns a canned `Pokemon`; favorites/team are `MutableStateFlow`-backed so toggles reflect live.

Create `app/src/androidTest/java/com/rogerparis/pokedex/fake/FakePokemonRepository.kt`:
```kotlin
package com.rogerparis.pokedex.fake

import androidx.paging.PagingData
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.model.Stat
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePokemonRepository @Inject constructor() : PokemonRepository {

    val browseEntries = listOf(
        PokemonListEntry(1, "bulbasaur", "https://img/1.png"),
        PokemonListEntry(4, "charmander", "https://img/4.png"),
        PokemonListEntry(7, "squirtle", "https://img/7.png"),
    )

    private val favorites = MutableStateFlow<List<PokemonListEntry>>(emptyList())
    private val favoriteIds = MutableStateFlow<Set<Int>>(emptySet())
    private val team = MutableStateFlow<List<PokemonListEntry>>(emptyList())

    fun pokemon(id: Int) = Pokemon(
        id = id, name = browseEntries.first { it.id == id }.name,
        heightDm = 7, weightHg = 69, types = listOf("grass"),
        stats = listOf(Stat("hp", 45)), abilities = listOf("overgrow"),
        artworkUrl = "https://img/$id.png",
    )

    override fun pokemonPager(): Flow<PagingData<PokemonListEntry>> =
        flowOf(PagingData.from(browseEntries))

    override suspend fun getPokemon(id: Int): ApiResult<Pokemon> =
        ApiResult.Success(pokemon(id))

    override fun observeFavorites(): Flow<List<PokemonListEntry>> = favorites
    override fun isFavorite(id: Int): Flow<Boolean> = favoriteIds.map { id in it }
    override suspend fun addFavorite(pokemon: Pokemon) {
        favoriteIds.value = favoriteIds.value + pokemon.id
        favorites.value = favorites.value + PokemonListEntry(pokemon.id, pokemon.name, pokemon.artworkUrl)
    }
    override suspend fun removeFavorite(id: Int) {
        favoriteIds.value = favoriteIds.value - id
        favorites.value = favorites.value.filterNot { it.id == id }
    }

    override suspend fun ensureSearchIndex() = Unit
    override fun searchPager(query: String): Flow<PagingData<PokemonListEntry>> =
        flowOf(PagingData.from(browseEntries.filter { it.name.contains(query, ignoreCase = true) }))

    override fun observeTeam(): Flow<List<PokemonListEntry>> = team
    override fun isInTeam(id: Int): Flow<Boolean> = team.map { list -> list.any { it.id == id } }
    override suspend fun addToTeam(pokemon: Pokemon): Boolean {
        team.value = team.value + PokemonListEntry(pokemon.id, pokemon.name, pokemon.artworkUrl)
        return true
    }
    override suspend fun removeFromTeam(id: Int) {
        team.value = team.value.filterNot { it.id == id }
    }
    override suspend fun moveTeamMember(id: Int, up: Boolean) = Unit
}
```

- [ ] **Step 2: Test module replacing the real one**

Create `app/src/androidTest/java/com/rogerparis/pokedex/di/TestRepositoryModule.kt`:
```kotlin
package com.rogerparis.pokedex.di

import com.rogerparis.pokedex.domain.repository.PokemonRepository
import com.rogerparis.pokedex.fake.FakePokemonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFakeRepository(impl: FakePokemonRepository): PokemonRepository
}
```
`@TestInstallIn(replaces = [RepositoryModule::class])` makes Hilt drop the production binding and use the fake across the whole test app. (The production `NetworkModule`/`DatabaseModule` still load but go unused, since nothing resolves `PokeApi`/DAOs through the fake.)

- [ ] **Step 3: Compile the androidTest sources**

Run: `./gradlew assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL` (compiles the test APK; doesn't need a device). If the fake fails to compile, it's almost always a `PokemonRepository` signature drift — align the override to the interface.

- [ ] **Step 4: Suggested commit**

```
test: add FakePokemonRepository + Hilt @TestInstallIn module
```

---

## Task 3: The end-to-end UI tests

**Files:** Create `app/src/androidTest/java/com/rogerparis/pokedex/ui/PokedexUiTest.kt`

- [ ] **Step 1: Write the tests**

Create `app/src/androidTest/java/com/rogerparis/pokedex/ui/PokedexUiTest.kt`:
```kotlin
package com.rogerparis.pokedex.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rogerparis.pokedex.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PokedexUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun browseList_showsPokemon_andNavigatesToDetail() {
        // Browse list renders fake entries
        composeRule.onNodeWithText("Bulbasaur").assertIsDisplayed()

        // Tap navigates to detail (detail shows the same name in its app bar)
        composeRule.onNodeWithText("Charmander").performClick()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Charmander").assertIsDisplayed()
    }

    @Test
    fun detail_favoriteToggle_updatesContentDescription() {
        composeRule.onNodeWithText("Bulbasaur").performClick()

        // Starts not-favorited
        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        // After toggling, the action flips to the remove state
        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun favoritesTab_emptyState_isShown() {
        composeRule.onNodeWithText("Favorites").performClick()
        composeRule.onNodeWithText("No favorites yet. Tap the heart on a Pokémon.").assertIsDisplayed()
    }
}
```
Notes: the fake's `browseEntries` drive what's on screen; names are capitalized by the UI (`capitalize`). Toggling favorite flips the icon's `contentDescription` from "Add to favorites" → "Remove from favorites" — a stable, text-free way to assert the state change. The empty-state test relies on the fake starting with no favorites.

- [ ] **Step 2: Run on a connected device/emulator**

Boot an emulator, then:
Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rogerparis.pokedex.ui.PokedexUiTest`
Expected: `BUILD SUCCESSFUL`, 3 tests pass.
Troubleshooting:
- If Compose can't find a node, add `composeRule.waitForIdle()` after navigation, or assert with `onNodeWithText(..., useUnmergedTree = true)`.
- If two "Charmander" nodes exist transiently during navigation, scope the detail assertion to the app-bar title or use `waitUntil { onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }`.
- If the run can't find the runner, confirm `testInstrumentationRunner` points to `com.rogerparis.pokedex.HiltTestRunner` and re-run.

- [ ] **Step 3: Suggested commit**

```
test: add Hilt-instrumented UI tests for browse, detail, favorites
```

---

## Task 4: Confirm the JVM suite is unaffected

- [ ] **Step 1: Run the unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` — the new androidTest harness must not affect JVM unit tests (they run separately).

- [ ] **Step 2: Suggested commit (if anything needed adjusting)**

```
chore: verify unit suite unaffected by instrumented harness
```

---

## Self-Review

**Decision coverage:** fake repo via `@TestInstallIn` (Task 2) ✓; 2–3 focused flows — list→detail, favorite toggle, empty state (Task 3) ✓; canonical `androidTest` Hilt harness launching real `MainActivity` (Tasks 1, 3) ✓.

**Placeholder scan:** No TBD; full code + commands with expected output. Flaky-UI mitigations are given concretely (waitForIdle / unmerged tree / scoped assertions). No production code changes required (noted in pre-req).

**Type consistency:** `FakePokemonRepository` implements the current `PokemonRepository` surface exactly — `pokemonPager`, `getPokemon`, `observeFavorites`, `isFavorite`, `addFavorite`, `removeFavorite`, `ensureSearchIndex`, `searchPager`, `observeTeam`, `isInTeam`, `addToTeam`, `removeFromTeam`, `moveTeamMember`. `@TestInstallIn(replaces = [RepositoryModule::class])` matches the real module name. Runner FQCN matches `testInstrumentationRunner`. ✓

## Notes / remaining
- These run only on a device/emulator (`connectedDebugAndroidTest`), like the smoke test — keep them out of fast JVM CI stages or gate on an emulator.
- This was the last planned stretch item; remaining ideas (e.g. `exportSchema` + `MigrationTestHelper`, richer flows) are optional.
```
