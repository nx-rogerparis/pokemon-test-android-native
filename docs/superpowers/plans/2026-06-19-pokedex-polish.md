# Pokédex — Polish: Shared State Components, Type Chips, Compose UI Tests

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove duplicated loading/error/empty UI into shared composables, add authentic type-colored chips to the detail screen, and add the first Compose UI tests (Robolectric).

**Architecture:** Extract `ui/components/` stateless composables reused across list/detail/favorites; a reusable `PokemonTypeChip`; Robolectric-based Compose UI tests of those stateless composables. Spec chunk 7.

**Tech Stack:** Compose + Material3, Robolectric + Compose UI test (`createComposeRule`).

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Decisions (from grill-me)

1. All three polish items, in order: shared components → chips → UI test.
2. Compose UI tests run under Robolectric (JVM), testing **stateless** composables (pass props, assert tree) — no emulator, no Hilt test infra.
3. Type chips use a hardcoded `type → Color` map with a fallback, wrapped in a reusable `PokemonTypeChip`.

## Global Constraints

- Package root `com.rogerparis.pokedex`; layering `ui → domain → data`.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17.
- Stateless, hoisted composables (so they're trivially testable). Stable lambdas.
- **Claude never stages or commits.** Suggested commit per task; user commits.
- Comments: explain non-obvious *why* only.

## File Structure

```
app/src/main/java/com/rogerparis/pokedex/ui/components/
├── StateViews.kt            # LoadingState, ErrorState, EmptyState
└── PokemonTypeChip.kt       # type -> Color map + chip composable
app/src/main/java/com/rogerparis/pokedex/ui/
├── list/PokemonListScreen.kt        # use LoadingState/ErrorState
├── detail/PokemonDetailScreen.kt    # use LoadingState/ErrorState(retry) + type chips
└── favorites/FavoritesScreen.kt     # use EmptyState
app/build.gradle.kts                 # + compose ui-test on testImplementation
app/src/test/java/com/rogerparis/pokedex/ui/components/ComponentsUiTest.kt
```

---

## Task 1: Shared state composables + refactor

**Files:** Create `ui/components/StateViews.kt`; modify `ui/list/PokemonListScreen.kt`, `ui/detail/PokemonDetailScreen.kt`, `ui/favorites/FavoritesScreen.kt`

**Interfaces:**
- Produces: `LoadingState(modifier)`, `ErrorState(message: String, onRetry: () -> Unit, modifier)`, `EmptyState(message: String, modifier)`.

- [ ] **Step 1: Create the shared composables**

Create `app/src/main/java/com/rogerparis/pokedex/ui/components/StateViews.kt`:
```kotlin
package com.rogerparis.pokedex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message)
    }
}
```

- [ ] **Step 2: Refactor the list screen**

In `ui/list/PokemonListScreen.kt`:
- Add import: `import com.rogerparis.pokedex.ui.components.ErrorState` and `import com.rogerparis.pokedex.ui.components.LoadingState`.
- Replace the `when (val refresh = items.loadState.refresh)` branches:
```kotlin
    when (items.loadState.refresh) {
        is LoadState.Loading -> LoadingState(modifier)
        is LoadState.Error -> ErrorState(
            message = "Couldn't load Pokémon. Check your connection.",
            onRetry = items::retry,
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
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
```
- Delete the now-unused private `FullScreenCenter` and `ErrorMessage` composables and the `Arrangement` import if it's no longer used (keep `LoadingFooter`/`ErrorFooter`). The compiler will flag unused imports.

- [ ] **Step 3: Refactor the favorites screen**

In `ui/favorites/FavoritesScreen.kt`:
- Add import: `import com.rogerparis.pokedex.ui.components.EmptyState`.
- Replace the empty branch:
```kotlin
    if (favorites.isEmpty()) {
        EmptyState("No favorites yet. Tap the heart on a Pokémon.", modifier)
        return
    }
```
- Remove the now-unused `Box`/`Alignment` imports if nothing else uses them.

- [ ] **Step 4: Refactor the detail screen (and wire `retry()`)**

In `ui/detail/PokemonDetailScreen.kt`:
- Add imports: `import com.rogerparis.pokedex.ui.components.ErrorState` and `import com.rogerparis.pokedex.ui.components.LoadingState`.
- Replace the `when (val s = state)` branches inside the `Box`:
```kotlin
            when (val s = state) {
                is DetailUiState.Loading -> LoadingState()
                is DetailUiState.Error -> ErrorState(s.error.toMessage(), onRetry = viewModel::retry)
                is DetailUiState.Success -> PokemonDetail(s.pokemon)
            }
```
This wires the ViewModel's `retry()` (previously unused) to the error UI.

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Fix any unused-import warnings the refactor leaves.

- [ ] **Step 6: Suggested commit**

```
refactor(ui): extract shared LoadingState/ErrorState/EmptyState components
```

---

## Task 2: Type-colored chips on detail

**Files:** Create `ui/components/PokemonTypeChip.kt`; modify `ui/detail/PokemonDetailScreen.kt`

**Interfaces:**
- Produces: `PokemonTypeChip(type: String, modifier)`.

- [ ] **Step 1: Create the chip + color map**

Create `app/src/main/java/com/rogerparis/pokedex/ui/components/PokemonTypeChip.kt`:
```kotlin
package com.rogerparis.pokedex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp

private val typeColors: Map<String, Color> = mapOf(
    "normal" to Color(0xFFA8A77A),
    "fire" to Color(0xFFEE8130),
    "water" to Color(0xFF6390F0),
    "electric" to Color(0xFFF7D02C),
    "grass" to Color(0xFF7AC74C),
    "ice" to Color(0xFF96D9D6),
    "fighting" to Color(0xFFC22E28),
    "poison" to Color(0xFFA33EA1),
    "ground" to Color(0xFFE2BF65),
    "flying" to Color(0xFFA98FF3),
    "psychic" to Color(0xFFF95587),
    "bug" to Color(0xFFA6B91A),
    "rock" to Color(0xFFB6A136),
    "ghost" to Color(0xFF735797),
    "dragon" to Color(0xFF6F35FC),
    "dark" to Color(0xFF705746),
    "steel" to Color(0xFFB7B7CE),
    "fairy" to Color(0xFFD685AD),
)

private val fallbackTypeColor = Color(0xFF777777)

@Composable
fun PokemonTypeChip(type: String, modifier: Modifier = Modifier) {
    Text(
        text = type.capitalize(Locale.current),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(typeColors[type] ?: fallbackTypeColor)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
```

- [ ] **Step 2: Use chips in the detail screen**

In `ui/detail/PokemonDetailScreen.kt`, inside `PokemonDetail`, replace the `Text("Types: " + ...)` line with a row of chips:
- Add imports:
```kotlin
import androidx.compose.foundation.layout.Row
import com.rogerparis.pokedex.ui.components.PokemonTypeChip
```
- Replace:
```kotlin
        Text("Types: " + pokemon.types.joinToString { it.capitalize(Locale.current) })
```
with:
```kotlin
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pokemon.types.forEach { type -> PokemonTypeChip(type) }
        }
```
(`Arrangement` and `Locale` are already imported in this file.)

- [ ] **Step 3: Build + device**

Run: `./gradlew installDebug`
Open a Pokémon → its types now show as colored chips (e.g., Bulbasaur: green "Grass" + purple "Poison").

- [ ] **Step 4: Suggested commit**

```
feat(ui): add type-colored chips to detail screen
```

---

## Task 3: Add Compose UI test dependency

**Files:** Modify `app/build.gradle.kts`

Compose UI tests need the test artifact on the **unit** test classpath (we already have it on `androidTestImplementation`; add it to `testImplementation` for Robolectric).

- [ ] **Step 1: Add the deps**

In `app/build.gradle.kts` `dependencies { }`:
```kotlin
testImplementation(platform(libs.androidx.compose.bom))
testImplementation(libs.androidx.compose.ui.test.junit4)
```
(The catalog already defines `androidx-compose-bom` and `androidx-compose-ui-test-junit4`.)

- [ ] **Step 2: Sync**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Suggested commit**

```
build: add Compose UI test on the unit (Robolectric) classpath
```

---

## Task 4: Compose UI tests (Robolectric)

**Files:** Create `app/src/test/java/com/rogerparis/pokedex/ui/components/ComponentsUiTest.kt`

- [ ] **Step 1: Write the tests**

Create `ComponentsUiTest.kt`:
```kotlin
package com.rogerparis.pokedex.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComponentsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typeChip_displays_capitalized_type() {
        composeRule.setContent { PokemonTypeChip("grass") }
        composeRule.onNodeWithText("Grass").assertIsDisplayed()
    }

    @Test
    fun errorState_shows_message_and_retry_invokes_callback() {
        var retried = 0
        composeRule.setContent {
            ErrorState(message = "Couldn't load", onRetry = { retried++ })
        }
        composeRule.onNodeWithText("Couldn't load").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.ui.components.ComponentsUiTest"`
Expected: PASS (2 tests). This is the first Compose UI test — it renders a composable into a virtual tree, finds nodes by their text, asserts visibility, and simulates a click.
NOTE: if Robolectric + Compose throws a config error (e.g., looking for a graphics/native lib), add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (`import org.robolectric.annotation.GraphicsMode`) to the class and re-run; if it still can't initialize, fall back to running these as instrumented tests under `androidTest/` with `createComposeRule()` (no code change beyond the runner) — paste the error and we'll decide.

- [ ] **Step 3: Full suite**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Suggested commit**

```
test: add Robolectric Compose UI tests for type chip and error state
```

---

## Self-Review

**Decision coverage:** shared components + refactor (Task 1) ✓; type chips with hardcoded map + fallback (Task 2) ✓; Robolectric Compose UI tests of stateless composables (Tasks 3–4) ✓. Bonus: detail error now wires `retry()` (Task 1 Step 4). ✓

**Placeholder scan:** No TBD; full code + commands with expected output. The Robolectric-Compose config risk is called out with a concrete fallback. ✓

**Type consistency:** `LoadingState(modifier)`, `ErrorState(message, onRetry, modifier)`, `EmptyState(message, modifier)`, `PokemonTypeChip(type, modifier)` used identically in the components, the three refactored screens, and the UI test. ✓

## Notes / remaining
- Stretch still open: RemoteMediator offline browse, search, team builder, full Hilt-instrumented UI tests, `exportSchema` for future migrations.
