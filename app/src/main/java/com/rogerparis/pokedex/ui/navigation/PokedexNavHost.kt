package com.rogerparis.pokedex.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rogerparis.pokedex.ui.detail.PokemonDetailScreen
import com.rogerparis.pokedex.ui.list.PokemonListScreen

private enum class TopDestination(val label: String) {
    LIST("Browse"),
    FAVORITES("Favorites"),
}

@Composable
fun PokedexNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = currentRoute?.contains("ListRoute") == true,
                onClick = { navController.navigate(ListRoute) },
                icon = {},
                label = { Text(TopDestination.LIST.label) },
            )
            item(
                selected = currentRoute?.contains("FavoritesRoute") == true,
                onClick = { navController.navigate(FavoritesRoute) },
                icon = {},
                label = { Text(TopDestination.FAVORITES.label) },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = ListRoute) {
            composable<ListRoute> {
                PokemonListScreen(onPokemonClick = { id -> navController.navigate(DetailRoute(id)) })
            }
            composable<DetailRoute> {
                PokemonDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<FavoritesRoute> {
                Text("Favorites — coming in the next plan")
            }
        }
    }
}
