package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rogerparis.pokedex.domain.model.Stat

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val types: List<String>,
    val heightDm: Int,
    val weightHg: Int,
    val abilities: List<String>,
    val stats: List<Stat>,
)
