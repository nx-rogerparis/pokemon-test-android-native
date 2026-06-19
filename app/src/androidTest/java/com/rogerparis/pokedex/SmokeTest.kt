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
