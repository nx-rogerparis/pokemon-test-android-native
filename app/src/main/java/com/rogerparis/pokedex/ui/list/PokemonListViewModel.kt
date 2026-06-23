package com.rogerparis.pokedex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PokemonListViewModel @Inject constructor(
    private val repository: PokemonRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    val pokemon: Flow<PagingData<PokemonListEntry>> =
        _query
            .debounce { q -> if (q.isBlank()) 0L else 300L }
            .flatMapLatest { q ->
                if (q.isBlank()) {
                    repository.pokemonPager()
                } else {
                    repository.ensureSearchIndex()
                    repository.searchPager(q.trim())
                }
            }
            .cachedIn(viewModelScope)

    suspend fun primaryTypeOf(id: Int): String? = repository.primaryType(id)
}
