# Pokédex — Android Kotlin learning app

Learning project: a React Native dev practicing idiomatic native Android Kotlin on a
networked-CRUD shape. Backed by the free [PokéAPI](https://pokeapi.co) (no API key).

**Design spec (source of truth):** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Architecture

- Single Gradle module, layered packages under `com.rogerparis.pokedex`:
  `data/` (remote, local, repository, mapper) → `domain/` (model, repository, error)
  → `ui/` (list, detail, favorites, navigation, theme) → `di/`.
- **MVVM + UDF.** ViewModels expose `StateFlow<UiState>` (sealed `Loading/Success/Error`);
  Views are Compose, render state, emit events up via lambdas. No two-way binding.
- Dependencies point downward only: `ui → domain → data`. `domain/model` is pure
  Kotlin (no Android/Room/Retrofit imports).
- Repository returns a sealed `Result<T>` with typed `AppError`
  (`Network/NotFound/Unknown`). Repository owns all try/catch; nothing above it sees
  a raw exception. Paging errors flow via `LoadResult.Error`.
- No UseCase classes unless logic is genuinely shared/complex.

## Stack

Compose + Material3 + adaptive-navigation-suite · Hilt (KSP) · Retrofit + OkHttp +
kotlinx.serialization · Room (KSP) · Paging 3 (network-only in v1) · Coil 3 ·
Navigation Compose (type-safe `@Serializable` routes) · Coroutines + Flow.

- Toolchain: **KSP** (not kapt), **Java 17** via `jvmToolchain(17)`. `minSdk 24`.
- List sprites: derive id from item `url`, build official-artwork URL by convention.
- Offline scope v1: **favorites only** (denormalized Room snapshot). Browse list
  needs network. RemoteMediator offline browse is a stretch chunk.
- Favorite toggle lives on the **detail** screen only.

## Workflow conventions

- **Brainstorm → grill-me before any new feature.** Don't skip grill-me.
- **TDD on features**: test first, red-green-refactor. ViewModel + repository tests
  are highest value; write them first.
- Unit suite runs on the JVM (Robolectric for Room/DAO). `androidTest/` reserved for
  Compose UI tests.
- Build in reviewable chunks (see spec build order). One commit per chunk.
- **Claude never stages or commits.** Suggest a commit message per chunk; the user
  commits after review. No ticket branch yet → plain messages.
- Minimize comments; explain non-obvious *why* only. Memoize Compose correctly
  (hoist state, `remember`, stable lambdas — the Android analog of `useCallback`).
- Refactor existing code rather than duplicate.

## Common commands

- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented (Compose UI) tests: `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lint`
