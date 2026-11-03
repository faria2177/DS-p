package com.docscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.docscan.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke-test Compose rendering and screenshot capture without an optional
 * third-party screenshot library that is not declared in this project.
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
        val screenshot = composeTestRule.onRoot().captureToImage()
        assertTrue("Screenshot must have a non-empty width", screenshot.width > 0)
        assertTrue("Screenshot must have a non-empty height", screenshot.height > 0)
    }
}
