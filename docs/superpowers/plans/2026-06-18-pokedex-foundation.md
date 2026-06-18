# Pokédex Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the project foundation — renamed module, modern toolchain, DI, networking, and a tested domain layer — so feature screens can be built on top.

**Architecture:** Single Gradle module, layered packages under `com.rogerparis.pokedex` (`data` → `domain` → `ui` → `di`). MVVM + UDF. This plan covers spec chunks 0–3 (rename, setup, remote, domain+mappers+errors). Feature chunks (list/detail/favorites) come in a separate plan once these concrete types exist.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, Jetpack Compose, Hilt (KSP), Retrofit + OkHttp + kotlinx.serialization, Coroutines. JUnit + MockK + coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-18-pokedex-design.md`

## Global Constraints

- Package root: `com.rogerparis.pokedex`. Module name: `Pokedex`.
- `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36`.
- Build JVM: Java 17 via `jvmToolchain(17)`.
- Annotation processing: **KSP only** (never kapt).
- Dependencies point downward only: `ui → domain → data`. `domain/model` and `domain/error` import nothing from Android/Retrofit/Room.
- Repository translates all exceptions into a typed result exactly once; nothing above the repository sees a raw exception.
- Sprite URL convention: `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/{id}.png`.
- Retrofit base URL: `https://pokeapi.co/api/v2/` (trailing slash required).
- `Json { ignoreUnknownKeys = true }` everywhere — DTOs model only the fields we use.
- **Claude never stages or commits.** Each task ends with a *suggested* commit message; the user reviews and commits. No ticket branch yet → plain Conventional Commits messages.
- Comments: explain non-obvious *why* only; no narration comments.

---

## File Structure

```
settings.gradle.kts                                   # rootProject.name
gradle/libs.versions.toml                             # version catalog (all deps)
app/build.gradle.kts                                  # plugins, namespace, toolchain, deps
app/src/main/AndroidManifest.xml                      # Application name, theme
app/src/main/java/com/rogerparis/pokedex/
├── PokedexApp.kt                                      # @HiltAndroidApp Application
├── MainActivity.kt                                    # @AndroidEntryPoint (renamed)
├── data/
│   ├── remote/PokeApi.kt                              # Retrofit interface
│   ├── remote/dto/PokemonListResponse.kt             # list DTOs
│   ├── remote/dto/PokemonDetailDto.kt                # detail DTOs
│   ├── remote/SpriteUrls.kt                          # id-from-url + artwork-url helpers
│   ├── mapper/PokemonMappers.kt                      # DTO → domain
│   └── repository/DefaultPokemonRepository.kt        # implements domain interface
├── domain/
│   ├── model/Pokemon.kt                              # Pokemon, Stat, PokemonListEntry
│   ├── error/AppError.kt                             # AppError + ApiResult
│   └── repository/PokemonRepository.kt               # interface
└── di/
    ├── NetworkModule.kt                              # Json, OkHttp, Retrofit, PokeApi
    └── RepositoryModule.kt                           # binds repository
app/src/test/java/com/rogerparis/pokedex/             # JVM unit tests
```

---

## Task 1: Baseline build + rename to Pokedex (chunk 0)

**Files:**
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Move: `app/src/main/java/com/rogerparis/androidkotlintest/` → `.../pokedex/` (+ `test/`, `androidTest/`)
- Modify: `MainActivity.kt`, `ui/theme/Theme.kt` (package + symbol renames via sed)

**Interfaces:**
- Consumes: nothing.
- Produces: a building app rooted at `com.rogerparis.pokedex`; theme composable `PokedexTheme`.

- [ ] **Step 1: Baseline build BEFORE touching anything**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. If it fails, STOP and fix the starter (likely AGP 9.2.1 / `compileSdk { release(36) }` DSL issues) before renaming — otherwise you can't tell rename breakage from pre-existing breakage.

- [ ] **Step 2: Move package directories (preserves git history)**

```bash
git mv app/src/main/java/com/rogerparis/androidkotlintest app/src/main/java/com/rogerparis/pokedex
git mv app/src/test/java/com/rogerparis/androidkotlintest app/src/test/java/com/rogerparis/pokedex
git mv app/src/androidTest/java/com/rogerparis/androidkotlintest app/src/androidTest/java/com/rogerparis/pokedex
```

- [ ] **Step 3: Rewrite package + import strings (macOS sed)**

```bash
grep -rl 'com\.rogerparis\.androidkotlintest' app/src \
  | xargs sed -i '' 's/com\.rogerparis\.androidkotlintest/com.rogerparis.pokedex/g'
```

- [ ] **Step 4: Rename the theme symbol + style + app label**

```bash
# Theme composable: AndroidKotlinTestTheme -> PokedexTheme
grep -rl 'AndroidKotlinTestTheme' app/src \
  | xargs sed -i '' 's/AndroidKotlinTestTheme/PokedexTheme/g'
# XML style + app composable name
grep -rl 'Theme\.AndroidKotlinTest' app/src \
  | xargs sed -i '' 's/Theme\.AndroidKotlinTest/Theme.Pokedex/g'
sed -i '' 's/AndroidKotlinTestApp/PokedexApp/g' app/src/main/java/com/rogerparis/pokedex/MainActivity.kt
```
Note: `MainActivity.kt` defines a composable named `AndroidKotlinTestApp`; this renames it to `PokedexApp` to match the module. (The Hilt Application class in Task 2 is a *class* named `PokedexApp` in a separate file — no clash with this composable, but if your IDE flags it, rename the composable to `PokedexRoot`.)

- [ ] **Step 5: Update `app/build.gradle.kts` identifiers**

Change both occurrences:
```kotlin
namespace = "com.rogerparis.pokedex"
// ...
applicationId = "com.rogerparis.pokedex"
```

- [ ] **Step 6: Update `settings.gradle.kts`**

```kotlin
rootProject.name = "Pokedex"
```

- [ ] **Step 7: Update `strings.xml` app name**

In `app/src/main/res/values/strings.xml`, set:
```xml
<string name="app_name">Pokedex</string>
```

- [ ] **Step 8: Build to verify the rename is clean**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Fix any stray unrenamed reference the compiler reports.

- [ ] **Step 9: Suggested commit (user runs)**

```
chore: rename module to Pokedex / com.rogerparis.pokedex
```

---

## Task 2: Toolchain, dependencies, and Hilt wiring (chunk 1)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/rogerparis/pokedex/PokedexApp.kt`
- Modify: `app/src/main/java/com/rogerparis/pokedex/MainActivity.kt`

**Interfaces:**
- Consumes: renamed module from Task 1.
- Produces: KSP + Java 17 build; Hilt graph bootstrapped (`@HiltAndroidApp` Application, `@AndroidEntryPoint` activity); `kotlinx.serialization`, Retrofit, OkHttp, Coroutines, MockK, coroutines-test available on the classpath.

- [ ] **Step 1: Add versions + libraries to the catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:
```toml
ksp = "2.2.10-2.0.2"
hilt = "2.56.2"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
coroutines = "1.9.0"
mockk = "1.13.13"
```
Add under `[libraries]`:
```toml
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
```
Add under `[plugins]`:
```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply plugins + toolchain + deps in `app/build.gradle.kts`**

In the `plugins { }` block add:
```kotlin
alias(libs.plugins.ksp)
alias(libs.plugins.hilt)
alias(libs.plugins.kotlin.serialization)
```
Replace the `compileOptions { sourceCompatibility = VERSION_11; targetCompatibility = VERSION_11 }` block with a toolchain. Add inside `android { }`:
```kotlin
kotlin {
    jvmToolchain(17)
}
```
(Remove the old `compileOptions` Java-11 block.) In `dependencies { }` add:
```kotlin
implementation(libs.hilt.android)
ksp(libs.hilt.compiler)
implementation(libs.retrofit)
implementation(libs.retrofit.kotlinx.serialization)
implementation(libs.okhttp.logging)
implementation(libs.kotlinx.serialization.json)
implementation(libs.kotlinx.coroutines.core)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.mockk)
```

- [ ] **Step 3: Sync to validate versions (the version gate)**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. If it fails on the KSP version, bump the `ksp` suffix (try `2.2.10-2.0.3`, etc. — the prefix must stay `2.2.10`); re-run until the sync resolves.

- [ ] **Step 4: Create the Hilt Application class**

Create `app/src/main/java/com/rogerparis/pokedex/PokedexApp.kt`:
```kotlin
package com.rogerparis.pokedex

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PokedexApp : Application()
```
If Task 1 renamed the *composable* to `PokedexApp`, rename that composable to `PokedexRoot` now to free this class name.

- [ ] **Step 5: Register the Application + annotate the Activity**

In `AndroidManifest.xml`, add `android:name=".PokedexApp"` to the `<application>` tag (first attribute):
```xml
<application
    android:name=".PokedexApp"
    android:allowBackup="true"
    ...>
```
In `MainActivity.kt`, add the annotation and import:
```kotlin
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
```

- [ ] **Step 6: Build to verify Hilt code-gen runs**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (Hilt generates `Hilt_MainActivity` etc.). A failure here usually means the Application isn't registered or KSP isn't applied.

- [ ] **Step 7: Suggested commit (user runs)**

```
build: add KSP, Hilt, Retrofit, serialization, coroutines; wire Hilt app
```

---

## Task 3: Remote layer — DTOs, sprite helpers, PokeApi, NetworkModule (chunk 2)

**Files:**
- Create: `data/remote/dto/PokemonListResponse.kt`, `data/remote/dto/PokemonDetailDto.kt`, `data/remote/SpriteUrls.kt`, `data/remote/PokeApi.kt`, `di/NetworkModule.kt`
- Test: `app/src/test/java/com/rogerparis/pokedex/data/remote/SpriteUrlsTest.kt`, `.../data/remote/dto/PokemonDetailDtoTest.kt`

**Interfaces:**
- Consumes: kotlinx.serialization, Retrofit, Hilt from Task 2.
- Produces:
  - `fun pokemonIdFromUrl(url: String): Int`
  - `fun officialArtworkUrl(id: Int): String`
  - DTOs: `PokemonListResponse(count, next, previous, results)`, `PokemonListItemDto(name, url)`, `PokemonDetailDto(id, name, height, weight, types, stats, abilities)`, `TypeSlotDto(slot, type)`, `StatSlotDto(baseStat, stat)`, `AbilitySlotDto(ability, isHidden, slot)`, `NamedApiResourceDto(name, url)`
  - `interface PokeApi { suspend fun getPokemonList(limit, offset): PokemonListResponse; suspend fun getPokemonDetail(id): PokemonDetailDto }`
  - Hilt provides `PokeApi`.

- [ ] **Step 1: Write the failing sprite-helper test**

Create `app/src/test/java/com/rogerparis/pokedex/data/remote/SpriteUrlsTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteUrlsTest {
    @Test
    fun `extracts id from trailing path segment`() {
        assertEquals(1, pokemonIdFromUrl("https://pokeapi.co/api/v2/pokemon/1/"))
    }

    @Test
    fun `extracts id when url has no trailing slash`() {
        assertEquals(151, pokemonIdFromUrl("https://pokeapi.co/api/v2/pokemon/151"))
    }

    @Test
    fun `builds official artwork url from id`() {
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png",
            officialArtworkUrl(25),
        )
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.SpriteUrlsTest"`
Expected: FAIL — `pokemonIdFromUrl` / `officialArtworkUrl` unresolved.

- [ ] **Step 3: Implement the helpers**

Create `app/src/main/java/com/rogerparis/pokedex/data/remote/SpriteUrls.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote

fun pokemonIdFromUrl(url: String): Int =
    url.trimEnd('/').substringAfterLast('/').toInt()

fun officialArtworkUrl(id: Int): String =
    "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$id.png"
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.SpriteUrlsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Write the list DTOs**

Create `app/src/main/java/com/rogerparis/pokedex/data/remote/dto/PokemonListResponse.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PokemonListResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<PokemonListItemDto>,
)

@Serializable
data class PokemonListItemDto(
    val name: String,
    val url: String,
)
```

- [ ] **Step 6: Write the failing detail-DTO deserialization test**

Create `app/src/test/java/com/rogerparis/pokedex/data/remote/dto/PokemonDetailDtoTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonDetailDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses id name height weight and ignores unknown fields`() {
        val raw = """
            {"id":1,"name":"bulbasaur","height":7,"weight":69,
             "base_experience":64,
             "types":[{"slot":1,"type":{"name":"grass","url":"u"}}],
             "stats":[{"base_stat":45,"stat":{"name":"hp","url":"u"}}],
             "abilities":[{"ability":{"name":"overgrow","url":"u"},"is_hidden":false,"slot":1}]}
        """.trimIndent()

        val dto = json.decodeFromString<PokemonDetailDto>(raw)

        assertEquals(1, dto.id)
        assertEquals("bulbasaur", dto.name)
        assertEquals(7, dto.height)
        assertEquals(69, dto.weight)
        assertEquals("grass", dto.types.first().type.name)
        assertEquals(45, dto.stats.first().baseStat)
        assertEquals("overgrow", dto.abilities.first().ability.name)
        assertEquals(false, dto.abilities.first().isHidden)
    }
}
```

- [ ] **Step 7: Run the test, verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.dto.PokemonDetailDtoTest"`
Expected: FAIL — `PokemonDetailDto` unresolved.

- [ ] **Step 8: Write the detail DTOs**

Create `app/src/main/java/com/rogerparis/pokedex/data/remote/dto/PokemonDetailDto.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PokemonDetailDto(
    val id: Int,
    val name: String,
    val height: Int,
    val weight: Int,
    val types: List<TypeSlotDto>,
    val stats: List<StatSlotDto>,
    val abilities: List<AbilitySlotDto>,
)

@Serializable
data class TypeSlotDto(
    val slot: Int,
    val type: NamedApiResourceDto,
)

@Serializable
data class StatSlotDto(
    @SerialName("base_stat") val baseStat: Int,
    val stat: NamedApiResourceDto,
)

@Serializable
data class AbilitySlotDto(
    val ability: NamedApiResourceDto,
    @SerialName("is_hidden") val isHidden: Boolean,
    val slot: Int,
)

@Serializable
data class NamedApiResourceDto(
    val name: String,
    val url: String,
)
```

- [ ] **Step 9: Run the test, verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.remote.dto.PokemonDetailDtoTest"`
Expected: PASS.

- [ ] **Step 10: Write the Retrofit interface**

Create `app/src/main/java/com/rogerparis/pokedex/data/remote/PokeApi.kt`:
```kotlin
package com.rogerparis.pokedex.data.remote

import com.rogerparis.pokedex.data.remote.dto.PokemonDetailDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PokeApi {
    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): PokemonListResponse

    @GET("pokemon/{id}")
    suspend fun getPokemonDetail(@Path("id") id: Int): PokemonDetailDto
}
```

- [ ] **Step 11: Write the NetworkModule**

Create `app/src/main/java/com/rogerparis/pokedex/di/NetworkModule.kt`:
```kotlin
package com.rogerparis.pokedex.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rogerparis.pokedex.data.remote.PokeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://pokeapi.co/api/v2/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun providePokeApi(retrofit: Retrofit): PokeApi = retrofit.create(PokeApi::class.java)
}
```
Note on the converter import: with `com.squareup.retrofit2:converter-kotlinx-serialization`, the `asConverterFactory` extension is at `retrofit2.converter.kotlinx.serialization.asConverterFactory`. If your IDE can't resolve `com.jakewharton...`, switch the import to the `retrofit2.converter...` package — same function, the artifact you declared in Task 2 determines which one resolves.

- [ ] **Step 12: Build to verify the remote layer + DI compile**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 13: Suggested commit (user runs)**

```
feat(data): add PokeApi, DTOs, sprite-url helpers, network DI
```

---

## Task 4: Domain layer — models, errors, mappers, repository (chunk 3)

**Files:**
- Create: `domain/model/Pokemon.kt`, `domain/error/AppError.kt`, `domain/repository/PokemonRepository.kt`, `data/mapper/PokemonMappers.kt`, `data/repository/DefaultPokemonRepository.kt`, `di/RepositoryModule.kt`
- Test: `.../data/mapper/PokemonMappersTest.kt`, `.../data/repository/DefaultPokemonRepositoryTest.kt`

**Interfaces:**
- Consumes: DTOs + sprite helpers (Task 3), `PokeApi` (Task 3).
- Produces:
  - `data class PokemonListEntry(id: Int, name: String, artworkUrl: String)`
  - `data class Pokemon(id, name, heightDm, weightHg, types: List<String>, stats: List<Stat>, abilities: List<String>, artworkUrl: String)`; `data class Stat(name: String, baseValue: Int)`
  - `sealed interface AppError { Network; NotFound; data class Unknown(message: String?) }`
  - `sealed interface ApiResult<out T> { data class Success<T>(data: T); data class Error(error: AppError) }`
  - `PokemonListItemDto.toEntry(): PokemonListEntry`, `PokemonDetailDto.toDomain(): Pokemon`
  - `interface PokemonRepository { suspend fun getPokemon(id: Int): ApiResult<Pokemon> }`

- [ ] **Step 1: Write the domain models**

Create `app/src/main/java/com/rogerparis/pokedex/domain/model/Pokemon.kt`:
```kotlin
package com.rogerparis.pokedex.domain.model

data class PokemonListEntry(
    val id: Int,
    val name: String,
    val artworkUrl: String,
)

data class Pokemon(
    val id: Int,
    val name: String,
    val heightDm: Int,
    val weightHg: Int,
    val types: List<String>,
    val stats: List<Stat>,
    val abilities: List<String>,
    val artworkUrl: String,
)

data class Stat(
    val name: String,
    val baseValue: Int,
)
```

- [ ] **Step 2: Write the error + result types**

Create `app/src/main/java/com/rogerparis/pokedex/domain/error/AppError.kt`:
```kotlin
package com.rogerparis.pokedex.domain.error

sealed interface AppError {
    data object Network : AppError
    data object NotFound : AppError
    data class Unknown(val message: String?) : AppError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val error: AppError) : ApiResult<Nothing>
}
```
Named `ApiResult` (not `Result`) to avoid clashing with `kotlin.Result`.

- [ ] **Step 3: Write the failing mapper test**

Create `app/src/test/java/com/rogerparis/pokedex/data/mapper/PokemonMappersTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.remote.dto.AbilitySlotDto
import com.rogerparis.pokedex.data.remote.dto.NamedApiResourceDto
import com.rogerparis.pokedex.data.remote.dto.PokemonDetailDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.data.remote.dto.StatSlotDto
import com.rogerparis.pokedex.data.remote.dto.TypeSlotDto
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonMappersTest {
    @Test
    fun `list item maps to entry with derived id and artwork`() {
        val dto = PokemonListItemDto(name = "pikachu", url = "https://pokeapi.co/api/v2/pokemon/25/")
        val entry = dto.toEntry()
        assertEquals(25, entry.id)
        assertEquals("pikachu", entry.name)
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png",
            entry.artworkUrl,
        )
    }

    @Test
    fun `detail maps to domain with types ordered by slot`() {
        val dto = PokemonDetailDto(
            id = 6, name = "charizard", height = 17, weight = 905,
            types = listOf(
                TypeSlotDto(slot = 2, type = NamedApiResourceDto("flying", "u")),
                TypeSlotDto(slot = 1, type = NamedApiResourceDto("fire", "u")),
            ),
            stats = listOf(StatSlotDto(baseStat = 78, stat = NamedApiResourceDto("hp", "u"))),
            abilities = listOf(AbilitySlotDto(NamedApiResourceDto("blaze", "u"), isHidden = false, slot = 1)),
        )
        val domain = dto.toDomain()
        assertEquals(6, domain.id)
        assertEquals(listOf("fire", "flying"), domain.types)
        assertEquals("hp", domain.stats.first().name)
        assertEquals(78, domain.stats.first().baseValue)
        assertEquals(listOf("blaze"), domain.abilities)
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/6.png",
            domain.artworkUrl,
        )
    }
}
```

- [ ] **Step 4: Run the test, verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.mapper.PokemonMappersTest"`
Expected: FAIL — `toEntry` / `toDomain` unresolved.

- [ ] **Step 5: Write the mappers**

Create `app/src/main/java/com/rogerparis/pokedex/data/mapper/PokemonMappers.kt`:
```kotlin
package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.remote.officialArtworkUrl
import com.rogerparis.pokedex.data.remote.pokemonIdFromUrl
import com.rogerparis.pokedex.data.remote.dto.PokemonDetailDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.model.Stat

fun PokemonListItemDto.toEntry(): PokemonListEntry {
    val id = pokemonIdFromUrl(url)
    return PokemonListEntry(id = id, name = name, artworkUrl = officialArtworkUrl(id))
}

fun PokemonDetailDto.toDomain(): Pokemon = Pokemon(
    id = id,
    name = name,
    heightDm = height,
    weightHg = weight,
    types = types.sortedBy { it.slot }.map { it.type.name },
    stats = stats.map { Stat(name = it.stat.name, baseValue = it.baseStat) },
    abilities = abilities.map { it.ability.name },
    artworkUrl = officialArtworkUrl(id),
)
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.mapper.PokemonMappersTest"`
Expected: PASS.

- [ ] **Step 7: Write the repository interface**

Create `app/src/main/java/com/rogerparis/pokedex/domain/repository/PokemonRepository.kt`:
```kotlin
package com.rogerparis.pokedex.domain.repository

import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.model.Pokemon

interface PokemonRepository {
    suspend fun getPokemon(id: Int): ApiResult<Pokemon>
}
```
(The paging method is added in the feature plan, where Paging 3 is introduced.)

- [ ] **Step 8: Write the failing repository test**

Create `app/src/test/java/com/rogerparis/pokedex/data/repository/DefaultPokemonRepositoryTest.kt`:
```kotlin
package com.rogerparis.pokedex.data.repository

import com.rogerparis.pokedex.data.remote.PokeApi
import com.rogerparis.pokedex.data.remote.dto.PokemonDetailDto
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.error.AppError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class DefaultPokemonRepositoryTest {
    private val api = mockk<PokeApi>()
    private val repository = DefaultPokemonRepository(api)

    private fun detailDto() = PokemonDetailDto(
        id = 1, name = "bulbasaur", height = 7, weight = 69,
        types = emptyList(), stats = emptyList(), abilities = emptyList(),
    )

    @Test
    fun `returns Success with mapped domain on api success`() = runTest {
        coEvery { api.getPokemonDetail(1) } returns detailDto()
        val result = repository.getPokemon(1)
        assertEquals(ApiResult.Success::class, result::class)
        assertEquals("bulbasaur", (result as ApiResult.Success).data.name)
    }

    @Test
    fun `maps IOException to Network error`() = runTest {
        coEvery { api.getPokemonDetail(1) } throws IOException("no connection")
        val result = repository.getPokemon(1)
        assertEquals(ApiResult.Error(AppError.Network), result)
    }

    @Test
    fun `maps HTTP 404 to NotFound error`() = runTest {
        val http404 = HttpException(
            Response.error<Any>(404, "".toResponseBody("application/json".toMediaType())),
        )
        coEvery { api.getPokemonDetail(1) } throws http404
        val result = repository.getPokemon(1)
        assertEquals(ApiResult.Error(AppError.NotFound), result)
    }
}
```

- [ ] **Step 9: Run the test, verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.repository.DefaultPokemonRepositoryTest"`
Expected: FAIL — `DefaultPokemonRepository` unresolved.

- [ ] **Step 10: Write the repository implementation**

Create `app/src/main/java/com/rogerparis/pokedex/data/repository/DefaultPokemonRepository.kt`:
```kotlin
package com.rogerparis.pokedex.data.repository

import com.rogerparis.pokedex.data.mapper.toDomain
import com.rogerparis.pokedex.data.remote.PokeApi
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class DefaultPokemonRepository @Inject constructor(
    private val api: PokeApi,
) : PokemonRepository {

    override suspend fun getPokemon(id: Int): ApiResult<Pokemon> =
        try {
            ApiResult.Success(api.getPokemonDetail(id).toDomain())
        } catch (e: HttpException) {
            if (e.code() == 404) ApiResult.Error(AppError.NotFound)
            else ApiResult.Error(AppError.Unknown(e.message()))
        } catch (e: IOException) {
            ApiResult.Error(AppError.Network)
        }
}
```

- [ ] **Step 11: Run the test, verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.rogerparis.pokedex.data.repository.DefaultPokemonRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 12: Bind the repository in Hilt**

Create `app/src/main/java/com/rogerparis/pokedex/di/RepositoryModule.kt`:
```kotlin
package com.rogerparis.pokedex.di

import com.rogerparis.pokedex.data.repository.DefaultPokemonRepository
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPokemonRepository(impl: DefaultPokemonRepository): PokemonRepository
}
```

- [ ] **Step 13: Full build + full unit suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all tests green (sprite, DTO, mapper, repository).

- [ ] **Step 14: Suggested commit (user runs)**

```
feat(domain): add models, ApiResult/AppError, mappers, repository + Hilt binding
```

---

## Self-Review

**Spec coverage (chunks 0–3):**
- Chunk 0 rename → Task 1. ✓
- Chunk 1 setup (KSP, Java 17, Hilt) → Task 2. ✓
- Chunk 2 remote (Retrofit, DTOs, kotlinx.serialization, id/sprite helper, network DI, live-call verifiable via build) → Task 3. ✓
- Chunk 3 domain + mappers + errors → Task 4. ✓
- Decisions covered: list-sprite derivation (Task 3 helper + Task 4 mapper), typed `ApiResult`/`AppError` (Task 4), repository translates exceptions once (Task 4 repo + test). ✓
- Out of scope here (feature plan): Paging, Room/favorites, Coil, type-safe navigation, adaptive nav screens, Compose UI tests, Robolectric.

**Placeholder scan:** No TBD/TODO; every code step shows full code; every test step shows the assertions; commands include expected output. ✓

**Type consistency:** `PokemonListEntry`, `Pokemon`, `Stat`, `ApiResult`, `AppError`, `PokeApi.getPokemonDetail`, `toEntry`, `toDomain`, `DefaultPokemonRepository(api)` names are identical across the Interfaces blocks, code, and tests. Helper names `pokemonIdFromUrl` / `officialArtworkUrl` consistent in Task 3 and Task 4. ✓

## Notes carried to the feature plan
- `PokemonRepository` gains a `pokemonPager(): Flow<PagingData<PokemonListEntry>>` when Paging 3 is introduced.
- A live-network smoke check (one real `getPokemonDetail(1)` call) is best done as an instrumented/manual run once an emulator is up — not part of the JVM unit suite.
