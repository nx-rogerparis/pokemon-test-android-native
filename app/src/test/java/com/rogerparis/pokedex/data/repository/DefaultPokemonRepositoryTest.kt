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
