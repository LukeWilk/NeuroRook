package io.github.lukewilk

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickButtonShowsGreeting() {
        composeTestRule.setContent {
            App()
        }
        // Click the button
        composeTestRule.onNodeWithText("Click me!").performClick()
        // Check that greeting is shown
        composeTestRule.onNodeWithText("Hello, ", substring = true).assertExists()
    }

    @Test
    fun clickButtonTogglesGreetingVisibility() {
        composeTestRule.setContent {
            App()
        }
        // Initially, greeting should not be visible
        composeTestRule.onNodeWithText("Hello, ", substring = true).assertDoesNotExist()
        // Click to show
        composeTestRule.onNodeWithText("Click me!").performClick()
        composeTestRule.onNodeWithText("Hello, ", substring = true).assertExists()
        // Click to hide
        composeTestRule.onNodeWithText("Click me!").performClick()
        composeTestRule.onNodeWithText("Hello, ", substring = true).assertDoesNotExist()
    }
}
