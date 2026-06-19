package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.PokemonEntity
import com.rogerparis.pokedex.data.remote.officialArtworkUrl
import com.rogerparis.pokedex.data.remote.pokemonIdFromUrl
import com.rogerparis.pokedex.data.remote.dto.PokemonDetailDto
import com.rogerparis.pokedex.data.remote.dto.PokemonListItemDto
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.model.Stat

fun PokemonListItemDto.toEntry(): PokemonListEntry {
    val id = pokemonIdFromUrl(url)
    return PokemonListEntry(id = id, name = name, artworkUrl = officialArtworkUrl(id))
}

fun PokemonDetailDto.toDomain(): Pokemon = Pokemon(
    id = id,
    name = name,
    heightDm = height,
    weightHg = weight,
    types = types.sortedBy { it.slot }.map { it.type.name },
    stats = stats.map { Stat(name = it.stat.name, baseValue = it.baseStat) },
    abilities = abilities.map { it.ability.name },
    artworkUrl = officialArtworkUrl(id),
)

fun PokemonEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
