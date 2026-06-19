package com.rogerparis.pokedex.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.ui.components.ErrorState
import com.rogerparis.pokedex.ui.components.LoadingState
import com.rogerparis.pokedex.ui.components.PokemonTypeChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (state as? DetailUiState.Success)
                            ?.pokemon?.name?.capitalize(Locale.current) ?: "Pokémon",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is DetailUiState.Loading -> LoadingState()
                is DetailUiState.Error -> ErrorState(s.error.toMessage(), onRetry = viewModel::retry)
                is DetailUiState.Success -> PokemonDetail(s.pokemon)
            }
        }
    }
}

@Composable
private fun PokemonDetail(pokemon: Pokemon) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = pokemon.artworkUrl,
            contentDescription = pokemon.name,
            modifier = Modifier.size(200.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pokemon.types.forEach { type -> PokemonTypeChip(type) }
        }
        Text("Height: ${pokemon.heightDm / 10.0} m   Weight: ${pokemon.weightHg / 10.0} kg")
        Text("Abilities: " + pokemon.abilities.joinToString { it.capitalize(Locale.current) })
        pokemon.stats.forEach { stat ->
            Text("${stat.name.capitalize(Locale.current)}: ${stat.baseValue}")
        }
    }
}

private fun AppError.toMessage(): String = when (this) {
    AppError.Network -> "No connection. Check your network."
    AppError.NotFound -> "Pokémon not found."
    is AppError.Unknown -> "Something went wrong."
}
