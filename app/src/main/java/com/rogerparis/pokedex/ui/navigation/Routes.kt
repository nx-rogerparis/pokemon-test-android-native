package com.rogerparis.pokedex.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Serializable
data class DetailRoute(val id: Int)

@Serializable
data object FavoritesRoute
