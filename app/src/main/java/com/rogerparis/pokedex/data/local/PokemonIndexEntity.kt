package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon_index")
data class PokemonIndexEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
)
