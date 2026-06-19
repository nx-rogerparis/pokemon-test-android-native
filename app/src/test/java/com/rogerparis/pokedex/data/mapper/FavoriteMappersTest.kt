package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.Stat
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteMappersTest {
    private fun pikachu() = Pokemon(
        id = 25, name = "pikachu", heightDm = 4, weightHg = 60,
        types = listOf("electric"), stats = listOf(Stat("hp", 35)),
        abilities = listOf("static"), artworkUrl = "url25",
    )

    @Test
    fun `pokemon maps to favorite entity snapshot`() {
        val entity = pikachu().toFavoriteEntity()
        assertEquals(
            FavoriteEntity(
                id = 25, name = "pikachu", artworkUrl = "url25",
                types = listOf("electric"), heightDm = 4, weightHg = 60,
                abilities = listOf("static"), stats = listOf(Stat("hp", 35)),
            ),
            entity,
        )
    }

    @Test
    fun `pokemon survives entity round-trip`() {
        assertEquals(pikachu(), pikachu().toFavoriteEntity().toPokemon())
    }

    @Test
    fun `favorite entity maps to list entry`() {
        val entity = FavoriteEntity(
            id = 25, name = "pikachu", artworkUrl = "url25", types = listOf("electric"),
            heightDm = 4, weightHg = 60, abilities = listOf("static"), stats = listOf(Stat("hp", 35)),
        )
        val entry = entity.toListEntry()
        assertEquals(25, entry.id)
        assertEquals("pikachu", entry.name)
        assertEquals("url25", entry.artworkUrl)
    }
}
