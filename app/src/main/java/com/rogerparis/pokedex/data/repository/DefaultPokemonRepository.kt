package com.rogerparis.pokedex.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.rogerparis.pokedex.data.local.FavoriteDao
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonDao
import com.rogerparis.pokedex.data.local.PokemonIndexDao
import com.rogerparis.pokedex.data.local.PokemonIndexEntity
import com.rogerparis.pokedex.data.mapper.toDomain
import com.rogerparis.pokedex.data.mapper.toEntry
import com.rogerparis.pokedex.data.mapper.toFavoriteEntity
import com.rogerparis.pokedex.data.mapper.toListEntry
import com.rogerparis.pokedex.data.mapper.toPokemon
import com.rogerparis.pokedex.data.remote.PokeApi
import com.rogerparis.pokedex.data.remote.PokemonRemoteMediator
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class DefaultPokemonRepository @Inject constructor(
    private val api: PokeApi,
    private val favoriteDao: FavoriteDao,
    private val database: PokedexDatabase,
    private val pokemonDao: PokemonDao,
    private val pokemonIndexDao: PokemonIndexDao,
) : PokemonRepository {

    @OptIn(ExperimentalPagingApi::class)
    override fun pokemonPager(): Flow<PagingData<PokemonListEntry>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            remoteMediator = PokemonRemoteMediator(api, database, pokemonDao),
            pagingSourceFactory = { pokemonDao.pagingSource() },
        ).flow.map { pagingData -> pagingData.map { entity -> entity.toListEntry() } }

    override fun observeFavorites(): Flow<List<PokemonListEntry>> =
        favoriteDao.observeAll().map { list -> list.map { it.toListEntry() } }

    override fun isFavorite(id: Int): Flow<Boolean> = favoriteDao.observeIsFavorite(id)

    override suspend fun addFavorite(pokemon: Pokemon) = favoriteDao.add(pokemon.toFavoriteEntity())

    override suspend fun removeFavorite(id: Int) = favoriteDao.remove(id)

    override suspend fun ensureSearchIndex() {
        if (pokemonIndexDao.count() > 0) return
        try {
            val response = api.getPokemonList(limit = 100_000, offset = 0)
            pokemonIndexDao.insertAll(
                response.results.map {
                    val entry = it.toEntry()
                    PokemonIndexEntity(id = entry.id, name = entry.name, artworkUrl = entry.artworkUrl)
                },
            )
        } catch (e: IOException) {
            // best-effort: offline search is unavailable until the index is populated
        } catch (e: HttpException) {
            // best-effort
        }
    }

    override fun searchPager(query: String): Flow<PagingData<PokemonListEntry>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { pokemonIndexDao.search(query) },
        ).flow.map { pagingData -> pagingData.map { entity -> entity.toListEntry() } }

    override suspend fun getPokemon(id: Int): ApiResult<Pokemon> =
        try {
            ApiResult.Success(api.getPokemonDetail(id).toDomain())
        } catch (e: HttpException) {
            if (e.code() == 404) ApiResult.Error(AppError.NotFound)
            else ApiResult.Error(AppError.Unknown(e.message()))
        } catch (e: IOException) {
            val snapshot = favoriteDao.getById(id)
            if (snapshot != null) ApiResult.Success(snapshot.toPokemon())
            else ApiResult.Error(AppError.Network)
        }
}
