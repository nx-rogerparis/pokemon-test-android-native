package com.rogerparis.pokedex.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PokemonDetailDto(
    val id: Int,
    val name: String,
    val height: Int,
    val weight: Int,
    val types: List<TypeSlotDto>,
    val stats: List<StatSlotDto>,
    val abilities: List<AbilitySlotDto>,
)

@Serializable
data class TypeSlotDto(
    val slot: Int,
    val type: NamedApiResourceDto,
)

@Serializable
data class StatSlotDto(
    @SerialName("base_stat") val baseStat: Int,
    val stat: NamedApiResourceDto,
)

@Serializable
data class AbilitySlotDto(
    val ability: NamedApiResourceDto,
    @SerialName("is_hidden") val isHidden: Boolean,
    val slot: Int,
)

@Serializable
data class NamedApiResourceDto(
    val name: String,
    val url: String,
)
