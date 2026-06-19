package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.remote.dto.AbilitySlotDto
import com.rogerparis.pokedex.data.remote.dto.NamedApiResourceDto
import com.rogerparis.pokedex.data.remote.dto.PokemonDetailDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.data.remote.dto.StatSlotDto
import com.rogerparis.pokedex.data.remote.dto.TypeSlotDto
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonMappersTest {
    @Test
    fun `list item maps to entry with derived id and artwork`() {
        val dto = PokemonListItemDto(name = "pikachu", url = "https://pokeapi.co/api/v2/pokemon/25/")
        val entry = dto.toEntry()
        assertEquals(25, entry.id)
        assertEquals("pikachu", entry.name)
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png",
            entry.artworkUrl,
        )
    }

    @Test
    fun `detail maps to domain with types ordered by slot`() {
        val dto = PokemonDetailDto(
            id = 6, name = "charizard", height = 17, weight = 905,
            types = listOf(
                TypeSlotDto(slot = 2, type = NamedApiResourceDto("flying", "u")),
                TypeSlotDto(slot = 1, type = NamedApiResourceDto("fire", "u")),
            ),
            stats = listOf(StatSlotDto(baseStat = 78, stat = NamedApiResourceDto("hp", "u"))),
            abilities = listOf(AbilitySlotDto(NamedApiResourceDto("blaze", "u"), isHidden = false, slot = 1)),
        )
        val domain = dto.toDomain()
        assertEquals(6, domain.id)
        assertEquals(listOf("fire", "flying"), domain.types)
        assertEquals("hp", domain.stats.first().name)
        assertEquals(78, domain.stats.first().baseValue)
        assertEquals(listOf("blaze"), domain.abilities)
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/6.png",
            domain.artworkUrl,
        )
    }
}
