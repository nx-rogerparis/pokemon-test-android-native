package com.rogerparis.pokedex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class PokemonListViewModel @Inject constructor(
    repository: PokemonRepository,
) : ViewModel() {
    val pokemon: Flow<PagingData<PokemonListEntry>> =
        repository.pokemonPager().cachedIn(viewModelScope)
}
