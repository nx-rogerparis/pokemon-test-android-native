package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry

fun Pokemon.toFavoriteEntity(): FavoriteEntity =
    FavoriteEntity(
        id = id,
        name = name,
        artworkUrl = artworkUrl,
        types = types,
        heightDm = heightDm,
        weightHg = weightHg,
        abilities = abilities,
        stats = stats,
    )

fun FavoriteEntity.toPokemon(): Pokemon =
    Pokemon(
        id = id,
        name = name,
        heightDm = heightDm,
        weightHg = weightHg,
        types = types,
        stats = stats,
        abilities = abilities,
        artworkUrl = artworkUrl,
    )

fun FavoriteEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
