package com.rogerparis.pokedex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteUrlsTest {
    @Test
    fun `extracts id from trailing path segment`() {
        assertEquals(1, pokemonIdFromUrl("https://pokeapi.co/api/v2/pokemon/1/"))
    }

    @Test
    fun `extracts id when url has no trailing slash`() {
        assertEquals(151, pokemonIdFromUrl("https://pokeapi.co/api/v2/pokemon/151"))
    }

    @Test
    fun `builds official artwork url from id`() {
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png",
            officialArtworkUrl(25),
        )
    }
}
