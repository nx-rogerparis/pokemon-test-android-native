package com.rogerparis.pokedex.data.repository

import com.rogerparis.pokedex.data.local.FavoriteDao
import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonDao
import com.rogerparis.pokedex.data.local.PokemonIndexDao
import com.rogerparis.pokedex.data.local.TeamMemberDao
import com.rogerparis.pokedex.data.remote.PokeApi
import com.rogerparis.pokedex.data.remote.dto.NamedApiResourceDto
import com.rogerparis.pokedex.data.remote.dto.PokemonDetailDto
import com.rogerparis.pokedex.data.remote.dto.TypeSlotDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListResponse
import com.rogerparis.pokedex.domain.model.Stat
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
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
    private val favoriteDao = mockk<FavoriteDao>()
    private val database = mockk<PokedexDatabase>()
    private val pokemonDao = mockk<PokemonDao>()
    private val pokemonIndexDao = mockk<PokemonIndexDao>()
    private val teamMemberDao = mockk<TeamMemberDao>()
    private val repository =
        DefaultPokemonRepository(api, favoriteDao, database, pokemonDao, pokemonIndexDao, teamMemberDao)

    private fun samplePokemon() =
        Pokemon(1, "bulbasaur", 7, 69, listOf("grass"), emptyList(), listOf("overgrow"), "u")

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
    fun `maps IOException to Network error when not favorited`() = runTest {
        coEvery { api.getPokemonDetail(1) } throws IOException("no connection")
        coEvery { favoriteDao.getById(1) } returns null
        val result = repository.getPokemon(1)
        assertEquals(ApiResult.Error(AppError.Network), result)
    }

    @Test
    fun `falls back to favorite snapshot when offline and favorited`() = runTest {
        coEvery { api.getPokemonDetail(1) } throws IOException("offline")
        coEvery { favoriteDao.getById(1) } returns FavoriteEntity(
            id = 1, name = "bulbasaur", artworkUrl = "u", types = listOf("grass"),
            heightDm = 7, weightHg = 69, abilities = listOf("overgrow"), stats = listOf(Stat("hp", 45)),
        )

        val result = repository.getPokemon(1)

        assertEquals(ApiResult.Success::class, result::class)
        assertEquals("bulbasaur", (result as ApiResult.Success).data.name)
        assertEquals(listOf("grass"), result.data.types)
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

    @Test
    fun `ensureSearchIndex fetches and inserts when index is empty`() = runTest {
        coEvery { pokemonIndexDao.count() } returns 0
        coEvery { api.getPokemonList(limit = 100_000, offset = 0) } returns PokemonListResponse(
            count = 1,
            next = null,
            previous = null,
            results = listOf(PokemonListItemDto(name = "bulbasaur", url = "https://pokeapi.co/api/v2/pokemon/1/")),
        )
        coJustRun { pokemonIndexDao.insertAll(any()) }

        repository.ensureSearchIndex()

        coVerify { pokemonIndexDao.insertAll(any()) }
    }

    @Test
    fun `ensureSearchIndex does nothing when already populated`() = runTest {
        coEvery { pokemonIndexDao.count() } returns 1300

        repository.ensureSearchIndex()

        coVerify(exactly = 0) { api.getPokemonList(any(), any()) }
    }

    @Test
    fun `addToTeam inserts and returns true when below max`() = runTest {
        coEvery { teamMemberDao.count() } returns 3
        coJustRun { teamMemberDao.upsert(any()) }

        val added = repository.addToTeam(samplePokemon())

        assertEquals(true, added)
        coVerify { teamMemberDao.upsert(any()) }
    }

    @Test
    fun `addToTeam returns false and does not insert when team is full`() = runTest {
        coEvery { teamMemberDao.count() } returns 6

        val added = repository.addToTeam(samplePokemon())

        assertEquals(false, added)
        coVerify(exactly = 0) { teamMemberDao.upsert(any()) }
    }

    @Test
    fun `primaryType returns first type and caches it`() = runTest {
        coEvery { api.getPokemonDetail(1) } returns PokemonDetailDto(
            id = 1, name = "bulbasaur", height = 7, weight = 69,
            types = listOf(
                TypeSlotDto(slot = 2, type = NamedApiResourceDto("poison", "u")),
                TypeSlotDto(slot = 1, type = NamedApiResourceDto("grass", "u")),
            ),
            stats = emptyList(), abilities = emptyList(),
        )

        val first = repository.primaryType(1)
        val second = repository.primaryType(1)

        assertEquals("grass", first)
        assertEquals("grass", second)
        coVerify(exactly = 1) { api.getPokemonDetail(1) }
    }

    @Test
    fun `primaryType returns null on error`() = runTest {
        coEvery { api.getPokemonDetail(1) } throws IOException("offline")
        coEvery { favoriteDao.getById(1) } returns null

        assertEquals(null, repository.primaryType(1))
    }
}
