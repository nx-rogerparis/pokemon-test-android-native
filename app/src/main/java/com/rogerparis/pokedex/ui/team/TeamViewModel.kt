package com.rogerparis.pokedex.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val repository: PokemonRepository,
) : ViewModel() {
    val team: StateFlow<List<PokemonListEntry>> =
        repository.observeTeam()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun primaryTypeOf(id: Int): String? = repository.primaryType(id)

    fun moveUp(id: Int) = move(id, up = true)

    fun moveDown(id: Int) = move(id, up = false)

    fun remove(id: Int) {
        viewModelScope.launch { repository.removeFromTeam(id) }
    }

    private fun move(id: Int, up: Boolean) {
        viewModelScope.launch { repository.moveTeamMember(id, up) }
    }
}
