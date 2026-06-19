package com.rogerparis.pokedex.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rogerparis.pokedex.MainDispatcherRule
import com.rogerparis.pokedex.domain.error.ApiResult
import com.rogerparis.pokedex.domain.error.AppError
import com.rogerparis.pokedex.domain.model.Pokemon
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PokemonDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<PokemonRepository>()

    private fun pokemon() = Pokemon(
        id = 1, name = "bulbasaur", heightDm = 7, weightHg = 69,
        types = listOf("grass"), stats = emptyList(), abilities = listOf("overgrow"),
        artworkUrl = "url",
    )

    private fun viewModel() =
        PokemonDetailViewModel(SavedStateHandle(mapOf("id" to 1)), repository)

    @Before
    fun stubFavoriteDefault() {
        coEvery { repository.isFavorite(1) } returns flowOf(false)
    }

    @Test
    fun `emits Loading then Success`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())

        viewModel().state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            assertEquals(DetailUiState.Success(pokemon()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Loading then Error on failure`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Error(AppError.Network)

        viewModel().state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            assertEquals(DetailUiState.Error(AppError.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite adds when not currently favorite`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())
        coEvery { repository.isFavorite(1) } returns flowOf(false)
        coJustRun { repository.addFavorite(any()) }

        val vm = viewModel()
        runCurrent()
        vm.toggleFavorite()
        runCurrent()

        coVerify { repository.addFavorite(pokemon()) }
    }

    @Test
    fun `toggleFavorite removes when currently favorite`() = runTest {
        coEvery { repository.getPokemon(1) } returns ApiResult.Success(pokemon())
        coEvery { repository.isFavorite(1) } returns flowOf(true)
        coJustRun { repository.removeFavorite(1) }

        val vm = viewModel()
        runCurrent()
        vm.toggleFavorite()
        runCurrent()

        coVerify { repository.removeFavorite(1) }
    }
}
