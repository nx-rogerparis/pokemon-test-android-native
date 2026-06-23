package com.rogerparis.pokedex.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: PokemonRepository,
) : ViewModel() {
    val favorites: StateFlow<List<PokemonListEntry>> =
        repository.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun primaryTypeOf(id: Int): String? = repository.primaryType(id)
}
