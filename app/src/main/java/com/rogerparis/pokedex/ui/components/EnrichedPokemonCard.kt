package com.rogerparis.pokedex.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.rogerparis.pokedex.domain.model.PokemonListEntry

/**
 * A [PokemonCard] that resolves its primary type lazily (per item, cancellable, cached upstream)
 * so list rows can colorize without the list endpoint carrying type data.
 */
@Composable
fun EnrichedPokemonCard(
    entry: PokemonListEntry,
    onClick: (Int) -> Unit,
    resolveType: suspend (Int) -> String?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val primaryType by produceState<String?>(initialValue = null, entry.id) {
        value = resolveType(entry.id)
    }
    PokemonCard(
        entry = entry,
        types = listOfNotNull(primaryType),
        onClick = onClick,
        modifier = modifier,
        trailing = trailing,
    )
}
