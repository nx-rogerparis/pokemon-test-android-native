package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.Stat
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteMappersTest {
    @Test
    fun `pokemon maps to favorite entity snapshot`() {
        val pokemon = Pokemon(
            id = 25, name = "pikachu", heightDm = 4, weightHg = 60,
            types = listOf("electric"), stats = listOf(Stat("hp", 35)),
            abilities = listOf("static"), artworkUrl = "url25",
        )
        val entity = pokemon.toFavoriteEntity()
        assertEquals(
            FavoriteEntity(id = 25, name = "pikachu", artworkUrl = "url25", types = listOf("electric")),
            entity,
        )
    }

    @Test
    fun `favorite entity maps to list entry`() {
        val entity = FavoriteEntity(id = 25, name = "pikachu", artworkUrl = "url25", types = listOf("electric"))
        val entry = entity.toListEntry()
        assertEquals(25, entry.id)
        assertEquals("pikachu", entry.name)
        assertEquals("url25", entry.artworkUrl)
    }
}
