# Pokédex Redesign — Motion & Component Tests

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the motion layer to the redesign — animated toggles, list-item enter, and the shared-element artwork transition (list → detail) — plus a `StatBar` component test.

**Architecture:** Confident, deterministic work first (component test, toggle pops, list-item animation); the shared-element transition last and isolated, scopes carried by a `CompositionLocal`, with a baked-in fallback if the morph misbehaves. Spec chunks 4–5.

**Tech Stack:** Compose animation (`AnimatedContent`, `animateItem`, `SharedTransitionLayout`), Robolectric Compose test. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-06-23-pokedex-redesign-design.md`

## Global Constraints

- Package root `com.rogerparis.pokedex`; presentation/motion only.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17.
- **Preserve exact strings the instrumented tests assert:** `"Add to favorites"`/`"Remove from favorites"`, `"Add to team"`/`"Remove from team"`. The animated icons must keep these `contentDescription`s.
- Shared-element transition is **eyeball-verified** (no deterministic gate) — expect a couple of visual-tuning cycles; that's why it's last and isolated.
- Verify the `SharedTransition` opt-in against the build (keep `@OptIn(ExperimentalSharedTransitionApi::class)`; if the compiler reports it unnecessary, drop it).
- Memoize: stable lambdas; brushes/keys hoisted.
- **Claude never stages or commits.** Suggested commit per task; user commits.

## File Structure

```
ui/components/StatBar.kt                 # (exists) — covered by new test
ui/detail/PokemonDetailScreen.kt         # animated favorite/team toggle icons
ui/list/PokemonListScreen.kt             # animateItem on cards
ui/favorites/FavoritesScreen.kt          # animateItem on cards
ui/team/TeamScreen.kt                    # animateItem on cards
ui/navigation/SharedElement.kt           # NEW: CompositionLocals + artwork-transition Modifier helper
ui/navigation/PokedexNavHost.kt          # wrap in SharedTransitionLayout; provide locals per destination
ui/components/PokemonCard.kt             # artwork applies the transition modifier
app/src/test/.../ui/components/StatBarTest.kt   # NEW
```

---

## Task 1: StatBar component test (Robolectric)

**Files:** Create `app/src/test/java/com/rogerparis/pokedex/ui/components/StatBarTest.kt`

Note: `PokemonCard` is already exercised end-to-end by the instrumented `PokedexUiTest` (renders real cards via the fake repo), so this plan adds only the `StatBar` unit — it has no image dependency, so it's fully deterministic under Robolectric.

- [ ] **Step 1: Write the test**

Create `app/src/test/java/com/rogerparis/pokedex/ui/components/StatBarTest.kt`:
```kotlin
package com.rogerparis.pokedex.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_label_and_value() {
        composeRule.setContent {
            StatBar(label = "Speed", value = 100, accent = Color(0xFFEE8130))
        }
        composeRule.onNodeWithText("Speed").assertIsDisplayed()
        composeRule.onNodeWithText("100").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew testDebugUnitTest --tests '*StatBarTest'`
Expected: PASS (the bar animates from 0, but the label/value text render immediately).

- [ ] **Step 3: Suggested commit**

```
test: add Robolectric component test for StatBar
```

---

## Task 2: Animated favorite/team toggle icons

**Files:** Modify `ui/detail/PokemonDetailScreen.kt`

Swap the two action icons with an `AnimatedContent` so toggling pops (scale + fade) between filled/outline. Preserve the exact `contentDescription`s.

- [ ] **Step 1: Add imports**

In `PokemonDetailScreen.kt` add:
```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
```

- [ ] **Step 2: Replace the favorite IconButton content**

Replace the favorite `IconButton { Icon(...) }` block with:
```kotlin
                    IconButton(onClick = viewModel::toggleFavorite) {
                        AnimatedContent(
                            targetState = isFavorite,
                            transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                            label = "favorite",
                        ) { fav ->
                            Icon(
                                imageVector = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
                                tint = Color.White,
                            )
                        }
                    }
```

- [ ] **Step 3: Replace the team IconButton content**

Replace the team `IconButton { Icon(...) }` block with:
```kotlin
                    IconButton(onClick = viewModel::toggleTeam) {
                        AnimatedContent(
                            targetState = isInTeam,
                            transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                            label = "team",
                        ) { inTeam ->
                            Icon(
                                imageVector = if (inTeam) Icons.Filled.Groups else Icons.Outlined.Groups,
                                contentDescription = if (inTeam) "Remove from team" else "Add to team",
                                tint = Color.White,
                            )
                        }
                    }
```

- [ ] **Step 4: Build + full unit suite + device**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (the instrumented favorite-toggle test still finds the `contentDescription`s — unaffected here, but the strings are preserved).
Run: `./gradlew installDebug`
Expected: tapping the heart/team icons pops with a scale+fade between states.

- [ ] **Step 5: Suggested commit**

```
feat(ui): animate favorite and team toggle icons
```

---

## Task 3: List-item enter/placement animation

**Files:** Modify `ui/list/PokemonListScreen.kt`, `ui/favorites/FavoritesScreen.kt`, `ui/team/TeamScreen.kt`

Add `Modifier.animateItem()` (the LazyItemScope API) to each card so items animate placement and fade in.

- [ ] **Step 1: Browse list**

In `PokemonListScreen.kt`, in the `items(...)` lambda, pass the modifier to the card:
```kotlin
                        EnrichedPokemonCard(
                            entry = entry,
                            onClick = onPokemonClick,
                            resolveType = viewModel::primaryTypeOf,
                            modifier = Modifier.animateItem(),
                        )
```
(`Modifier` is already imported.)

- [ ] **Step 2: Favorites list**

In `FavoritesScreen.kt`, in the `items(...)` lambda:
```kotlin
            EnrichedPokemonCard(
                entry = entry,
                onClick = onPokemonClick,
                resolveType = viewModel::primaryTypeOf,
                modifier = Modifier.animateItem(),
            )
```

- [ ] **Step 3: Team list**

In `TeamScreen.kt`, in the `itemsIndexed(...)` lambda, add the modifier (keep the `trailing` slot):
```kotlin
            EnrichedPokemonCard(
                entry = entry,
                onClick = onPokemonClick,
                resolveType = viewModel::primaryTypeOf,
                modifier = Modifier.animateItem(),
                trailing = { /* unchanged up/down/remove Row */ },
            )
```
(Leave the existing `trailing` content as-is; only add the `modifier` argument.)

- [ ] **Step 4: Build + device**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Run: `./gradlew installDebug`
Expected: removing a favorite/team member animates the list closing the gap; reordering the team animates the swap.

- [ ] **Step 5: Suggested commit**

```
feat(ui): animate list item placement on the three lists
```

---

## Task 4: Shared-element artwork transition (isolated, with fallback)

**Files:**
- Create: `ui/navigation/SharedElement.kt`
- Modify: `ui/navigation/PokedexNavHost.kt`, `ui/components/PokemonCard.kt`, `ui/detail/PokemonDetailScreen.kt`

**Interfaces:**
- Produces: `LocalSharedTransitionScope`, `LocalNavAnimatedVisibilityScope`, and `@Composable fun Modifier.pokemonArtworkTransition(id: Int): Modifier` (no-op when the locals are absent — so the card stays usable anywhere).

**Why a CompositionLocal:** the artwork lives deep inside the shared `PokemonCard`; threading scopes through its signature would pollute every call site. The local carries nullable scopes; the modifier no-ops when they're null.

- [ ] **Step 1: Create the locals + modifier helper**

Create `app/src/main/java/com/rogerparis/pokedex/ui/navigation/SharedElement.kt`:
```kotlin
package com.rogerparis.pokedex.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks the Pokémon artwork as a shared element keyed by id. No-op outside a SharedTransitionLayout. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.pokemonArtworkTransition(id: Int): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@pokemonArtworkTransition.sharedElement(
            rememberSharedContentState(key = "art-$id"),
            animatedVisibilityScope = animatedScope,
        )
    }
}
```
Note: if the build reports `ExperimentalSharedTransitionApi` is unnecessary (API stable in this BOM), remove the `@OptIn`. If `sharedElement`'s first parameter name differs, the doc signature is `sharedElement(sharedContentState, animatedVisibilityScope, ...)` — positional call as written is safe.

- [ ] **Step 2: Wrap the nav host + provide the scopes per destination**

In `PokedexNavHost.kt`:
- Add imports:
```kotlin
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
```
- Add `@OptIn(ExperimentalSharedTransitionApi::class)` to `PokedexNavHost`.
- Wrap the existing `NavigationSuiteScaffold(...) { NavHost(...) { ... } }` body in `SharedTransitionLayout { CompositionLocalProvider(LocalSharedTransitionScope provides this) { ... } }`. Concretely, the function body becomes:
```kotlin
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
            NavigationSuiteScaffold(
                navigationSuiteItems = { /* unchanged items */ },
            ) {
                NavHost(navController = navController, startDestination = ListRoute) {
                    composable<ListRoute> {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            PokemonListScreen(onPokemonClick = { id -> navController.navigate(DetailRoute(id)) })
                        }
                    }
                    composable<DetailRoute> {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            PokemonDetailScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable<FavoritesRoute> {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            FavoritesScreen(onPokemonClick = { id -> navController.navigate(DetailRoute(id)) })
                        }
                    }
                    composable<TeamRoute> {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            TeamScreen(onPokemonClick = { id -> navController.navigate(DetailRoute(id)) })
                        }
                    }
                }
            }
        }
    }
```
(Keep the existing `navigationSuiteItems` item blocks and `currentRoute` logic exactly as they are.)

- [ ] **Step 3: Apply the transition modifier to the card artwork**

In `ui/components/PokemonCard.kt`, change the `AsyncImage` modifier (apply the shared element *before* size, per the API ordering rule):
```kotlin
import com.rogerparis.pokedex.ui.navigation.pokemonArtworkTransition
```
```kotlin
        AsyncImage(
            model = entry.artworkUrl,
            contentDescription = entry.name,
            modifier = Modifier
                .pokemonArtworkTransition(entry.id)
                .size(84.dp),
        )
```

- [ ] **Step 4: Apply the transition modifier to the detail hero artwork**

In `ui/detail/PokemonDetailScreen.kt`, change the hero `AsyncImage` modifier:
```kotlin
import com.rogerparis.pokedex.ui.navigation.pokemonArtworkTransition
```
```kotlin
            AsyncImage(
                model = pokemon.artworkUrl,
                contentDescription = pokemon.name,
                modifier = Modifier
                    .pokemonArtworkTransition(pokemon.id)
                    .size(200.dp)
                    .padding(top = 24.dp),
            )
```

- [ ] **Step 5: Build + device (verify the morph)**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run: `./gradlew installDebug`
Expected: tapping a card's artwork morphs it into the detail hero artwork (and back on pop). Works from browse, favorites, and team (only the visible source list is composed, so `"art-$id"` resolves correctly).

- [ ] **Step 6: FALLBACK (only if the morph misbehaves)**

If the shared element distorts, flickers, or doesn't animate (a known risk with Paging + nav), do NOT keep fighting it. Replace it with a simple, reliable hero entrance and drop the card-side modifier:
- In `SharedElement.kt`, make `pokemonArtworkTransition` simply `return this` (disable the shared element), keeping the locals harmless.
- In `PokemonDetailContent`, animate the hero artwork in on first composition instead:
```kotlin
// add: import androidx.compose.animation.core.animateFloatAsState ; import androidx.compose.animation.core.spring
//      import androidx.compose.runtime.* ; import androidx.compose.ui.draw.scale
var shown by remember { mutableStateOf(false) }
val scale by animateFloatAsState(if (shown) 1f else 0.6f, spring(), label = "heroIn")
LaunchedEffect(Unit) { shown = true }
// AsyncImage(... modifier = Modifier.scale(scale).size(200.dp).padding(top = 24.dp))
```
This keeps a polished entrance without the cross-destination morph. Commit either the morph or the fallback — whichever looks right.

- [ ] **Step 7: Suggested commit**

```
feat(ui): shared-element artwork transition between list and detail
```
(or, if fallback used: `feat(ui): animate detail hero artwork entrance`)

---

## Self-Review

**Spec coverage (chunks 4–5):**
- Shared-element artwork transition (list → detail) → Task 4 (+ fallback) ✓
- Toggle spring-pops → Task 2 ✓
- List-item enter/placement animation → Task 3 ✓
- `StatBar` component test → Task 1 ✓ (`PokemonCard` covered by existing instrumented `PokedexUiTest` — noted)
- Stat-bar fill spring already shipped in `StatBar` (prior plan) ✓

**Placeholder scan:** No TBD; full code in every step; commands with expected output. The shared-element API opt-in uncertainty and the eyeball-verification nature are called out, and the fallback is concrete code (not a TODO).

**Type consistency:** `LocalSharedTransitionScope`, `LocalNavAnimatedVisibilityScope`, `Modifier.pokemonArtworkTransition(id)` consistent across the helper, NavHost wiring, card, and detail. Preserved `contentDescription` strings match the instrumented tests verbatim. `animateItem()` / `AnimatedContent` are current Compose APIs in the project's BOM.

## Notes
- This completes the redesign (chunks 1–5). No further planned work.
- If you later add a custom display font, it slots into `ui/theme/Type.kt` with a `res/font` family — independent of motion.
