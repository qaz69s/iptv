package com.streamdeck.player.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyStateShowsActionAndInvokesCallback() {
        var clicked = false
        rule.setContent { StreamDeckTheme { EmptyState("Nothing here", actionLabel = "Add", onAction = { clicked = true }) } }
        rule.onNodeWithText("Nothing here").assertIsDisplayed()
        rule.onNodeWithText("Add").performClick()
        assertTrue(clicked)
    }

    @Test fun errorStateShowsReasonAndRetry() {
        var retried = false
        rule.setContent { StreamDeckTheme { ErrorState("HTTP 404", onRetry = { retried = true }) } }
        rule.onNodeWithText("HTTP 404").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }
}
