package com.rogerparis.pokedex.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rogerparis.pokedex.domain.model.Stat

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val types: List<String>,
    @ColumnInfo(defaultValue = "0") val heightDm: Int,
    @ColumnInfo(defaultValue = "0") val weightHg: Int,
    @ColumnInfo(defaultValue = "[]") val abilities: List<String>,
    @ColumnInfo(defaultValue = "[]") val stats: List<Stat>,
)
