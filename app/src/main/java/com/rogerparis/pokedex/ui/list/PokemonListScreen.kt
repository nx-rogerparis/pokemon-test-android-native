package com.rogerparis.pokedex.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.rogerparis.pokedex.ui.components.EmptyState
import com.rogerparis.pokedex.ui.components.EnrichedPokemonCard
import com.rogerparis.pokedex.ui.components.ErrorState
import com.rogerparis.pokedex.ui.components.LoadingState

@Composable
fun PokemonListScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonListViewModel = hiltViewModel(),
) {
    val items = viewModel.pokemon.collectAsLazyPagingItems()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search Pokémon") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        when (items.loadState.refresh) {
            is LoadState.Loading -> LoadingState()
            is LoadState.Error -> ErrorState(
                message = "Couldn't load Pokémon. Check your connection.",
                onRetry = items::retry,
            )
            else -> if (items.itemCount == 0 && query.isNotBlank()) {
                EmptyState("No Pokémon match \"$query\".")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val entry = items[index] ?: return@items
                        EnrichedPokemonCard(
                            entry = entry,
                            onClick = onPokemonClick,
                            resolveType = viewModel::primaryTypeOf,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    when (items.loadState.append) {
                        is LoadState.Loading -> item { LoadingFooter() }
                        is LoadState.Error -> item { ErrorFooter(onRetry = items::retry) }
                        else -> Unit
                    }
                }
            }
        }
    }
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
