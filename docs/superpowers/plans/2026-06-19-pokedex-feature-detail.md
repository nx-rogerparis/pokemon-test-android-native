# Pokédex Feature — Detail Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the detail stub with a real screen that fetches one Pokémon by id and renders loading / success / error states.

**Architecture:** Builds on the foundation + list plans. Introduces the canonical UDF ViewModel: a sealed `UiState` exposed as `StateFlow`, collected in Compose with `collectAsStateWithLifecycle()`. Spec chunk 5. Favorites + Room + polish (chunks 6–7) follow in a later plan.

**Tech Stack:** Compose + Material3, Hilt ViewModel + `SavedStateHandle`, Coroutines/StateFlow, Coil 3. Tests: JUnit + coroutines-test + Turbine + MockK.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Global Constraints

- Package root `com.rogerparis.pokedex`; layering `ui → domain → data`, inward only.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17. Plugin versions declared once at root (`apply false`).
- `getPokemon(id)` already returns `ApiResult<Pokemon>` (foundation). The detail ViewModel maps that to a sealed `DetailUiState`. No raw exceptions above the repository.
- The detail id comes from the type-safe `DetailRoute(id)` via `SavedStateHandle`.
- State down (immutable `StateFlow`), events up (lambdas). Collect with `collectAsStateWithLifecycle()`.
- **Claude never stages or commits.** Each task ends with a suggested commit; the user commits. Plain Conventional Commits messages.
- Comments: explain non-obvious *why* only. Compose memoization: hoist state, stable lambdas.

## File Structure

```
gradle/libs.versions.toml                                      # + turbine (test)
app/build.gradle.kts                                           # + turbine testImpl
app/src/main/java/com/rogerparis/pokedex/ui/detail/
├── DetailUiState.kt                                           # sealed Loading/Success/Error
├── PokemonDetailViewModel.kt                                  # StateFlow<DetailUiState>
└── PokemonDetailScreen.kt                                     # renders the state
app/src/main/java/com/rogerparis/pokedex/ui/navigation/PokedexNavHost.kt   # wire real screen
app/src/test/java/com/rogerparis/pokedex/
├── MainDispatcherRule.kt                                      # test rule for Dispatchers.Main
└── ui/detail/PokemonDetailViewModelTest.kt
```

---

## Task 1: Add Turbine test dependency

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Interfaces:**
- Produces: `app.cash.turbine.test` available in unit tests.

Turbine makes asserting on a `Flow`/`StateFlow`'s sequence of emissions easy — you `awaitItem()` each value instead of manually collecting into a list.

- [ ] **Step 1: Add to the catalog**

In `gradle/libs.versions.toml` under `[versions]`: `turbine = "1.2.0"`
Under `[libraries]`:
```toml
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

- [ ] **Step 2: Add the test dep in `app/build.gradle.kts`**

In `dependencies { }`, next to the other `testImplementation` lines:
```kotlin
testImplementation(libs.turbine)
```

- [ ] **Step 3: Sync (version gate)**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. If `turbine 1.2.0` doesn't resolve, bump to the nearest existing stable and re-run.

- [ ] **Step 4: Suggested commit (user runs)**

```
build: add Turbine for Flow testing
```

---

## Task 2: DetailUiState + ViewModel (TDD)

**Files:**
- Create: `ui/detail/DetailUiState.kt`, `ui/detail/PokemonDetailViewModel.kt`
- Create: `app/src/test/java/com/rogerparis/pokedex/MainDispatcherRule.kt`
- Test: `app/src/test/java/com/rogerparis/pokedex/ui/detail/PokemonDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `PokemonRepository.getPokemon(id)`, `DetailRoute(id)`, `ApiResult`, `AppError`, `Pokemon`.
- Produces:
  - `sealed interface DetailUiState { Loading; data class Success(pokemon: Pokemon); data class Error(error: AppError) }`
  - `PokemonDetailViewModel(savedStateHandle, repository)` exposing `state: StateFlow<DetailUiState>` and `fun retry()`.

- [ ] **Step 1: Write the UiState**

Create `app/src/main/java/com/rogerparis/pokedex/ui/detail/DetailUiState.kt`:
```kotlin
package com.rogerparis.pokedex.ui.detail

import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val pokemon: Pokemon) : DetailUiState
    data class Error(val error: AppError) : DetailUiState
}
```

- [ ] **Step 2: Write the MainDispatcherRule (test infra)**

`viewModelScope` runs on `Dispatchers.Main`, which doesn't exist in plain JVM tests. This JUnit rule swaps in a test dispatcher.

Create `app/src/test/java/com/rogerparis/pokedex/MainDispatcherRule.kt`:
```kotlin
package com.rogerparis.pokedex

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 3: Write the failing ViewModel test**

Create `app/src/test/java/com/rogerparis/pokedex/ui/detail/PokemonDetailViewModelTest.kt`:
```kotlin
package com.rogerparis.pokedex.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rogerparis.pokedex.MainDispatcherRule
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PokemonDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<PokemonRepository>()

    private fun pokemon() = Pokemon(
        id = 1, name = "bulbasaur", heightDm = 7, weightHg = 69,
        types = listOf("grass"), stats = emptyList(), abilities = listOf("overgrow"),
        artworkUrl = "url",
    )

    private fun viewModel() =
        PokemonDetailViewModel(SavedStateHandle(mapOf("id" to 1)), repository)

    @Test
    fun `emits Loading then Success`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())

        viewModel().state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            assertEquals(DetailUiState.Success(pokemon()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Loading then Error on failure`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Error(AppError.Network)

        viewModel().state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            assertEquals(DetailUiState.Error(AppError.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```
NOTE: this test reads the id via `SavedStateHandle(mapOf("id" to 1))` + `toRoute<DetailRoute>()`. If `toRoute` can't decode in a pure-JVM test (it relies on nav arg machinery), the run will throw at `viewModel()`. If that happens, add `@RunWith(org.robolectric.RobolectricTestRunner::class)` + the Robolectric test dep (already planned for favorites) — do NOT change the production code to work around a test-infra gap.

- [ ] **Step 4: Run the test, verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.ui.detail.PokemonDetailViewModelTest"`
Expected: FAIL — `PokemonDetailViewModel` unresolved.

- [ ] **Step 5: Write the ViewModel**

Create `app/src/main/java/com/rogerparis/pokedex/ui/detail/PokemonDetailViewModel.kt`:
```kotlin
package com.rogerparis.pokedex.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import com.rogerparis.pokedex.ui.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PokemonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PokemonRepository,
) : ViewModel() {

    private val pokemonId = savedStateHandle.toRoute<DetailRoute>().id

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = when (val result = repository.getPokemon(pokemonId)) {
                is ApiResult.Success -> DetailUiState.Success(result.data)
                is ApiResult.Error -> DetailUiState.Error(result.error)
            }
        }
    }
}
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.ui.detail.PokemonDetailViewModelTest"`
Expected: PASS (2 tests). If it throws on `toRoute` decoding, apply the Robolectric note from Step 3 and re-run.

- [ ] **Step 7: Suggested commit (user runs)**

```
feat(ui): add PokemonDetailViewModel with sealed DetailUiState
```

---

## Task 3: Detail screen (Compose) + wire into navigation

**Files:**
- Create: `ui/detail/PokemonDetailScreen.kt`
- Modify: `ui/navigation/PokedexNavHost.kt`

**Interfaces:**
- Consumes: `PokemonDetailViewModel`, `DetailUiState`, `Pokemon`, `AppError`.
- Produces: `@Composable fun PokemonDetailScreen(onBack: () -> Unit, viewModel: PokemonDetailViewModel = hiltViewModel())`.

- [ ] **Step 1: Write the detail screen**

Create `app/src/main/java/com/rogerparis/pokedex/ui/detail/PokemonDetailScreen.kt`:
```kotlin
package com.rogerparis.pokedex.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text((state as? DetailUiState.Success)?.pokemon?.name?.capitalize(Locale.current) ?: "Pokémon") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is DetailUiState.Loading -> CircularProgressIndicator()
                is DetailUiState.Error -> Text(s.error.toMessage())
                is DetailUiState.Success -> PokemonDetail(s.pokemon)
            }
        }
    }
}

@Composable
private fun PokemonDetail(pokemon: Pokemon) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = pokemon.artworkUrl,
            contentDescription = pokemon.name,
            modifier = Modifier.size(200.dp),
        )
        Text("Types: " + pokemon.types.joinToString { it.capitalize(Locale.current) })
        Text("Height: ${pokemon.heightDm / 10.0} m   Weight: ${pokemon.weightHg / 10.0} kg")
        Text("Abilities: " + pokemon.abilities.joinToString { it.capitalize(Locale.current) })
        pokemon.stats.forEach { stat ->
            Text("${stat.name.capitalize(Locale.current)}: ${stat.baseValue}")
        }
    }
}

private fun AppError.toMessage(): String = when (this) {
    AppError.Network -> "No connection. Check your network."
    AppError.NotFound -> "Pokémon not found."
    is AppError.Unknown -> "Something went wrong."
}
```
Note: `state` is read once into a local `s` inside `when` so Kotlin can smart-cast it to each subtype. `AppError.toMessage()` is the exhaustive mapping the sealed type forces — add a case later and this won't compile until handled.

- [ ] **Step 2: Replace the detail stub in the nav host**

In `ui/navigation/PokedexNavHost.kt`:
- Add import: `import com.rogerparis.pokedex.ui.detail.PokemonDetailScreen`
- Replace the `composable<DetailRoute> { ... }` body:
```kotlin
            composable<DetailRoute> {
                PokemonDetailScreen(onBack = { navController.popBackStack() })
            }
```
- Remove the now-unused `import androidx.navigation.toRoute` and the `Text` import if nothing else uses them (the compiler will flag unused imports; the Favorites stub still uses `Text`).

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. If `collectAsStateWithLifecycle` is unresolved, confirm `lifecycle-runtime-compose` is present (it was added in the list plan) — the import is `androidx.lifecycle.compose.collectAsStateWithLifecycle`.

- [ ] **Step 4: Run on device (manual verification)**

Run: `./gradlew installDebug`
Open the app, tap a Pokémon. Expected: a brief spinner, then the detail screen — large artwork, types, height/weight (converted to m/kg), abilities, and base stats. Back arrow returns to the list. Turn off the emulator's network and open a Pokémon to see the error message instead of a crash.

- [ ] **Step 5: Suggested commit (user runs)**

```
feat(ui): add PokemonDetailScreen wired into navigation
```

---

## Self-Review

**Spec coverage (chunk 5):**
- Detail screen: artwork, types, base stats, abilities, height/weight → Task 3. ✓
- Fetch by id via type-safe route + `SavedStateHandle` → Task 2. ✓
- Sealed `UiState` (Loading/Success/Error) exposed as `StateFlow`, collected with `collectAsStateWithLifecycle` → Tasks 2–3. ✓
- Error handling via typed `AppError` → exhaustive `toMessage()` → Task 3. ✓
- Out of scope (next plan): favorite toggle, Room, Favorites screen, polish, Compose UI test.

**Placeholder scan:** No TBD; all code complete; commands show expected output. The `toRoute`-in-JVM-test risk is called out with a concrete fallback (Robolectric), not left vague. ✓

**Type consistency:** `DetailUiState.{Loading,Success(pokemon),Error(error)}`, `PokemonDetailViewModel(savedStateHandle, repository)`, `state: StateFlow<DetailUiState>`, `retry()`, `PokemonDetailScreen(onBack, modifier, viewModel)` consistent across Interfaces, code, and tests. Reuses `getPokemon`, `Pokemon`, `AppError`, `DetailRoute` unchanged. ✓

## Notes carried to the next plan (chunks 6–7)
- Favorites: Room entity = denormalized snapshot of `Pokemon`; DAO; DB via KSP; `DatabaseModule`. Add a favorite toggle to this detail screen (observe `isFavorite` from the repository; toggle inserts/deletes). Favorites tab reads Room (offline). Robolectric DAO tests (and possibly retrofit the detail VM test onto Robolectric per Task 2's note).
- Add an instrumented Compose UI test covering list + detail.
- Polish: shared loading/empty/error components, type-colored chips, etc.
