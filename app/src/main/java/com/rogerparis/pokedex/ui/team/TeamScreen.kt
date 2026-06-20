package com.rogerparis.pokedex.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.ui.components.EmptyState

@Composable
fun TeamScreen(
    onPokemonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val team by viewModel.team.collectAsStateWithLifecycle()

    if (team.isEmpty()) {
        EmptyState("Your team is empty. Add up to 6 from a Pokémon's page.", modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items = team, key = { _, entry -> entry.id }) { index, entry ->
            TeamRow(
                entry = entry,
                isFirst = index == 0,
                isLast = index == team.lastIndex,
                onClick = onPokemonClick,
                onMoveUp = viewModel::moveUp,
                onMoveDown = viewModel::moveDown,
                onRemove = viewModel::remove,
            )
        }
    }
}

@Composable
private fun TeamRow(
    entry: PokemonListEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
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
        trailingContent = {
            Row {
                IconButton(onClick = { onMoveUp(entry.id) }, enabled = !isFirst) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMoveDown(entry.id) }, enabled = !isLast) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = { onRemove(entry.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove from team")
                }
            }
        },
    )
}
