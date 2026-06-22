package com.rogerparis.pokedex.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rogerparis.pokedex.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PokedexUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun favoritesList_showsItems_andNavigatesToDetail() {
        composeRule.onNodeWithText("Favorites").performClick()

        waitForText("Bulbasaur")
        composeRule.onNodeWithText("Charmander").performClick()

        // Detail shows a Back action and the type chip from the fake (types = ["grass"]).
        waitForDescription("Back")
        composeRule.onNodeWithText("Grass").assertIsDisplayed()
    }

    @Test
    fun detail_favoriteToggle_updatesContentDescription() {
        composeRule.onNodeWithText("Favorites").performClick()
        waitForText("Bulbasaur")
        composeRule.onNodeWithText("Bulbasaur").performClick()

        // Pre-seeded as a favorite, so the action starts in the "remove" state.
        waitForDescription("Remove from favorites")
        composeRule.onNodeWithContentDescription("Remove from favorites").performClick()

        waitForDescription("Add to favorites")
        composeRule.onNodeWithContentDescription("Add to favorites").assertIsDisplayed()
    }

    @Test
    fun teamTab_emptyState_isShown() {
        composeRule.onNodeWithText("Team").performClick()

        waitForText("Your team is empty. Add up to 6 from a Pokémon's page.")
        composeRule.onNodeWithText("Your team is empty. Add up to 6 from a Pokémon's page.").assertIsDisplayed()
    }
}
