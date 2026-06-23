package com.rogerparis.pokedex.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerparis.pokedex.ui.components.EmptyState
import com.rogerparis.pokedex.ui.components.EnrichedPokemonCard

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        itemsIndexed(items = team, key = { _, entry -> entry.id }) { index, entry ->
            EnrichedPokemonCard(
                entry = entry,
                onClick = onPokemonClick,
                resolveType = viewModel::primaryTypeOf,
                trailing = {
                    Row {
                        IconButton(onClick = { viewModel.moveUp(entry.id) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { viewModel.moveDown(entry.id) }, enabled = index < team.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = { viewModel.remove(entry.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from team")
                        }
                    }
                },
            )
        }
    }
}
