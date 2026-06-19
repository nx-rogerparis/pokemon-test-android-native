package com.rogerparis.pokedex.data.local

import androidx.room.Room
import app.cash.turbine.test
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
class FavoriteDaoTest {
    private lateinit var db: PokedexDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            PokedexDatabase::class.java,
        ).build()
        dao = db.favoriteDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(id: Int) =
        FavoriteEntity(id = id, name = "p$id", artworkUrl = "u$id", types = listOf("grass"))

    @Test
    fun `add then observeAll emits the favorite`() = runTest {
        dao.add(entity(1))
        dao.observeAll().test {
            assertEquals(listOf(entity(1)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeIsFavorite reflects add and remove`() = runTest {
        dao.observeIsFavorite(1).test {
            assertEquals(false, awaitItem())
            dao.add(entity(1))
            assertEquals(true, awaitItem())
            dao.remove(1)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
