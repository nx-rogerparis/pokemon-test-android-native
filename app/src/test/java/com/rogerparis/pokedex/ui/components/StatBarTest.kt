package com.rogerparis.pokedex.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_label_and_value() {
        composeRule.setContent {
            StatBar(label = "Speed", value = 100, accent = Color(0xFFEE8130))
        }
        composeRule.onNodeWithText("Speed").assertIsDisplayed()
        composeRule.onNodeWithText("100").assertIsDisplayed()
    }
}
