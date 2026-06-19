package com.rogerparis.pokedex.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rogerparis.pokedex.data.mapper.toEntry
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import retrofit2.HttpException
import java.io.IOException

private const val PAGE_SIZE = 20

class PokemonPagingSource(
    private val api: PokeApi,
) : PagingSource<Int, PokemonListEntry>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PokemonListEntry> {
        val offset = params.key ?: 0
        return try {
            val response = api.getPokemonList(limit = PAGE_SIZE, offset = offset)
            LoadResult.Page(
                data = response.results.map { it.toEntry() },
                prevKey = if (offset == 0) null else offset - PAGE_SIZE,
                nextKey = if (response.next == null) null else offset + PAGE_SIZE,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, PokemonListEntry>): Int? =
        state.anchorPosition?.let { anchor ->
            val closest = state.closestPageToPosition(anchor)
            closest?.prevKey?.plus(PAGE_SIZE) ?: closest?.nextKey?.minus(PAGE_SIZE)
        }
}
