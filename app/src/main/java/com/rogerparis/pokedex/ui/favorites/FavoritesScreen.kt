package com.rogerparis.pokedex.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerparis.pokedex.ui.components.EmptyState
import com.rogerparis.pokedex.ui.components.EnrichedPokemonCard

@Composable
fun FavoritesScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        EmptyState("No favorites yet. Tap the heart on a Pokémon.", modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        items(items = favorites, key = { it.id }) { entry ->
            EnrichedPokemonCard(
                entry = entry,
                onClick = onPokemonClick,
                resolveType = viewModel::primaryTypeOf,
                modifier = Modifier.animateItem(),
            )
        }
    }
}
