package com.rogerparis.pokedex.domain.repository

import androidx.paging.PagingData
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import kotlinx.coroutines.flow.Flow

interface PokemonRepository {
    fun pokemonPager(): Flow<PagingData<PokemonListEntry>>
    suspend fun getPokemon(id: Int): ApiResult<Pokemon>
}
