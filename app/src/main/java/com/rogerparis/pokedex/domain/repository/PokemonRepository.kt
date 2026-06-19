package com.rogerparis.pokedex.domain.repository

import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.model.Pokemon

interface PokemonRepository {
    suspend fun getPokemon(id: Int): ApiResult<Pokemon>
}
