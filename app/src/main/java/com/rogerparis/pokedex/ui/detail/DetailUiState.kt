package com.rogerparis.pokedex.ui.detail

import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val pokemon: Pokemon) : DetailUiState
    data class Error(val error: AppError) : DetailUiState
}
