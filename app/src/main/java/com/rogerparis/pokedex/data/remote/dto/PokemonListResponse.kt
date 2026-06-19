package com.rogerparis.pokedex.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PokemonListResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<PokemonListItemDto>,
)

@Serializable
data class PokemonListItemDto(
    val name: String,
    val url: String,
)
