package com.rogerparis.pokedex.data.mapper

import com.rogerparis.pokedex.data.local.FavoriteEntity
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry

fun Pokemon.toFavoriteEntity(): FavoriteEntity =
    FavoriteEntity(id = id, name = name, artworkUrl = artworkUrl, types = types)

fun FavoriteEntity.toListEntry(): PokemonListEntry =
    PokemonListEntry(id = id, name = name, artworkUrl = artworkUrl)
