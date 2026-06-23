# Pokédex Redesign — Static Visuals (Theme, Cards, Detail Hero)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reskin all screens into the playful, type-color-driven look — custom theme, the Type Card row, and the gradient detail hero with animated stat bars — without changing behavior.

**Architecture:** A shared `PokemonTypeColors` source feeds a custom Material3 theme (light+dark, dynamic color off), a reusable `PokemonCard` adopted by browse/favorites/team, and a redesigned detail screen (gradient hero + curved sheet + `StatBar`s). Spec chunks 1–3. Motion (shared-element transition, toggle pops) + component tests are a follow-up plan.

**Tech Stack:** Jetpack Compose + Material3, Coil 3. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-06-23-pokedex-redesign-design.md`

## Global Constraints

- Package root `com.rogerparis.pokedex`; presentation-only — no data/nav/ViewModel changes.
- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17.
- **Preserve exact strings the instrumented tests assert:** action `contentDescription`s `"Add to favorites"`/`"Remove from favorites"` and `"Add to team"`/`"Remove from team"`; empty-state copy `"No favorites yet. Tap the heart on a Pokémon."` and `"Your team is empty. Add up to 6 from a Pokémon's page."`.
- Keep `statusBarsPadding()` on browse/favorites/team; detail hero draws under the status bar by design.
- Memoize: hoist brushes with `remember(...)`; stable lambdas; no per-recomposition allocation in list rows.
- Verification is **build + on-device** (visual). Component tests are deferred to the follow-up plan.
- **Claude never stages or commits.** Suggested commit per task; user commits.
- Minimize comments; explain non-obvious *why* only.

## File Structure

```
ui/theme/PokemonTypeColors.kt   # NEW: typeColor(), gradientFor(), cardBrush() — single source of type color
ui/theme/Color.kt               # REPLACE palette
ui/theme/Theme.kt               # custom light/dark schemes; dynamicColor=false; Shapes
ui/theme/Type.kt                # heavier display weights
ui/components/PokemonTypeChip.kt# use typeColor() from the shared source
ui/components/PokemonCard.kt    # NEW: the Type Card row (browse/favorites/team)
ui/components/StatBar.kt        # NEW: animated type-gradient stat bar
ui/list/PokemonListScreen.kt    # use PokemonCard; restyle search field
ui/favorites/FavoritesScreen.kt # use PokemonCard
ui/team/TeamScreen.kt           # use PokemonCard + trailing controls
ui/detail/PokemonDetailScreen.kt# gradient hero + sheet + StatBars
```

---

## Task 1: Shared type-color source + chip refactor

**Files:**
- Create: `ui/theme/PokemonTypeColors.kt`
- Modify: `ui/components/PokemonTypeChip.kt`

**Interfaces:**
- Produces:
  - `fun typeColor(type: String): Color`
  - `fun cardBrush(primaryType: String, surface: Color): Brush` — horizontal type-tint → surface
  - `fun heroBrush(types: List<String>): Brush` — diagonal primary → secondary/darkened

- [ ] **Step 1: Create the shared color source**

Create `app/src/main/java/com/rogerparis/pokedex/ui/theme/PokemonTypeColors.kt`:
```kotlin
package com.rogerparis.pokedex.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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

fun typeColor(type: String): Color = typeColors[type] ?: fallbackTypeColor

/** Soft left→right tint used on list cards. Fades the type colour into the surface. */
fun cardBrush(primaryType: String, surface: Color): Brush =
    Brush.horizontalGradient(
        0f to lerp(surface, typeColor(primaryType), 0.38f),
        0.78f to surface,
        1f to surface,
    )

/** Diagonal hero gradient: primary type → second type (or a darkened primary). */
fun heroBrush(types: List<String>): Brush {
    val start = typeColor(types.firstOrNull() ?: "")
    val end = if (types.size > 1) typeColor(types[1]) else lerp(start, Color.Black, 0.40f)
    return Brush.linearGradient(listOf(start, end))
}
```

- [ ] **Step 2: Refactor the chip to use the shared source**

Replace `ui/components/PokemonTypeChip.kt` body (drop the local map):
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
import com.rogerparis.pokedex.ui.theme.typeColor

@Composable
fun PokemonTypeChip(type: String, modifier: Modifier = Modifier) {
    Text(
        text = type.capitalize(Locale.current),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(typeColor(type))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
```

- [ ] **Step 3: Build + full unit suite (proves the chip refactor didn't break component tests)**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (the existing `ComponentsUiTest` chip test still renders "Grass").

- [ ] **Step 4: Suggested commit**

```
refactor(ui): extract shared PokemonTypeColors (typeColor/cardBrush/heroBrush)
```

---

## Task 2: Custom theme — palette, dark mode, shapes, type

**Files:** Modify `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`

- [ ] **Step 1: Replace the palette**

Replace `ui/theme/Color.kt`:
```kotlin
package com.rogerparis.pokedex.ui.theme

import androidx.compose.ui.graphics.Color

// Light
val BrandRed = Color(0xFFE63950)
val BrandBlue = Color(0xFF2E6CF6)
val BrandYellow = Color(0xFFF7B500)
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F2F5)
val LightOnSurface = Color(0xFF1B1B1F)

// Dark
val BrandRedDark = Color(0xFFFF6B7E)
val BrandBlueDark = Color(0xFF8AB4FF)
val BrandYellowDark = Color(0xFFFFD45E)
val DarkBackground = Color(0xFF121316)
val DarkSurface = Color(0xFF1B1C20)
val DarkSurfaceVariant = Color(0xFF2A2B30)
val DarkOnSurface = Color(0xFFECECEE)
```

- [ ] **Step 2: Custom schemes + shapes; turn dynamic color off**

Replace `ui/theme/Theme.kt`:
```kotlin
package com.rogerparis.pokedex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    secondary = BrandBlue,
    onSecondary = Color.White,
    tertiary = BrandYellow,
    onTertiary = Color(0xFF1A1A1A),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF44464F),
)

private val DarkColors = darkColorScheme(
    primary = BrandRedDark,
    onPrimary = Color(0xFF2A000A),
    secondary = BrandBlueDark,
    onSecondary = Color(0xFF00214D),
    tertiary = BrandYellowDark,
    onTertiary = Color(0xFF2A2000),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC4C6CF),
)

private val PokedexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PokedexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = PokedexShapes,
        content = content,
    )
}
```
Note: `dynamicColor` is removed (spec: custom brand palette, not Material You). `MainActivity` calls `PokedexTheme { ... }` with no args — unaffected.

- [ ] **Step 3: Heavier display type**

In `ui/theme/Type.kt`, replace the `Typography` definition's relevant styles with heavier headline/title weights. Set the file's `Typography` to:
```kotlin
package com.rogerparis.pokedex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
        fontSize = 30.sp, lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
)
```

- [ ] **Step 4: Build + run on device**

Run: `./gradlew installDebug`
Expected: app builds; the nav, buttons, and surfaces now use the brand palette; toggle system dark mode to confirm both schemes read well. (Screens still use old `ListItem` rows — restyled next.)

- [ ] **Step 5: Suggested commit**

```
feat(ui): custom brand theme (light/dark), rounded shapes, bold type
```

---

## Task 3: PokemonCard + browse list

**Files:**
- Create: `ui/components/PokemonCard.kt`
- Modify: `ui/list/PokemonListScreen.kt`

**Interfaces:**
- Consumes: `typeColor`, `cardBrush`, `PokemonListEntry`, `PokemonTypeChip`.
- Produces: `@Composable fun PokemonCard(entry: PokemonListEntry, types: List<String>, onClick: (Int) -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null)`.

Note: list/favorites entries don't carry types (only id/name/artwork). v1 renders the card with **no type chips when `types` is empty** (the browse/favorites/team rows pass `emptyList()`); the chips appear on the detail screen where types are known. The card still tints by a neutral fallback when `types` is empty.

- [ ] **Step 1: Create the card**

Create `app/src/main/java/com/rogerparis/pokedex/ui/components/PokemonCard.kt`:
```kotlin
package com.rogerparis.pokedex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.ui.theme.cardBrush
import com.rogerparis.pokedex.ui.theme.typeColor

@Composable
fun PokemonCard(
    entry: PokemonListEntry,
    types: List<String>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val surface = MaterialTheme.colorScheme.surface
    val primaryType = types.firstOrNull() ?: ""
    val brush = remember(primaryType, surface) { cardBrush(primaryType, surface) }
    val accent = typeColor(primaryType)
    val nameColor = lerp(MaterialTheme.colorScheme.onSurface, accent, 0.30f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .clickable { onClick(entry.id) }
            .heightIn(min = 92.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#%03d".format(entry.id),
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
            )
            Text(
                text = entry.name.capitalize(Locale.current),
                color = nameColor,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (types.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    types.forEach { PokemonTypeChip(it) }
                }
            }
        }
        AsyncImage(
            model = entry.artworkUrl,
            contentDescription = entry.name,
            modifier = Modifier.size(84.dp),
        )
        if (trailing != null) trailing()
    }
}
```

- [ ] **Step 2: Use it in the browse list**

In `ui/list/PokemonListScreen.kt`, replace the private `PokemonRow` usage with `PokemonCard`, add list spacing, and restyle the search field. Specifically:
- Add import: `import com.rogerparis.pokedex.ui.components.PokemonCard` and `import androidx.compose.foundation.layout.Arrangement` and `import androidx.compose.foundation.shape.RoundedCornerShape`.
- In the `LazyColumn`, set `verticalArrangement = Arrangement.spacedBy(10.dp)` and `contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)` (import `androidx.compose.foundation.layout.PaddingValues`).
- Replace the item body `PokemonRow(entry = entry, onClick = onPokemonClick)` with:
```kotlin
                    PokemonCard(entry = entry, types = emptyList(), onClick = onPokemonClick)
```
- Delete the now-unused private `PokemonRow` composable.
- Restyle the search `OutlinedTextField`: give it `shape = RoundedCornerShape(16.dp)` and keep its existing value/onValueChange/placeholder/leadingIcon.

- [ ] **Step 3: Build + device**

Run: `./gradlew installDebug`
Expected: browse list shows the new Type Cards (tinted, #num, bold name, artwork right) with spacing; rounded search field; tapping still opens detail. Empty/error/loading still appear (they use theme colors, auto-restyled).

- [ ] **Step 4: Suggested commit**

```
feat(ui): add PokemonCard and adopt it in the browse list
```

---

## Task 4: Favorites + team adopt the card

**Files:** Modify `ui/favorites/FavoritesScreen.kt`, `ui/team/TeamScreen.kt`

- [ ] **Step 1: Favorites list uses the card**

In `ui/favorites/FavoritesScreen.kt`:
- Add imports: `import com.rogerparis.pokedex.ui.components.PokemonCard`, `import androidx.compose.foundation.layout.Arrangement`, `import androidx.compose.foundation.layout.PaddingValues`, `import androidx.compose.ui.unit.dp`.
- Set the `LazyColumn` `verticalArrangement = Arrangement.spacedBy(10.dp)`, `contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)`.
- Replace `FavoriteRow(entry = entry, onClick = onPokemonClick)` with:
```kotlin
            PokemonCard(entry = entry, types = emptyList(), onClick = onPokemonClick)
```
- Delete the now-unused private `FavoriteRow`. Keep the `EmptyState(...)` call and its exact string.

- [ ] **Step 2: Team list uses the card with trailing controls**

In `ui/team/TeamScreen.kt`, replace the private `TeamRow` with a `PokemonCard` that passes the up/down/remove buttons via the `trailing` slot:
- Add imports: `import com.rogerparis.pokedex.ui.components.PokemonCard`, `import androidx.compose.foundation.layout.Arrangement`, `import androidx.compose.foundation.layout.PaddingValues`.
- Set `LazyColumn` spacing/content padding as above.
- Replace the `itemsIndexed` body with:
```kotlin
        itemsIndexed(items = team, key = { _, entry -> entry.id }) { index, entry ->
            PokemonCard(
                entry = entry,
                types = emptyList(),
                onClick = onPokemonClick,
                trailing = {
                    Row {
                        IconButton(onClick = { viewModel.moveUp(entry.id) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { viewModel.moveDown(entry.id) }, enabled = index < team.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = { viewModel.remove(entry.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from team")
                        }
                    }
                },
            )
        }
```
- Delete the now-unused private `TeamRow`. Keep the existing icon/Row imports and the `EmptyState(...)` exact string.

- [ ] **Step 3: Build + device**

Run: `./gradlew installDebug`
Expected: Favorites and Team show Type Cards; Team rows keep the up/down/remove controls in the trailing slot and reorder/remove still work; empty states unchanged.

- [ ] **Step 4: Suggested commit**

```
feat(ui): adopt PokemonCard in favorites and team screens
```

---

## Task 5: StatBar + detail hero redesign

**Files:**
- Create: `ui/components/StatBar.kt`
- Modify: `ui/detail/PokemonDetailScreen.kt`

**Interfaces:**
- Consumes: `typeColor`, `heroBrush`, `Pokemon`, `Stat`, `PokemonTypeChip`, `LoadingState`/`ErrorState`.
- Produces: `@Composable fun StatBar(label: String, value: Int, accent: Color, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Create the animated stat bar**

Create `app/src/main/java/com/rogerparis/pokedex/ui/components/StatBar.kt`:
```kotlin
package com.rogerparis.pokedex.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val MAX_STAT = 255f

@Composable
fun StatBar(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    // Animate from 0 to the real fraction once, on first composition.
    var started by remember { mutableStateOf(false) }
    val target = if (started) (value / MAX_STAT).coerceIn(0f, 1f) else 0f
    val fraction by animateFloatAsState(targetValue = target, animationSpec = spring(), label = "stat")
    androidx.compose.runtime.LaunchedEffect(Unit) { started = true }

    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.Black, 0.4f)))),
            )
        }
        Text("$value", modifier = Modifier.width(32.dp).padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}
```

- [ ] **Step 2: Redesign the detail screen**

Replace `ui/detail/PokemonDetailScreen.kt` with the gradient hero + sheet. Full file:
```kotlin
package com.rogerparis.pokedex.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.ui.components.ErrorState
import com.rogerparis.pokedex.ui.components.LoadingState
import com.rogerparis.pokedex.ui.components.PokemonTypeChip
import com.rogerparis.pokedex.ui.components.StatBar
import com.rogerparis.pokedex.ui.theme.heroBrush
import com.rogerparis.pokedex.ui.theme.typeColor

@Composable
fun PokemonDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isInTeam by viewModel.isInTeam.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is DetailUiState.Loading -> LoadingState()
                is DetailUiState.Error -> ErrorState(s.error.toMessage(), onRetry = viewModel::retry)
                is DetailUiState.Success -> PokemonDetailContent(s.pokemon)
            }
            // Floating top actions over the hero.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Row {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = viewModel::toggleTeam) {
                        Icon(
                            imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Outlined.Groups,
                            contentDescription = if (isInTeam) "Remove from team" else "Add to team",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PokemonDetailContent(pokemon: Pokemon) {
    val accent = typeColor(pokemon.types.firstOrNull() ?: "")
    val brush = remember(pokemon.id) { heroBrush(pokemon.types) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(brush),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = pokemon.artworkUrl,
                contentDescription = pokemon.name,
                modifier = Modifier.size(200.dp).padding(top = 24.dp),
            )
        }
        // Sheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-24).dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                "#%03d".format(pokemon.id),
                color = lerp(MaterialTheme.colorScheme.onSurface, accent, 0.7f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                pokemon.name.capitalize(Locale.current),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                pokemon.types.forEach { PokemonTypeChip(it) }
            }
            Text(
                "Base stats",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            pokemon.stats.forEach { stat ->
                StatBar(label = stat.name.capitalize(Locale.current), value = stat.baseValue, accent = accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                StatPill(label = "Height", value = "${pokemon.heightDm / 10.0} m", modifier = Modifier.weight(1f))
                StatPill(label = "Weight", value = "${pokemon.weightHg / 10.0} kg", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun AppError.toMessage(): String = when (this) {
    AppError.Network -> "No connection. Check your network."
    AppError.NotFound -> "Pokémon not found."
    is AppError.Unknown -> "Something went wrong."
}
```
Note: the favorite/team `contentDescription` strings and `AppError.toMessage()` copy are preserved exactly (instrumented + behavior parity). The type chips now appear here (types are known on detail).

- [ ] **Step 3: Build + full unit suite + device**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all unit tests pass (detail VM tests unaffected — only the screen changed).
Run: `./gradlew installDebug`
Expected: detail shows the gradient hero with full artwork, curved sheet, type chips, animated stat bars filling on open, height/weight pills; back/favorite/team actions float white over the hero; favorite toggle + team full snackbar still work; offline shows the error message.

- [ ] **Step 4: Suggested commit**

```
feat(ui): redesign detail screen with gradient hero and animated stat bars
```

---

## Self-Review

**Spec coverage (chunks 1–3):**
- Custom palette light+dark, dynamic color off → Task 2 ✓
- Type colors promoted to shared source (`typeColor`/`cardBrush`/`heroBrush`) → Task 1 ✓
- Rounded shapes + bold type → Task 2 ✓
- Type Card (tint, #num saturated, dark type-hued name, chips, artwork) → Task 3 ✓
- Card adopted in browse/favorites/team (+ team trailing controls) → Tasks 3–4 ✓
- Restyled search field → Task 3 ✓
- State components auto-restyle via theme (no explicit work) → noted Task 3 ✓
- Detail gradient hero + glow... → hero gradient + full artwork + curved sheet + chips + animated StatBars + height/weight pills + floating white actions → Task 5 ✓ (the soft radial glow is omitted here to keep the hero a plain gradient; can add in the motion/polish plan — **noted as a deliberate deferral**, not a gap)
- Preserved test strings → constraints + Task 5 note ✓
- Out of scope (follow-up plan): shared-element transition, toggle spring-pops, list-item enter animation, `StatBar`/`PokemonCard` Robolectric component tests.

**Placeholder scan:** No TBD; full code in every code step; commands with expected output. The one design element trimmed (radial glow) is called out explicitly rather than left vague.

**Type consistency:** `typeColor(String)`, `cardBrush(String, Color)`, `heroBrush(List<String>)`, `PokemonCard(entry, types, onClick, modifier, trailing)`, `StatBar(label, value, accent, modifier)`, `StatPill(label, value, modifier)` consistent across definitions and call sites. `PokemonListEntry`/`Pokemon`/`Stat` fields used as they exist. Preserved `contentDescription`/empty-state/error strings match the instrumented tests verbatim. ✓

## Notes carried to the follow-up plan (chunks 4–5)
- Wrap `PokedexNavHost` in `SharedTransitionLayout`; share key `"art-$id"` between `PokemonCard` artwork and the detail hero artwork (thread `SharedTransitionScope` + `AnimatedContentScope`).
- Favorite/team toggle spring scale-pop + icon crossfade; list-item enter animation.
- Optional: re-add the hero radial glow; consider a custom display font.
- Add Robolectric Compose tests for `PokemonCard` (renders #num + name) and `StatBar` (renders label + value), mirroring `ComponentsUiTest`.
