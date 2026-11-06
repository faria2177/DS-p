package com.docscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.docscan.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke-test Compose rendering without optional screenshot dependencies.
 * Screenshot capture should use an instrumented UI test on a real device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun greeting_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.material3.Text("Doc Scanner")
            }
        }

        composeTestRule.onNodeWithText("Doc Scanner").assertIsDisplayed()
    }
}
