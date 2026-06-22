package com.rogerparis.pokedex.ui.navigation

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rogerparis.pokedex.ui.detail.PokemonDetailScreen
import com.rogerparis.pokedex.ui.favorites.FavoritesScreen
import com.rogerparis.pokedex.ui.list.PokemonListScreen
import com.rogerparis.pokedex.ui.team.TeamScreen

private enum class TopDestination(val label: String) {
    LIST("Browse"),
    FAVORITES("Favorites"),
    TEAM("Team"),
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
            item(
                selected = currentRoute?.contains("TeamRoute") == true,
                onClick = { navController.navigate(TeamRoute) },
                icon = {},
                label = { Text(TopDestination.TEAM.label) },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = ListRoute) {
            composable<ListRoute> {
                PokemonListScreen(
                    onPokemonClick = { id -> navController.navigate(DetailRoute(id)) },
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            composable<DetailRoute> {
                PokemonDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<FavoritesRoute> {
                FavoritesScreen(
                    onPokemonClick = { id -> navController.navigate(DetailRoute(id)) },
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            composable<TeamRoute> {
                TeamScreen(
                    onPokemonClick = { id -> navController.navigate(DetailRoute(id)) },
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }
    }
}
