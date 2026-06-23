package com.rogerparis.pokedex.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks the Pokémon artwork as a shared element keyed by id. No-op outside a SharedTransitionLayout. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.pokemonArtworkTransition(id: Int): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@pokemonArtworkTransition.sharedElement(
            rememberSharedContentState(key = "art-$id"),
            animatedVisibilityScope = animatedScope,
        )
    }
}
