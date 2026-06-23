package com.rogerparis.pokedex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.model.PokemonListEntry
import com.rogerparis.pokedex.ui.navigation.pokemonArtworkTransition
import com.rogerparis.pokedex.ui.theme.cardBrush
import com.rogerparis.pokedex.ui.theme.typeColor

@Composable
fun PokemonCard(
    entry: PokemonListEntry,
    types: List<String>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val surface = MaterialTheme.colorScheme.surface
    val primaryType = types.firstOrNull() ?: ""
    val brush = remember(primaryType, surface) { cardBrush(primaryType, surface) }
    val accent = typeColor(primaryType)
    val nameColor = lerp(MaterialTheme.colorScheme.onSurface, accent, 0.30f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .clickable { onClick(entry.id) }
            .heightIn(min = 92.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#%03d".format(entry.id),
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
            )
            Text(
                text = entry.name.capitalize(Locale.current),
                color = nameColor,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (types.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    types.forEach { PokemonTypeChip(it) }
                }
            }
        }
        AsyncImage(
            model = entry.artworkUrl,
            contentDescription = entry.name,
            modifier = Modifier
                .pokemonArtworkTransition(entry.id)
                .size(84.dp),
        )
        if (trailing != null) trailing()
    }
}
