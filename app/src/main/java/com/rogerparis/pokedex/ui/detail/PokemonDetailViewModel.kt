package com.rogerparis.pokedex.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PokemonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PokemonRepository,
) : ViewModel() {

    private val pokemonId: Int = checkNotNull(savedStateHandle["id"]) { "DetailRoute requires an id" }

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> =
        repository.isFavorite(pokemonId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        load()
    }

    fun retry() = load()

    fun toggleFavorite() {
        val current = state.value
        if (current !is DetailUiState.Success) return
        viewModelScope.launch {
            if (isFavorite.value) repository.removeFavorite(pokemonId)
            else repository.addFavorite(current.pokemon)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = when (val result = repository.getPokemon(pokemonId)) {
                is ApiResult.Success -> DetailUiState.Success(result.data)
                is ApiResult.Error -> DetailUiState.Error(result.error)
            }
        }
    }
}
