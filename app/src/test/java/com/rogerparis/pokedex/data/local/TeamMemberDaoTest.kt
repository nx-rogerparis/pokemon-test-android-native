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
class TeamMemberDaoTest {
    private lateinit var db: PokedexDatabase
    private lateinit var dao: TeamMemberDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PokedexDatabase::class.java).build()
        dao = db.teamMemberDao()
    }

    @After
    fun tearDown() = db.close()

    private fun member(id: Int, position: Int) =
        TeamMemberEntity(id = id, name = "p$id", artworkUrl = "u$id", position = position)

    @Test
    fun `observeAll returns members ordered by position`() = runTest {
        dao.upsert(member(2, position = 1))
        dao.upsert(member(1, position = 0))
        dao.observeAll().test {
            assertEquals(listOf(1, 2), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeIsMember reflects add and remove`() = runTest {
        dao.observeIsMember(1).test {
            assertEquals(false, awaitItem())
            dao.upsert(member(1, position = 0))
            assertEquals(true, awaitItem())
            dao.remove(1)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
