package com.rogerparis.pokedex.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.model.PokemonListEntry

@Composable
fun PokemonListScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonListViewModel = hiltViewModel(),
) {
    val items = viewModel.pokemon.collectAsLazyPagingItems()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
            val entry = items[index] ?: return@items
            PokemonRow(entry = entry, onClick = onPokemonClick)
        }
        when (items.loadState.append) {
            is LoadState.Loading -> item { LoadingFooter() }
            is LoadState.Error -> item { ErrorFooter(onRetry = items::retry) }
            else -> Unit
        }
    }
}

@Composable
private fun PokemonRow(entry: PokemonListEntry, onClick: (Int) -> Unit) {
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

@Composable
private fun LoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorFooter(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Couldn't load more. Tap to retry.",
            modifier = Modifier.clickable { onRetry() },
        )
    }
}
