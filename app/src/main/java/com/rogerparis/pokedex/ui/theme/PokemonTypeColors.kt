package com.rogerparis.pokedex.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val typeColors: Map<String, Color> = mapOf(
    "normal" to Color(0xFFA8A77A),
    "fire" to Color(0xFFEE8130),
    "water" to Color(0xFF6390F0),
    "electric" to Color(0xFFF7D02C),
    "grass" to Color(0xFF7AC74C),
    "ice" to Color(0xFF96D9D6),
    "fighting" to Color(0xFFC22E28),
    "poison" to Color(0xFFA33EA1),
    "ground" to Color(0xFFE2BF65),
    "flying" to Color(0xFFA98FF3),
    "psychic" to Color(0xFFF95587),
    "bug" to Color(0xFFA6B91A),
    "rock" to Color(0xFFB6A136),
    "ghost" to Color(0xFF735797),
    "dragon" to Color(0xFF6F35FC),
    "dark" to Color(0xFF705746),
    "steel" to Color(0xFFB7B7CE),
    "fairy" to Color(0xFFD685AD),
)

private val fallbackTypeColor = Color(0xFF777777)

fun typeColor(type: String): Color = typeColors[type] ?: fallbackTypeColor

/** Soft left→right tint used on list cards. Fades the type colour into the surface. */
fun cardBrush(primaryType: String, surface: Color): Brush =
    Brush.horizontalGradient(
        0f to lerp(surface, typeColor(primaryType), 0.38f),
        0.78f to surface,
        1f to surface,
    )

/** Diagonal hero gradient: primary type → second type (or a darkened primary). */
fun heroBrush(types: List<String>): Brush {
    val start = typeColor(types.firstOrNull() ?: "")
    val end = if (types.size > 1) typeColor(types[1]) else lerp(start, Color.Black, 0.40f)
    return Brush.linearGradient(listOf(start, end))
}
