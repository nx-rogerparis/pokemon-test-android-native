package com.rogerparis.pokedex.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonDao
import com.rogerparis.pokedex.data.local.PokemonEntity
import com.rogerparis.pokedex.data.mapper.toEntry
import retrofit2.HttpException
import java.io.IOException

private const val PAGE_SIZE = 20

@OptIn(ExperimentalPagingApi::class)
class PokemonRemoteMediator(
    private val api: PokeApi,
    private val database: PokedexDatabase,
    private val pokemonDao: PokemonDao,
) : RemoteMediator<Int, PokemonEntity>() {

    override suspend fun initialize(): InitializeAction =
        if (pokemonDao.count() > 0) InitializeAction.SKIP_INITIAL_REFRESH
        else InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PokemonEntity>,
    ): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> pokemonDao.count()
        }
        return try {
            val response = api.getPokemonList(limit = PAGE_SIZE, offset = offset)
            val entities = response.results.mapIndexed { index, dto ->
                val entry = dto.toEntry()
                PokemonEntity(
                    id = entry.id,
                    name = entry.name,
                    artworkUrl = entry.artworkUrl,
                    position = offset + index,
                )
            }
            database.withTransaction {
                if (loadType == LoadType.REFRESH) pokemonDao.clearAll()
                pokemonDao.insertAll(entities)
            }
            MediatorResult.Success(endOfPaginationReached = response.next == null)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }
}
