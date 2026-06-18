# Pokédex — Android Kotlin Learning App: Design Spec

**Date:** 2026-06-18
**Status:** Design approved + grilled. Pending writing-plans → implementation.
**Author:** Roger Paris (with Claude)

## Purpose

A learning project for a React Native developer transitioning to native Android
Kotlin. Goal: practice the idiomatic, modern Android stack on a **networked CRUD**
shape (matches the upcoming real project). The app itself is secondary to learning
the patterns: layered architecture, MVVM + UDF, DI, networking, local persistence,
pagination, and testing.

## App concept

A **Pokédex** backed by the free [PokéAPI](https://pokeapi.co) (REST, no API key).

- PokéAPI is **read-only**, so create/update/delete is exercised **locally**:
  favorites are stored in Room (insert/delete = the C/U/D).
- **Key API fact:** the list endpoint (`/pokemon?limit&offset`) returns only
  `name` + `url` per item — no id, sprite, or types. The id is the trailing path
  segment of `url`; sprite URLs follow a fixed convention from the id.

## Architecture

**Approach: single Gradle module, layered packages.** MVVM + unidirectional data
flow (UDF). Chosen over multi-module (too much wiring for learning) and minimal
(skips the patterns that matter at the real job).

### Pattern: MVVM + UDF

- **Model** = `data` + `domain` layers (repository, DTOs, Room entities, pure models).
- **View** = Compose `@Composable` screens. Render `UiState`, emit events upward.
- **ViewModel** = `androidx.lifecycle.ViewModel`. Holds `StateFlow<UiState>`, calls
  repository, survives config changes.

Modern flavor: immutable state flows down, events flow up via lambdas (no two-way
binding); one `StateFlow<UiState>` (sealed `Loading/Success/Error`) collected with
`collectAsStateWithLifecycle()`. Edges toward MVI if the event side is later
formalized into an `Intent`/`Action` sealed type — flagged in code.

### Layers (dependencies point downward: ui → domain → data)

```
com.rogerparis.pokedex/
├── data/
│   ├── remote/        # Retrofit PokeApi, DTOs, id-from-url + sprite-URL helper
│   ├── local/         # Room: favorite entity, DAO, database (KSP)
│   ├── repository/    # PokemonRepository impl
│   └── mapper/        # DTO ↔ domain ↔ entity converters
├── domain/
│   ├── model/         # Pure Kotlin models (no Android/Room/Retrofit imports)
│   ├── repository/    # Repository interfaces (impl in data/)
│   └── error/         # AppError sealed type + Result<T> wrapper
├── ui/
│   ├── list/          # PokemonListViewModel + screen + UiState
│   ├── detail/        # PokemonDetailViewModel + screen + UiState
│   ├── favorites/     # FavoritesViewModel + screen
│   ├── navigation/    # type-safe routes + adaptive NavigationSuiteScaffold
│   └── theme/         # (exists)
└── di/                # Hilt modules (network, database, repository bindings)
```

**Domain layer is lean for v1**: repository interfaces, pure models, error types —
**no UseCase classes** (added only when logic is shared/complex). Code flags where a
use case *would* go.

### Resolved design decisions (from grill-me)

1. **List sprites** — derive id from the item `url`'s trailing segment, build the
   official-artwork URL by convention
   (`https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/{id}.png`),
   Coil loads it. No per-item detail fetch (avoids N+1). Encapsulated in a helper.
2. **Pagination** — network-only Paging 3 (`PagingSource` → Retrofit, page size 20).
   `collectAsLazyPagingItems()` in a `LazyColumn` with loading/error footers. Room is
   **not** in the browse path.
3. **Offline scope (v1)** — **favorites only.** The browse list requires network.
   Full offline browse via `RemoteMediator` is a stretch chunk.
4. **Favorites storage** — denormalized Room snapshot (id, name, sprite URL, types,
   key stats) so favorites render fully offline. Favorite toggle lives **on the
   detail screen** only (where full data exists). No list-row favoriting in v1.
5. **Error model** — repository returns a sealed `Result<T>` (`Success`/`Error`)
   with a typed `AppError` (`Network`/`NotFound`/`Unknown`). Repository owns all
   try/catch and translates exceptions exactly once; nothing above it sees a raw
   exception. Paging errors flow via `LoadResult.Error` (its own channel).
6. **Toolchain** — KSP (not kapt) for Hilt + Room; Java 17 via `jvmToolchain(17)`.
   `minSdk 24` unchanged (Android runtime floor, separate from build JVM).
7. **Navigation** — type-safe `@Serializable` routes: `List`, `Detail(id)`,
   `Favorites`. Adaptive `NavigationSuiteScaffold` with List + Favorites as
   top-level tabs (bottom bar on phone, rail on tablet); Detail pushed over either.

### Data flow — vertical slice (list)

```
PokeApi (Retrofit)   →  PagingSource    →  ViewModel             →  Composable
  suspend getPokemon()   Pager / PagingData  StateFlow<PagingData>   collectAsLazyPagingItems()
  (page = limit/offset)  (network-only)                              LazyColumn + footers
```

Detail / favorites (non-paged) flow through the repository's `Result<T>` API and the
sealed `UiState`.

## Features (v1)

1. **List** — paginated Pokémon list (network Paging 3), sprite + name, tap → detail.
2. **Detail** — sprite, types, base stats, abilities, height/weight; favorite toggle.
3. **Favorites** — local Room list, renders offline, fed by the detail toggle.
4. **Offline** — **favorites** readable with no network. Browse list needs network.

**Deferred to v2 / stretch:** `RemoteMediator` offline browse list; "My Team" builder
(max 6, reorder); search; type filter.

## Tech stack

| Concern | Library | RN analog |
|---|---|---|
| UI | Jetpack Compose + Material3 | React components / JSX |
| Adaptive nav | material3-adaptive-navigation-suite | tab navigator |
| State | `ViewModel` + `StateFlow<UiState>` | Zustand/Redux store (survives rotation) |
| State collection | `collectAsStateWithLifecycle()` | `useSelector` / `useStore` |
| DI | Hilt (KSP) | DI container / context providers |
| Networking | Retrofit + OkHttp + kotlinx.serialization | axios with typed endpoints |
| Async | Coroutines + Flow | async/await + RxJS |
| Local DB | Room (KSP) | WatermelonDB / structured SQLite |
| Pagination | Paging 3 (network-only) | FlatList `onEndReached` |
| Images | Coil 3 | FastImage (cached) |
| Navigation | Navigation Compose (type-safe) | React Navigation |
| Unit testing | JUnit + coroutines-test + Turbine + MockK + Robolectric | Jest + RTL |
| UI testing | Compose UI test (instrumented) | RTL/Detox |

**Versions:** NOT pinned from memory (stale versions break Gradle sync). Pulled fresh
into `gradle/libs.versions.toml` during chunk 1 and verified by a real `./gradlew build`.
KSP plugin version must track Kotlin 2.2.10.

## Testing strategy

TDD on features: test first, red-green-refactor. High-value tests (ViewModel +
repository) first.

| Layer | Tool | Runs on | What is tested |
|---|---|---|---|
| Mappers | JUnit (pure) | JVM | DTO↔domain↔entity correctness |
| Repository / DAO | JUnit + Robolectric + in-memory Room + fake API | JVM | cache/network logic, offline favorites |
| ViewModel | JUnit + coroutines-test + Turbine | JVM | UiState transitions (Loading→Success→Error) |
| UI | Compose UI test (`createComposeRule`) | instrumented | renders state, click emits event |

Whole unit suite stays on the JVM (Robolectric) for a fast TDD loop; `androidTest/`
reserved for Compose UI tests. `MockK` for mocking.

## Build order — reviewable chunks (one commit each)

0. **Rename** — `Pokedex` / `com.rogerparis.pokedex`: `settings.gradle.kts`,
   namespace + applicationId, move package dirs, fix `package`/`import` in
   `MainActivity` + `ui/theme/*` + example tests. Verify `./gradlew assembleDebug`.
1. **Setup** — KSP plugin + Java 17 toolchain; deps into version catalog; wire Hilt
   (`@HiltAndroidApp` Application, `@AndroidEntryPoint` activity); package skeleton.
2. **Remote** — Retrofit `PokeApi` + DTOs + kotlinx.serialization + network Hilt
   module; id-from-url + sprite-URL helper (+tests); verify one live call.
3. **Domain + mappers + errors** — pure models, repository interface, sealed
   `Result`/`AppError`, DTO→domain mappers (+tests).
4. **List feature** — network `PagingSource`, ViewModel, Compose list with paging
   footers, type-safe routes + adaptive `NavigationSuiteScaffold` shell (+tests, TDD).
5. **Detail feature** — fetch by id, ViewModel, screen, error/loading states (+tests).
6. **Favorites** — Room entity/DAO/DB (KSP), denormalized snapshot, detail-screen
   toggle, Favorites tab, offline reads, Robolectric DAO tests (+tests, TDD).
7. **Polish** — loading/empty/error states, offline messaging.
8. **Stretch** — `RemoteMediator` offline browse; My Team; search; type filter.

Each chunk = a commit reviewed before moving on. No ticket branch yet (`main`, no
commits); plain commit messages until a ticket branch exists. Commit/stage is done
by the user, not Claude.

## Next steps

1. **writing-plans** — produce the detailed implementation plan from this spec.
2. Implement chunk by chunk with review gates, TDD on features.
