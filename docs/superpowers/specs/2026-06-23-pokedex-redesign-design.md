# Pokédex — Playful Visual Redesign Design Spec

**Date:** 2026-06-23
**Status:** Design approved (visual companion), pending writing-plans.
**Author:** Roger Paris (with Claude)

## Purpose

Reskin the working Pokédex from generic Material3 defaults into a distinctive,
**playful & energetic**, type-color-driven design across all screens — without
changing app behavior, navigation, or the data layer. This is a UI/theming +
motion effort. Mockups were validated in the visual companion (browse list card
+ detail hero).

## Direction (locked via brainstorming)

- **Aesthetic:** playful & energetic; leans into the Pokémon brand.
- **Scope:** full redesign, all screens (browse, detail, favorites, team, states).
- **Color:** custom vibrant brand palette for **light and dark**, with each
  Pokémon's **primary type color** dynamically accenting its card/detail.
- **Motion:** rich — shared-element artwork transition (list → detail), spring
  stat bars, animated favorite/team toggles, screen + list-item enter animations.

## Non-goals

- No change to architecture, repositories, ViewModels' data, navigation graph,
  or persistence. Behavior is identical; only presentation + motion change.
- No new screens or features.
- Existing tests stay green; redesign must not break unit/instrumented suites.

## Design system

### Color
- A custom `lightColorScheme`/`darkColorScheme` (vibrant, warm-neutral surfaces;
  a saturated brand primary used for the nav + accents) replacing the starter's
  default scheme. Defined in `ui/theme/Color.kt` + `Theme.kt`.
- **Type colors** stay the source of per-Pokémon accent (existing 18-type map in
  `PokemonTypeChip`), promoted to a shared `ui/theme/PokemonTypeColors.kt` so
  cards, the detail hero gradient, and stat bars all derive from
  `primaryType -> Color`. A fallback color covers unknown types.
- Helper: `typeColor(type: String): Color` and a `gradientFor(types): Brush`
  (primary type → darker/secondary blend) used by hero + cards.

### Shape & type
- Rounded, friendly shapes: cards/sheets ~20–26.dp corners (Material3 `Shapes`
  tuned larger). Pills (chips, stat tracks) fully rounded.
- Expressive headings: heavy weights for names/numbers; keep the system font
  family (no custom font dependency in v1 — revisit later). Tuned `Typography`
  in `ui/theme/Type.kt`.

### Reusable components (`ui/components/`)
- `PokemonCard` — the locked **Type Card**: type-tinted horizontal gradient
  (`type 38% → surface`), rounded 20.dp, soft shadow; left column = saturated
  type-color `#NNN` (monospace) + dark name (type-hue-tinted, "Darker" level:
  ~`type 35% / near-black`) + white type chips; artwork (Coil) on the right.
  Used by **browse, favorites, and team** rows (team adds trailing controls).
- `PokemonTypeChip` — existing; keep, ensure white label + type background.
- `StatBar` — label (54.dp) · rounded track · type-gradient fill (animated
  width) · value. Used in detail.
- `StatefulLoading/Error/Empty` — restyle the existing shared state composables
  to the new look (playful spinner/illustration optional; keep simple).

### Screens
- **Browse / Favorites / Team:** `LazyColumn` of `PokemonCard` (spacing + content
  padding), keeping current status-bar insets. Team card shows up/down/remove in
  the trailing slot. Empty/error/loading use the restyled state components.
  Search field restyled (rounded, type-neutral) but unchanged in behavior.
- **Detail (showpiece):** locked **hero A** — full-bleed type **gradient hero**
  (`gradientFor(types)`) with a soft radial glow, full artwork centered above a
  white **sheet** with 26.dp top corners that overlaps upward; sheet holds `#num`
  + bold name, type chips, "Base stats" with six `StatBar`s, and height/weight
  pills. Back + favorite + team actions float in a transparent top bar over the
  hero (white tint). No number watermark.

## Motion

- **Shared element (list → detail):** wrap `PokedexNavHost` in
  `SharedTransitionLayout`; the card artwork and the detail hero artwork share a
  key (`"art-$id"`) so the sprite morphs between them. Requires threading
  `SharedTransitionScope` + `AnimatedContentScope` into the list card and detail
  screen (via composition locals or params). Uses Compose animation
  `SharedTransitionLayout` (stable in our Compose BOM).
- **Stat bars:** animate fill width from 0 → value with a spring on first
  display (`animateFloatAsState`, visible-once).
- **Favorite/team toggles:** spring scale-pop + crossfade between filled/outline
  icons.
- **Screen/content:** default Nav Compose transitions kept; list items use a
  subtle fade/slide-in.
- All motion respects reduced-motion sensibilities (keep durations short; no
  blocking animations).

## Testing

- This is presentation/motion: existing **unit** tests are unaffected (no data
  changes).
- **Instrumented (Hilt UI)** tests assert by text/contentDescription — keep them
  green. The favorite-toggle test relies on the `contentDescription` flip
  ("Add to favorites" ↔ "Remove from favorites"); preserve those exact strings on
  the restyled action. The empty-state strings must remain unchanged (Team/
  Favorites copy) or the tests update in lockstep.
- Add a Robolectric Compose component test for `StatBar` (renders value/label)
  and `PokemonCard` (renders name + number), mirroring the existing
  `ComponentsUiTest` pattern.
- Shared-element/animation themselves aren't unit-tested (known Compose-test
  friction); verify on-device.

## Constraints

- AGP 8.13.2 / Gradle 8.13 / KSP / Hilt / Java 17; package `com.rogerparis.pokedex`.
- Keep status-bar insets fix on browse/favorites/team; detail hero draws under
  the status bar by design (its top bar handles touch targets).
- Memoize correctly: hoist gradients/brushes with `remember(type)`, stable
  lambdas, avoid per-recomposition allocations in list rows.
- Minimize comments; explain non-obvious *why* only.

## Build order (reviewable chunks — for the plan)

1. **Theme foundation** — palette (light/dark), shapes, typography; shared
   `PokemonTypeColors` (`typeColor`, `gradientFor`). App still works, lightly
   restyled.
2. **`PokemonCard` + lists** — build the Type Card; adopt in browse, favorites,
   team. Restyle search field + state components.
3. **Detail hero + `StatBar`** — gradient hero, sheet, animated stat bars,
   height/weight pills, restyled top-bar actions.
4. **Motion** — shared-element artwork transition (list → detail); toggle
   spring-pops; list-item enter.
5. **Component tests + on-device polish pass.**

## Open notes
- Custom display font deferred (system font for v1).
- Reduced-motion / accessibility refinements beyond "short, non-blocking" are a
  later concern.
