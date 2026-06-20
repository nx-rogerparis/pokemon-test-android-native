package com.rogerparis.pokedex.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PokemonIndexDaoTest {
    private lateinit var db: PokedexDatabase
    private lateinit var dao: PokemonIndexDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PokedexDatabase::class.java).build()
        dao = db.pokemonIndexDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `search matches by substring case-insensitively, ordered by id`() = runTest {
        dao.insertAll(
            listOf(
                PokemonIndexEntity(1, "bulbasaur", "u1"),
                PokemonIndexEntity(4, "charmander", "u4"),
                PokemonIndexEntity(6, "charizard", "u6"),
            ),
        )

        val result = dao.search("char").load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(4, 6), page.data.map { it.id })
    }
}
