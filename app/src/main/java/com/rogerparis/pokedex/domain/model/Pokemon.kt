package com.rogerparis.pokedex.domain.model

data class PokemonListEntry(
    val id: Int,
    val name: String,
    val artworkUrl: String,
)

data class Pokemon(
    val id: Int,
    val name: String,
    val heightDm: Int,
    val weightHg: Int,
    val types: List<String>,
    val stats: List<Stat>,
    val abilities: List<String>,
    val artworkUrl: String,
)

data class Stat(
    val name: String,
    val baseValue: Int,
)
