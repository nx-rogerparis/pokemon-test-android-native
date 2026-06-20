package com.rogerparis.pokedex.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PokemonIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PokemonIndexEntity>)

    @Query("SELECT COUNT(*) FROM pokemon_index")
    suspend fun count(): Int

    @Query("SELECT * FROM pokemon_index WHERE name LIKE '%' || :query || '%' ORDER BY id")
    fun search(query: String): PagingSource<Int, PokemonIndexEntity>
}
