package com.rogerparis.pokedex.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComponentsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typeChip_displays_capitalized_type() {
        composeRule.setContent { PokemonTypeChip("grass") }
        composeRule.onNodeWithText("Grass").assertIsDisplayed()
    }

    @Test
    fun errorState_shows_message_and_retry_invokes_callback() {
        var retried = 0
        composeRule.setContent {
            ErrorState(message = "Couldn't load", onRetry = { retried++ })
        }
        composeRule.onNodeWithText("Couldn't load").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }
}
