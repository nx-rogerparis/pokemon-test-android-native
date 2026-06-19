package com.rogerparis.pokedex.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PokemonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PokemonEntity>)

    @Query("DELETE FROM pokemon")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun count(): Int

    @Query("SELECT * FROM pokemon ORDER BY position")
    fun pagingSource(): PagingSource<Int, PokemonEntity>
}
