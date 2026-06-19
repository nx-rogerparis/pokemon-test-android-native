package com.rogerparis.pokedex.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonDetailDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses id name height weight and ignores unknown fields`() {
        val raw = """
            {"id":1,"name":"bulbasaur","height":7,"weight":69,
             "base_experience":64,
             "types":[{"slot":1,"type":{"name":"grass","url":"u"}}],
             "stats":[{"base_stat":45,"stat":{"name":"hp","url":"u"}}],
             "abilities":[{"ability":{"name":"overgrow","url":"u"},"is_hidden":false,"slot":1}]}
        """.trimIndent()

        val dto = json.decodeFromString<PokemonDetailDto>(raw)

        assertEquals(1, dto.id)
        assertEquals("bulbasaur", dto.name)
        assertEquals(7, dto.height)
        assertEquals(69, dto.weight)
        assertEquals("grass", dto.types.first().type.name)
        assertEquals(45, dto.stats.first().baseStat)
        assertEquals("overgrow", dto.abilities.first().ability.name)
        assertEquals(false, dto.abilities.first().isHidden)
    }
}
