# Pokédex Features — Smoke Test + List Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the stack works end-to-end against the live API (smoke test), then build the first real screen — a paginated, image-loaded Pokémon list with type-safe navigation and adaptive nav.

**Architecture:** Builds on the foundation (`docs/superpowers/plans/2026-06-18-pokedex-foundation.md`). Adds the `ui` layer (Compose + ViewModel + UDF) and network Paging 3. Spec chunk 4. Detail/favorites/polish (chunks 5–7) get a separate plan once this lands.

**Tech Stack:** Jetpack Compose, Paging 3 (network-only), Coil 3, Navigation Compose (type-safe routes), Hilt ViewModel injection, Coroutines/Flow.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Global Constraints

- Package root: `com.rogerparis.pokedex`. Layering: `ui → domain → data`, inward only.
- AGP 8.13.2, Gradle 8.13, KSP, Hilt, Java 17. All plugin versions declared at the root `build.gradle.kts` (`apply false`); modules apply version-less aliases.
- **Coil 3 needs `coil-network-okhttp`** or network URLs silently fail to load. Package: `coil3.compose.AsyncImage`.
- Paging is **network-only** (no Room/RemoteMediator in v1). Page size 20.
- Type-safe navigation: routes are `@Serializable` classes/objects (`List`, `Detail(id)`, `Favorites`). Uses the kotlin-serialization plugin already applied.
- State: ViewModel exposes the paging `Flow`; UI collects with `collectAsLazyPagingItems()`. No raw exceptions above the repository.
- **Claude never stages or commits.** Each task ends with a suggested commit; the user commits. Plain Conventional Commits messages.
- Comments: explain non-obvious *why* only.
- Compose memoization: hoist state, use `remember`, pass stable references; avoid allocating new lambdas/objects in hot recomposition paths (the Android analog of `useCallback`/`useMemo`).

## File Structure

```
gradle/libs.versions.toml                                       # + paging, coil, nav, lifecycle-compose, hilt-nav
app/build.gradle.kts                                            # + new deps
app/src/main/java/com/rogerparis/pokedex/
├── data/
│   ├── remote/PokemonPagingSource.kt                           # PagingSource<Int, PokemonListEntry>
│   └── repository/DefaultPokemonRepository.kt                  # + pokemonPager()
├── domain/repository/PokemonRepository.kt                     # + pokemonPager()
├── ui/
│   ├── list/PokemonListViewModel.kt                            # exposes Flow<PagingData<PokemonListEntry>>
│   ├── list/PokemonListScreen.kt                               # LazyColumn + paging footers + Coil
│   ├── navigation/Routes.kt                                    # @Serializable route types
│   └── navigation/PokedexNavHost.kt                            # NavigationSuiteScaffold + NavHost
└── MainActivity.kt                                             # host PokedexNavHost
app/src/test/java/com/rogerparis/pokedex/data/remote/PokemonPagingSourceTest.kt
app/src/androidTest/java/com/rogerparis/pokedex/SmokeTest.kt    # live-API instrumented test
```

---

## Task 1: Live-API smoke test (instrumented)

**Files:**
- Test: `app/src/androidTest/java/com/rogerparis/pokedex/SmokeTest.kt`

**Interfaces:**
- Consumes: `PokeApi`, DTOs (foundation). Builds a Retrofit inline (independent of Hilt) so the smoke test has no DI setup.
- Produces: confidence that Retrofit + kotlinx.serialization + DTOs work against the real PokéAPI. Throwaway once the app runs.

**Why instrumented:** it hits the real network on a device/emulator — the one thing unit tests deliberately don't do. Validates the whole remote path before we build UI on top of it.

- [ ] **Step 1: Write the smoke test**

Create `app/src/androidTest/java/com/rogerparis/pokedex/SmokeTest.kt`:
```kotlin
package com.rogerparis.pokedex

import com.rogerparis.pokedex.data.remote.PokeApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SmokeTest {
    private val api: PokeApi =
        Retrofit.Builder()
            .baseUrl("https://pokeapi.co/api/v2/")
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(PokeApi::class.java)

    @Test
    fun list_endpoint_returns_a_full_page() = runBlocking {
        val response = api.getPokemonList(limit = 5, offset = 0)
        assertEquals(5, response.results.size)
        assertTrue(response.count > 1000)
        assertTrue(response.results.first().name.isNotBlank())
    }

    @Test
    fun detail_endpoint_returns_bulbasaur_for_id_1() = runBlocking {
        val detail = api.getPokemonDetail(1)
        assertEquals("bulbasaur", detail.name)
        assertTrue(detail.types.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run on a connected device/emulator**

Start an emulator (Android Studio Device Manager) or plug in a device, then:
Run: `./gradlew connectedDebugAndroidTest --tests "com.rogerparis.pokedex.SmokeTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests pass. If it fails with no devices, boot an emulator first. If it fails on `asConverterFactory` import, that's the same converter-package note from the foundation — align the import.

- [ ] **Step 3: Suggested commit (user runs)**

```
test: add live-API smoke test for PokeApi (instrumented)
```

---

## Task 2: Add UI/Paging dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Interfaces:**
- Produces: Paging 3, Coil 3 (+ network), Navigation Compose, lifecycle-compose, hilt-navigation-compose on the classpath.

- [ ] **Step 1: Add versions + libraries to the catalog**

In `gradle/libs.versions.toml` under `[versions]`:
```toml
paging = "3.3.6"
coil = "3.4.0"
navigationCompose = "2.8.5"
lifecycleCompose = "2.8.7"
hiltNavigationCompose = "1.2.0"
```
Under `[libraries]`:
```toml
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleCompose" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
```

- [ ] **Step 2: Add the deps in `app/build.gradle.kts`**

In `dependencies { }`:
```kotlin
implementation(libs.androidx.paging.runtime)
implementation(libs.androidx.paging.compose)
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
implementation(libs.androidx.navigation.compose)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.hilt.navigation.compose)
```

- [ ] **Step 3: Sync to validate versions (version gate)**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. If a version fails to resolve, bump it to the nearest existing stable (these are all AndroidX/Coil libs with frequent releases) and re-run.

- [ ] **Step 4: Suggested commit (user runs)**

```
build: add Paging 3, Coil 3, Navigation Compose, lifecycle-compose, hilt-navigation-compose
```

---

## Task 3: PagingSource + repository pager (TDD)

**Files:**
- Create: `data/remote/PokemonPagingSource.kt`
- Modify: `domain/repository/PokemonRepository.kt`, `data/repository/DefaultPokemonRepository.kt`
- Test: `app/src/test/java/com/rogerparis/pokedex/data/remote/PokemonPagingSourceTest.kt`

**Interfaces:**
- Consumes: `PokeApi.getPokemonList(limit, offset)`, `PokemonListItemDto.toEntry()`.
- Produces:
  - `class PokemonPagingSource(api: PokeApi) : PagingSource<Int, PokemonListEntry>` — offset-keyed, page size 20.
  - `PokemonRepository.pokemonPager(): Flow<PagingData<PokemonListEntry>>`

- [ ] **Step 1: Write the failing PagingSource test**

Create `app/src/test/java/com/rogerparis/pokedex/data/remote/PokemonPagingSourceTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote

import androidx.paging.PagingSource
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PokemonPagingSourceTest {
    private val api = mockk<PokeApi>()

    private fun page(vararg ids: Int, next: String?) = PokemonListResponse(
        count = 1000,
        next = next,
        previous = null,
        results = ids.map { PokemonListItemDto(name = "p$it", url = "https://pokeapi.co/api/v2/pokemon/$it/") },
    )

    @Test
    fun `first load returns mapped entries and next key`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns
            page(1, 2, 3, next = "https://pokeapi.co/api/v2/pokemon?offset=20&limit=20")
        val source = PokemonPagingSource(api)

        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(1, 2, 3), page.data.map { it.id })
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png",
            page.data.first().artworkUrl,
        )
        assertEquals(null, page.prevKey)
        assertEquals(20, page.nextKey)
    }

    @Test
    fun `null next means no more pages`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns page(1, next = null)
        val source = PokemonPagingSource(api)

        val page = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(null, page.nextKey)
    }

    @Test
    fun `io error becomes LoadResult Error`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } throws IOException("offline")
        val source = PokemonPagingSource(api)

        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

        assertEquals(PagingSource.LoadResult.Error::class, result::class)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.PokemonPagingSourceTest"`
Expected: FAIL — `PokemonPagingSource` unresolved.

- [ ] **Step 3: Implement the PagingSource**

Create `app/src/main/java/com/rogerparis/pokedex/data/remote/PokemonPagingSource.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rogerparis.pokedex.data.mapper.toEntry
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import retrofit2.HttpException
import java.io.IOException

private const val PAGE_SIZE = 20

class PokemonPagingSource(
    private val api: PokeApi,
) : PagingSource<Int, PokemonListEntry>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PokemonListEntry> {
        val offset = params.key ?: 0
        return try {
            val response = api.getPokemonList(limit = PAGE_SIZE, offset = offset)
            LoadResult.Page(
                data = response.results.map { it.toEntry() },
                prevKey = if (offset == 0) null else offset - PAGE_SIZE,
                nextKey = if (response.next == null) null else offset + PAGE_SIZE,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, PokemonListEntry>): Int? =
        state.anchorPosition?.let { anchor ->
            val closest = state.closestPageToPosition(anchor)
            closest?.prevKey?.plus(PAGE_SIZE) ?: closest?.nextKey?.minus(PAGE_SIZE)
        }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.PokemonPagingSourceTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Add `pokemonPager()` to the repository interface**

In `domain/repository/PokemonRepository.kt` add the import and method:
```kotlin
import androidx.paging.PagingData
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import kotlinx.coroutines.flow.Flow
```
```kotlin
interface PokemonRepository {
    fun pokemonPager(): Flow<PagingData<PokemonListEntry>>
    suspend fun getPokemon(id: Int): ApiResult<Pokemon>
}
```

- [ ] **Step 6: Implement `pokemonPager()` in `DefaultPokemonRepository`**

Add imports and the method:
```kotlin
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.rogerparis.pokedex.data.remote.PokemonPagingSource
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import kotlinx.coroutines.flow.Flow
```
```kotlin
    override fun pokemonPager(): Flow<PagingData<PokemonListEntry>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { PokemonPagingSource(api) },
        ).flow
```

- [ ] **Step 7: Build + run the full unit suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Suggested commit (user runs)**

```
feat(data): add network PokemonPagingSource + repository pager
```

---

## Task 4: List ViewModel

**Files:**
- Create: `ui/list/PokemonListViewModel.kt`

**Interfaces:**
- Consumes: `PokemonRepository.pokemonPager()`.
- Produces: `PokemonListViewModel.pokemon: Flow<PagingData<PokemonListEntry>>` (cached in viewModelScope).

The ViewModel is thin — it exposes the repository's paging flow, `cachedIn` so paged data survives configuration changes/recomposition. (Paging flows aren't meaningfully unit-tested at this layer; the logic lives in the tested PagingSource. UI behavior is covered by an instrumented Compose test in a later plan.)

- [ ] **Step 1: Write the ViewModel**

Create `app/src/main/java/com/rogerparis/pokedex/ui/list/PokemonListViewModel.kt`:
```kotlin
package com.rogerparis.pokedex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class PokemonListViewModel @Inject constructor(
    repository: PokemonRepository,
) : ViewModel() {
    val pokemon: Flow<PagingData<PokemonListEntry>> =
        repository.pokemonPager().cachedIn(viewModelScope)
}
```

- [ ] **Step 2: Build to verify Hilt can construct the ViewModel**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (`@HiltViewModel` + `@Inject` let Hilt build it; `hilt-navigation-compose` supplies it to Compose later).

- [ ] **Step 3: Suggested commit (user runs)**

```
feat(ui): add PokemonListViewModel exposing paged entries
```

---

## Task 5: List screen (Compose + Coil + paging footers)

**Files:**
- Create: `ui/list/PokemonListScreen.kt`

**Interfaces:**
- Consumes: `PokemonListViewModel`, `PokemonListEntry`.
- Produces: `@Composable fun PokemonListScreen(onPokemonClick: (Int) -> Unit, viewModel: PokemonListViewModel = hiltViewModel())`.

- [ ] **Step 1: Write the list screen**

Create `app/src/main/java/com/rogerparis/pokedex/ui/list/PokemonListScreen.kt`:
```kotlin
package com.rogerparis.pokedex.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.model.PokemonListEntry

@Composable
fun PokemonListScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonListViewModel = hiltViewModel(),
) {
    val items = viewModel.pokemon.collectAsLazyPagingItems()

    LazyColumn(modifier = modifier.fillMaxSize()) {
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

@Composable
private fun PokemonRow(entry: PokemonListEntry, onClick: (Int) -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onClick(entry.id) },
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

@Composable
private fun LoadingFooter() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorFooter(onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Couldn't load more. Tap to retry.",
            modifier = Modifier.clickable { onRetry() },
        )
    }
}
```
Note: `items[index]` is the Paging accessor; `?: return@items` skips placeholder nulls. `itemKey { it.id }` gives stable keys (the Compose analog of React's `key` prop — prevents needless recomposition).

- [ ] **Step 2: Build to verify the screen compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. If `capitalize`/`itemKey` imports fail to resolve, they're minor API-location differences — paste the error and adjust the import.

- [ ] **Step 3: Suggested commit (user runs)**

```
feat(ui): add PokemonListScreen with Coil images and paging footers
```

---

## Task 6: Type-safe navigation + adaptive nav scaffold

**Files:**
- Create: `ui/navigation/Routes.kt`, `ui/navigation/PokedexNavHost.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Consumes: `PokemonListScreen`.
- Produces: `@Composable fun PokedexNavHost()` hosting List + Favorites tabs (adaptive) with a Detail destination. Detail/Favorites are stubs filled in by the next plan.

- [ ] **Step 1: Define type-safe routes**

Create `app/src/main/java/com/rogerparis/pokedex/ui/navigation/Routes.kt`:
```kotlin
package com.rogerparis.pokedex.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Serializable
data class DetailRoute(val id: Int)

@Serializable
data object FavoritesRoute
```

- [ ] **Step 2: Build the nav host + adaptive scaffold**

Create `app/src/main/java/com/rogerparis/pokedex/ui/navigation/PokedexNavHost.kt`:
```kotlin
package com.rogerparis.pokedex.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rogerparis.pokedex.ui.list.PokemonListScreen

private enum class TopDestination(val label: String) {
    LIST("Browse"),
    FAVORITES("Favorites"),
}

@Composable
fun PokedexNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = currentRoute?.contains("ListRoute") == true,
                onClick = { navController.navigate(ListRoute) },
                icon = {},
                label = { Text(TopDestination.LIST.label) },
            )
            item(
                selected = currentRoute?.contains("FavoritesRoute") == true,
                onClick = { navController.navigate(FavoritesRoute) },
                icon = {},
                label = { Text(TopDestination.FAVORITES.label) },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = ListRoute) {
            composable<ListRoute> {
                PokemonListScreen(onPokemonClick = { id -> navController.navigate(DetailRoute(id)) })
            }
            composable<DetailRoute> { entry ->
                val detail = entry.toRoute<DetailRoute>()
                Text("Detail for #${detail.id} — coming in the next plan")
            }
            composable<FavoritesRoute> {
                Text("Favorites — coming in the next plan")
            }
        }
    }
}
```
Note: `composable<ListRoute> { }` is type-safe navigation — the route type *is* the destination. `entry.toRoute<DetailRoute>()` extracts the typed args (no string parsing). Detail/Favorites are intentional stubs.

- [ ] **Step 3: Host the nav graph in `MainActivity`**

Replace the body of `setContent { PokedexTheme { ... } }` so it calls `PokedexNavHost()` instead of the starter scaffold. In `MainActivity.kt`:
- Remove the `PokedexRoot`, `Greeting`, `AppDestinations`, and the old `NavigationSuiteScaffold`/`Scaffold` sample code.
- Keep `onCreate`/`enableEdgeToEdge`. Set content to:
```kotlin
setContent {
    PokedexTheme {
        PokedexNavHost()
    }
}
```
- Add import: `import com.rogerparis.pokedex.ui.navigation.PokedexNavHost`
- Remove now-unused imports (the compiler will flag them).

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run the app on a device/emulator (manual verification)**

Install and launch:
Run: `./gradlew installDebug`
Then open the app. Expected: a scrolling list of Pokémon with names + artwork; scrolling to the bottom loads more (a brief spinner footer); tapping a row shows the "Detail for #N" stub; the Browse/Favorites nav toggles destinations.

- [ ] **Step 6: Suggested commit (user runs)**

```
feat(ui): add type-safe navigation + adaptive nav scaffold hosting the list
```

---

## Self-Review

**Spec coverage (chunk 4 + smoke):**
- Smoke test (end-to-end live API) → Task 1. ✓
- Network Paging 3, page size 20 → Task 3 (PagingSource) + Task 4 (cachedIn). ✓
- List screen: sprite (Coil) + name, tap → detail → Task 5 + Task 6 nav. ✓
- Type-safe routes (`List`, `Detail(id)`, `Favorites`) → Task 6. ✓
- Adaptive navigation suite (Browse/Favorites tabs, Detail pushed) → Task 6. ✓
- List sprite derivation reused from foundation mapper (`toEntry`) → Task 3. ✓
- Out of scope (next plan): Detail screen body, Favorites + Room, offline, Compose UI tests, RemoteMediator.

**Placeholder scan:** Detail/Favorites screens are deliberate, labeled stubs (navigable, not "TODO"). All code steps show full code; commands show expected output. No TBD. ✓

**Type consistency:** `PokemonPagingSource(api)`, `pokemonPager(): Flow<PagingData<PokemonListEntry>>`, `PokemonListViewModel.pokemon`, `PokemonListScreen(onPokemonClick, modifier, viewModel)`, routes `ListRoute`/`DetailRoute(id)`/`FavoritesRoute` — consistent across Interfaces blocks, code, and tests. `toEntry()` reused from foundation. ✓

## Notes carried to the next plan (chunks 5–7)
- Detail screen: `getPokemon(id)` already returns `ApiResult<Pokemon>`; build `PokemonDetailViewModel` with a sealed `UiState` (Loading/Success/Error) + `collectAsStateWithLifecycle()`.
- Favorites: Room entity (denormalized snapshot of `Pokemon`), DAO, DB via KSP; favorite toggle on detail; Robolectric DAO tests; Favorites tab reads from Room (offline).
- Replace the Favorites/Detail stubs with real screens.
- Add instrumented Compose UI test for the list (renders items, click emits id).
