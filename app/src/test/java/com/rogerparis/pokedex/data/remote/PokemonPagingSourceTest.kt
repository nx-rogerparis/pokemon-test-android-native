package com.rogerparis.pokedex.data.remote

import androidx.paging.PagingSource
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PokemonPagingSourceTest {
    private val api = mockk<PokeApi>()

    private fun page(vararg ids: Int, next: String?) = PokemonListResponse(
        count = 1000,
        next = next,
        previous = null,
        results = ids.map { PokemonListItemDto(name = "p$it", url = "https://pokeapi.co/api/v2/pokemon/$it/") },
    )

    @Test
    fun `first load returns mapped entries and next key`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns
            page(1, 2, 3, next = "https://pokeapi.co/api/v2/pokemon?offset=20&limit=20")
        val source = PokemonPagingSource(api)

        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(1, 2, 3), page.data.map { it.id })
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png",
            page.data.first().artworkUrl,
        )
        assertEquals(null, page.prevKey)
        assertEquals(20, page.nextKey)
    }

    @Test
    fun `null next means no more pages`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns page(1, next = null)
        val source = PokemonPagingSource(api)

        val page = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(null, page.nextKey)
    }

    @Test
    fun `io error becomes LoadResult Error`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } throws IOException("offline")
        val source = PokemonPagingSource(api)

        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

        assertEquals(PagingSource.LoadResult.Error::class, result::class)
    }
}
