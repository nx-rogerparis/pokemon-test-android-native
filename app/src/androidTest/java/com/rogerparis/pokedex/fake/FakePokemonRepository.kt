package com.rogerparis.pokedex.fake

import androidx.paging.PagingData
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.model.Stat
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePokemonRepository @Inject constructor() : PokemonRepository {

    val browseEntries = listOf(
        PokemonListEntry(1, "bulbasaur", "https://img/1.png"),
        PokemonListEntry(4, "charmander", "https://img/4.png"),
        PokemonListEntry(7, "squirtle", "https://img/7.png"),
    )

    private val favorites = MutableStateFlow<List<PokemonListEntry>>(emptyList())
    private val favoriteIds = MutableStateFlow<Set<Int>>(emptySet())
    private val team = MutableStateFlow<List<PokemonListEntry>>(emptyList())

    private fun pokemon(id: Int) = Pokemon(
        id = id,
        name = browseEntries.first { it.id == id }.name,
        heightDm = 7,
        weightHg = 69,
        types = listOf("grass"),
        stats = listOf(Stat("hp", 45)),
        abilities = listOf("overgrow"),
        artworkUrl = "https://img/$id.png",
    )

    override fun pokemonPager(): Flow<PagingData<PokemonListEntry>> =
        flowOf(PagingData.from(browseEntries))

    override suspend fun getPokemon(id: Int): ApiResult<Pokemon> =
        ApiResult.Success(pokemon(id))

    override fun observeFavorites(): Flow<List<PokemonListEntry>> = favorites

    override fun isFavorite(id: Int): Flow<Boolean> = favoriteIds.map { id in it }

    override suspend fun addFavorite(pokemon: Pokemon) {
        favoriteIds.value = favoriteIds.value + pokemon.id
        favorites.value = favorites.value + PokemonListEntry(pokemon.id, pokemon.name, pokemon.artworkUrl)
    }

    override suspend fun removeFavorite(id: Int) {
        favoriteIds.value = favoriteIds.value - id
        favorites.value = favorites.value.filterNot { it.id == id }
    }

    override suspend fun ensureSearchIndex() = Unit

    override fun searchPager(query: String): Flow<PagingData<PokemonListEntry>> =
        flowOf(PagingData.from(browseEntries.filter { it.name.contains(query, ignoreCase = true) }))

    override fun observeTeam(): Flow<List<PokemonListEntry>> = team

    override fun isInTeam(id: Int): Flow<Boolean> = team.map { list -> list.any { it.id == id } }

    override suspend fun addToTeam(pokemon: Pokemon): Boolean {
        team.value = team.value + PokemonListEntry(pokemon.id, pokemon.name, pokemon.artworkUrl)
        return true
    }

    override suspend fun removeFromTeam(id: Int) {
        team.value = team.value.filterNot { it.id == id }
    }

    override suspend fun moveTeamMember(id: Int, up: Boolean) = Unit
}
