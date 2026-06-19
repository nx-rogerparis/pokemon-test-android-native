package com.rogerparis.pokedex.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.rogerparis.pokedex.data.mapper.toDomain
import com.rogerparis.pokedex.data.remote.PokeApi
import com.rogerparis.pokedex.data.remote.PokemonPagingSource
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class DefaultPokemonRepository @Inject constructor(
    private val api: PokeApi,
) : PokemonRepository {

    override fun pokemonPager(): Flow<PagingData<PokemonListEntry>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { PokemonPagingSource(api) },
        ).flow

    override suspend fun getPokemon(id: Int): ApiResult<Pokemon> =
        try {
            ApiResult.Success(api.getPokemonDetail(id).toDomain())
        } catch (e: HttpException) {
            if (e.code() == 404) ApiResult.Error(AppError.NotFound)
            else ApiResult.Error(AppError.Unknown(e.message()))
        } catch (e: IOException) {
            ApiResult.Error(AppError.Network)
        }
}
