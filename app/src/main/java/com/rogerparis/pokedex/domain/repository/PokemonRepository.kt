package com.rogerparis.pokedex.domain.repository

import androidx.paging.PagingData
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import kotlinx.coroutines.flow.Flow

interface PokemonRepository {
    fun pokemonPager(): Flow<PagingData<PokemonListEntry>>
    suspend fun getPokemon(id: Int): ApiResult<Pokemon>
    fun observeFavorites(): Flow<List<PokemonListEntry>>
    fun isFavorite(id: Int): Flow<Boolean>
    suspend fun addFavorite(pokemon: Pokemon)
    suspend fun removeFavorite(id: Int)
    suspend fun ensureSearchIndex()
    fun searchPager(query: String): Flow<PagingData<PokemonListEntry>>
    fun observeTeam(): Flow<List<PokemonListEntry>>
    fun isInTeam(id: Int): Flow<Boolean>
    suspend fun addToTeam(pokemon: Pokemon): Boolean
    suspend fun removeFromTeam(id: Int)
    suspend fun moveTeamMember(id: Int, up: Boolean)
}
