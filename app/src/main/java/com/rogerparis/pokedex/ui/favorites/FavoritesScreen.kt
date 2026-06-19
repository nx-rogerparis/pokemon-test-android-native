package com.rogerparis.pokedex.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
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
import com.rogerparis.pokedex.domain.model.PokemonListEntry

@Composable
fun FavoritesScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No favorites yet. Tap the heart on a Pokémon.")
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items = favorites, key = { it.id }) { entry ->
            FavoriteRow(entry = entry, onClick = onPokemonClick)
        }
    }
}

@Composable
private fun FavoriteRow(entry: PokemonListEntry, onClick: (Int) -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(entry.id) },
        leadingContent = {
            AsyncImage(
                model = entry.artworkUrl,
                contentDescription = entry.name,
                modifier = Modifier.size(56.dp),
            )
        },
        headlineContent = { Text(entry.name.capitalize(Locale.current)) },
    )
}
