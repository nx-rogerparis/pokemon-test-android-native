package com.rogerparis.pokedex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.rogerparis.pokedex.ui.theme.typeColor

@Composable
fun PokemonTypeChip(type: String, modifier: Modifier = Modifier) {
    Text(
        text = type.capitalize(Locale.current),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(typeColor(type))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
