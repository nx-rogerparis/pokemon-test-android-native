package com.rogerparis.pokedex.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.ui.components.ErrorState
import com.rogerparis.pokedex.ui.components.LoadingState
import com.rogerparis.pokedex.ui.components.PokemonTypeChip
import com.rogerparis.pokedex.ui.components.StatBar
import com.rogerparis.pokedex.ui.theme.heroBrush
import com.rogerparis.pokedex.ui.theme.typeColor

@Composable
fun PokemonDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isInTeam by viewModel.isInTeam.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is DetailUiState.Loading -> LoadingState()
                is DetailUiState.Error -> ErrorState(s.error.toMessage(), onRetry = viewModel::retry)
                is DetailUiState.Success -> PokemonDetailContent(s.pokemon)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Row {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = viewModel::toggleTeam) {
                        Icon(
                            imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Outlined.Groups,
                            contentDescription = if (isInTeam) "Remove from team" else "Add to team",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PokemonDetailContent(pokemon: Pokemon) {
    val accent = typeColor(pokemon.types.firstOrNull() ?: "")
    val brush = remember(pokemon.id) { heroBrush(pokemon.types) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(brush),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = pokemon.artworkUrl,
                contentDescription = pokemon.name,
                modifier = Modifier.size(200.dp).padding(top = 24.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-24).dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                )
                .padding(20.dp),
        ) {
            Text(
                "#%03d".format(pokemon.id),
                color = lerp(MaterialTheme.colorScheme.onSurface, accent, 0.7f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                pokemon.name.capitalize(Locale.current),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                pokemon.types.forEach { PokemonTypeChip(it) }
            }
            Text(
                "Base stats",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            pokemon.stats.forEach { stat ->
                StatBar(label = stat.name.capitalize(Locale.current), value = stat.baseValue, accent = accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                StatPill(label = "Height", value = "${pokemon.heightDm / 10.0} m", modifier = Modifier.weight(1f))
                StatPill(label = "Weight", value = "${pokemon.weightHg / 10.0} kg", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun AppError.toMessage(): String = when (this) {
    AppError.Network -> "No connection. Check your network."
    AppError.NotFound -> "Pokémon not found."
    is AppError.Unknown -> "Something went wrong."
}
