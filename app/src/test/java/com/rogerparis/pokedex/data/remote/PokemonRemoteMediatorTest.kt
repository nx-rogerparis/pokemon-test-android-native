package com.rogerparis.pokedex.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonEntity
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PokemonRemoteMediatorTest {
    private val api = mockk<PokeApi>()
    private lateinit var db: PokedexDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PokedexDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun page(range: IntRange, next: String?) = PokemonListResponse(
        count = 1000,
        next = next,
        previous = null,
        results = range.map { PokemonListItemDto(name = "p$it", url = "https://pokeapi.co/api/v2/pokemon/$it/") },
    )

    private fun emptyState() = PagingState<Int, PokemonEntity>(emptyList(), null, PagingConfig(20), 0)

    private fun mediator() = PokemonRemoteMediator(api, db, db.pokemonDao())

    @Test
    fun `refresh inserts first page and is not end of pagination`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns
            page(1..20, next = "https://pokeapi.co/api/v2/pokemon?offset=20&limit=20")

        val result = mediator().load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(20, db.pokemonDao().count())
    }

    @Test
    fun `append continues offset from cached count and detects end`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } returns page(1..20, next = "x")
        coEvery { api.getPokemonList(limit = 20, offset = 20) } returns page(21..30, next = null)

        val mediator = mediator()
        mediator.load(LoadType.REFRESH, emptyState())
        val result = mediator.load(LoadType.APPEND, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(true, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(30, db.pokemonDao().count())
    }

    @Test
    fun `io error becomes MediatorResult Error`() = runTest {
        coEvery { api.getPokemonList(limit = 20, offset = 0) } throws IOException("offline")

        val result = mediator().load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
    }
}
