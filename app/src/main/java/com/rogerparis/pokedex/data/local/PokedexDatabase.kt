package com.rogerparis.pokedex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [FavoriteEntity::class, PokemonEntity::class, PokemonIndexEntity::class, TeamMemberEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pokemonDao(): PokemonDao
    abstract fun pokemonIndexDao(): PokemonIndexDao
    abstract fun teamMemberDao(): TeamMemberDao
}
